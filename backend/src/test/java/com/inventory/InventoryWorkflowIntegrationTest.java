package com.inventory;

import com.inventory.entity.*;
import com.inventory.repository.*;
import com.inventory.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises real transactions and version checks in H2; MySQL migrations have a separate smoke test. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mountain-workflow-test;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryWorkflowIntegrationTest {
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired SupplierRepository suppliers;
    @Autowired WarehouseRepository warehouses;
    @Autowired WarehouseStockRepository stocks;
    @Autowired WarehouseTransferRepository transfers;
    @Autowired InventoryTransactionRepository movements;
    @Autowired PurchaseOrderRepository orders;
    @Autowired PurchaseOrderItemRepository lines;
    @Autowired InventoryTransactionService inventory;
    @Autowired PurchaseOrderService purchasing;
    @Autowired WarehouseService warehouseService;
    @Autowired SupplierService supplierService;
    @Autowired TransactionTemplate tx;
    @Autowired MockMvc mvc;

    record Fixture(Long product, Long staff, Long manager, Long supplier, Long warehouse) {}

    Fixture fixture() {
        return tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Category category = new Category(); category.setName("Category-" + suffix);
            categories.save(category);
            Product product = new Product(); product.setSku(suffix); product.setName("Test stock");
            product.setUnit("piece"); product.setCategory(category); product = products.save(product);
            User staff = new User(); staff.setFullName("Staff"); staff.setEmail(suffix + "@test.local");
            staff.setPasswordHash("not-used"); staff.setRole(User.Role.STAFF); users.save(staff);
            User manager = new User(); manager.setFullName("Manager"); manager.setEmail("m-" + suffix + "@test.local");
            manager.setPasswordHash("not-used"); manager.setRole(User.Role.MANAGER); users.save(manager);
            Supplier supplier = new Supplier(); supplier.setName("Supplier"); suppliers.save(supplier);
            Warehouse main = warehouseService.getOrCreateDefaultWarehouse();
            return new Fixture(product.getId(), staff.getId(), manager.getId(), supplier.getId(), main.getId());
        });
    }

    Long approvedOrder(Fixture f, int quantity) {
        PurchaseOrder order = purchasing.createPurchaseOrder(f.supplier, f.staff,
                List.of(new PurchaseOrderService.LineItemRequest(f.product, quantity, new BigDecimal("10.00"))));
        purchasing.submitForApproval(order.getId());
        purchasing.approvePurchaseOrder(order.getId(), f.manager);
        return order.getId();
    }

    Long lineId(Long order) { return purchasing.getItemsForOrder(order).getFirst().getId(); }

    void assertBalances(Fixture f, int expected) {
        tx.executeWithoutResult(status -> {
            assertThat(products.findById(f.product).orElseThrow().getCurrentQuantity()).isEqualTo(expected);
            assertThat(stocks.sumQuantityByProductId(f.product)).isEqualTo(expected);
        });
    }

    @Test void partialAndFullReceiptsUpdateAllStockAndReferenceOrder() {
        Fixture f = fixture(); Long order = approvedOrder(f, 10); Long line = lineId(order);
        purchasing.receiveItems(order, List.of(new PurchaseOrderService.ReceiveItemRequest(line, 4)), f.staff);
        assertThat(purchasing.getPurchaseOrderById(order).getStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertBalances(f, 4);
        purchasing.receiveItems(order, List.of(new PurchaseOrderService.ReceiveItemRequest(line, 6)), f.staff);
        assertThat(purchasing.getPurchaseOrderById(order).getStatus()).isEqualTo("RECEIVED");
        assertBalances(f, 10);
        tx.executeWithoutResult(status -> {
            var history = movements.findByProductIdOrderByCreatedAtDesc(f.product);
            assertThat(history).hasSize(2).allSatisfy(m -> assertThat(m.getReferencePurchaseOrder().getId()).isEqualTo(order));
        });
        assertThatThrownBy(() -> purchasing.receiveItems(order, List.of(new PurchaseOrderService.ReceiveItemRequest(line, 1)), f.staff))
                .isInstanceOf(IllegalArgumentException.class);
        assertBalances(f, 10);
    }

    @Test void invalidLaterReceiptRollsBackEarlierLinesAndStock() {
        Fixture f = fixture(); Long order = approvedOrder(f, 10); Long line = lineId(order);
        assertThatThrownBy(() -> purchasing.receiveItems(order, List.of(
                new PurchaseOrderService.ReceiveItemRequest(line, 4),
                new PurchaseOrderService.ReceiveItemRequest(Long.MAX_VALUE, 1)), f.staff))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(purchasing.getItemsForOrder(order).getFirst().getQuantityReceived()).isZero();
        assertThat(purchasing.getPurchaseOrderById(order).getStatus()).isEqualTo("APPROVED");
        assertBalances(f, 0);
        assertThat(movements.findByProductIdOrderByCreatedAtDesc(f.product)).isEmpty();
    }

    @Test void overReceiptIsRejectedWithoutStockImpact() {
        Fixture f = fixture(); Long order = approvedOrder(f, 10); Long line = lineId(order);
        assertThatThrownBy(() -> purchasing.receiveItems(order, List.of(new PurchaseOrderService.ReceiveItemRequest(line, 11)), f.staff))
                .isInstanceOf(IllegalArgumentException.class);
        assertBalances(f, 0);
    }

    @Test void pendingAndRejectedAdjustmentsDoNotChangeStock() {
        Fixture f = fixture(); inventory.recordStockIn(f.product, 20, f.staff, null);
        var request = inventory.requestAdjustment(f.product, "LOST", 3, f.staff, "Count shortage");
        assertBalances(f, 20);
        assertThat(request.getQuantityAfter()).isEqualTo(20);
        inventory.rejectAdjustment(request.getId(), f.manager);
        assertBalances(f, 20);
    }

    @Test void signedAdjustmentsAndDamageUseCorrectDirection() {
        Fixture f = fixture(); inventory.recordStockIn(f.product, 20, f.staff, null);
        for (var request : List.of(
                inventory.requestAdjustment(f.product, "ADJUSTMENT", 5, f.staff, "Count surplus"),
                inventory.requestAdjustment(f.product, "ADJUSTMENT", -2, f.staff, "Count shortage"),
                inventory.requestAdjustment(f.product, "DAMAGED", 3, f.staff, "Broken units"))) {
            inventory.approveAdjustment(request.getId(), f.manager);
        }
        assertBalances(f, 20);
    }

    @Test void approvedAdjustmentCannotBeAppliedTwice() {
        Fixture f = fixture(); inventory.recordStockIn(f.product, 20, f.staff, null);
        var request = inventory.requestAdjustment(f.product, "LOST", 3, f.staff, "Missing stock");
        inventory.approveAdjustment(request.getId(), f.manager);
        assertThatThrownBy(() -> inventory.approveAdjustment(request.getId(), f.manager)).isInstanceOf(IllegalArgumentException.class);
        assertBalances(f, 17);
    }

    @Test void correctionLinksOriginalWithoutChangingIt() {
        Fixture f = fixture(); var original = inventory.recordStockIn(f.product, 20, f.staff, null);
        var request = inventory.requestAdjustment(f.product, "CORRECTION", -2, f.staff, "Receipt was overstated", original.getId());
        inventory.approveAdjustment(request.getId(), f.manager);
        assertBalances(f, 18);
        tx.executeWithoutResult(status -> {
            assertThat(movements.findById(request.getId()).orElseThrow().getLinkedTransaction().getId()).isEqualTo(original.getId());
            assertThat(movements.findById(original.getId()).orElseThrow().getQuantity()).isEqualTo(20);
        });
    }

    @Test void transfersPreserveGlobalTotalAndCannotBeCompletedTwice() {
        Fixture f = fixture(); inventory.recordStockIn(f.product, 100, f.staff, null);
        Warehouse destination = new Warehouse(); destination.setCode(UUID.randomUUID().toString().substring(0, 20));
        destination.setName("Branch"); destination = warehouseService.createWarehouse(destination);
        var transfer = warehouseService.initiateTransfer(f.warehouse, destination.getId(), f.product, 80, "Rebalance", f.staff);
        warehouseService.completeTransfer(transfer.getId(), f.manager);
        assertBalances(f, 100);
        assertThat(stocks.findByWarehouseIdAndProductId(f.warehouse, f.product).orElseThrow().getCurrentQuantity()).isEqualTo(20);
        assertThatThrownBy(() -> warehouseService.completeTransfer(transfer.getId(), f.manager)).isInstanceOf(IllegalArgumentException.class);
        // A global balance of 100 does not permit dispatching 30 from a warehouse holding only 20.
        assertThatThrownBy(() -> inventory.recordStockOut(f.product, 30, f.staff)).isInstanceOf(IllegalArgumentException.class);
        assertBalances(f, 100);
    }

    @Test void invalidPoCreationLeavesNoOrphanOrderOrLines() {
        Fixture f = fixture(); long beforeOrders = orders.count(); long beforeLines = lines.count();
        assertThatThrownBy(() -> purchasing.createPurchaseOrder(f.supplier, f.staff, List.of(
                new PurchaseOrderService.LineItemRequest(f.product, 2, BigDecimal.ONE),
                new PurchaseOrderService.LineItemRequest(Long.MAX_VALUE, 1, BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(orders.count()).isEqualTo(beforeOrders);
        assertThat(lines.count()).isEqualTo(beforeLines);
    }

    @Test void concurrentDispatchesCannotOversell() throws Exception {
        Fixture f = fixture(); inventory.recordStockIn(f.product, 100, f.staff, null);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> dispatch = () -> {
                try {
                    tx.executeWithoutResult(status -> {
                        products.findById(f.product).orElseThrow(); // Both transactions load the same version.
                        try { barrier.await(10, TimeUnit.SECONDS); }
                        catch (Exception exception) { throw new IllegalStateException(exception); }
                        inventory.recordStockOut(f.product, 60, f.staff);
                    });
                    return true;
                } catch (org.springframework.dao.OptimisticLockingFailureException | IllegalStateException | IllegalArgumentException conflict) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(dispatch); Future<Boolean> second = executor.submit(dispatch);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertBalances(f, 40);
        assertThat(movements.findByProductIdOrderByCreatedAtDesc(f.product)).hasSize(2); // One receipt and one committed dispatch.
    }

    @Test void externalRolesCannotReadInternalStockOrInvoices() throws Exception {
        mvc.perform(get("/api/products").with(user("customer@test.local").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory-transactions").with(user("supplier@test.local").roles("SUPPLIER_CONTACT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/invoices").with(user("staff@test.local").roles("STAFF"))).andExpect(status().isForbidden());
    }

    @Test void unauthenticatedAndMalformedTokensReturn401() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header("Authorization", "Bearer invalid-token")).andExpect(status().isUnauthorized());
    }

    @Test void supplierReportSeparatesApprovedCommitmentsFromReceivedGoods() {
        Fixture f = fixture(); Long approved = approvedOrder(f, 10);
        purchasing.createPurchaseOrder(f.supplier, f.staff,
                List.of(new PurchaseOrderService.LineItemRequest(f.product, 99, new BigDecimal("10.00"))));
        purchasing.receiveItems(approved, List.of(new PurchaseOrderService.ReceiveItemRequest(lineId(approved), 4)), f.staff);
        var report = supplierService.getSupplierReport().stream().filter(row -> row.supplier().getId().equals(f.supplier)).findFirst().orElseThrow();
        assertThat(report.approvedOrderCount()).isEqualTo(1);
        assertThat(report.orderedValue()).isEqualByComparingTo("100.00");
        assertThat(report.receivedValue()).isEqualByComparingTo("40.00");
    }
}
