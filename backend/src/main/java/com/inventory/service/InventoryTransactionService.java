package com.inventory.service;

import com.inventory.entity.*;
import com.inventory.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryTransactionService {

    private final InventoryTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final WarehouseStockRepository warehouseStockRepository;
    private final WarehouseRepository warehouseRepository;

    @Autowired
    public InventoryTransactionService(
            InventoryTransactionRepository transactionRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            WarehouseStockRepository warehouseStockRepository,
            WarehouseRepository warehouseRepository) {
        this.transactionRepository = transactionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.warehouseStockRepository = warehouseStockRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional
    public InventoryTransaction recordStockIn(Long productId, Integer quantity, Long userId, Long referencePurchaseOrderId) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (referencePurchaseOrderId != null) {
            throw new IllegalArgumentException("Receive purchase order stock through the PO receiving endpoint");
        }
        return stockIn(productId, quantity, userId, null);
    }

    @Transactional
    public InventoryTransaction recordPurchaseOrderReceipt(Long productId, Integer quantity, Long userId, PurchaseOrder order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("A purchase order is required");
        }
        return stockIn(productId, quantity, userId, order);
    }

    private InventoryTransaction stockIn(Long productId, Integer quantity, Long userId, PurchaseOrder order) {
        requirePositiveQuantity(quantity);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        requireActiveProduct(product);
        User performedBy = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        int before = product.getCurrentQuantity();
        int after = checkedQuantity(before, quantity);

        InventoryTransaction txn = new InventoryTransaction();
        txn.setProduct(product);
        txn.setType("STOCK_IN");
        txn.setQuantity(quantity);
        txn.setQuantityBefore(before);
        txn.setQuantityAfter(after);
        txn.setStatus("COMPLETED");
        txn.setPerformedBy(performedBy);
        txn.setReferencePurchaseOrder(order);

        changeDefaultWarehouseStock(product, quantity);
        product.setCurrentQuantity(after);

        try {
            productRepository.save(product);
            
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalStateException("Product was updated by another transaction. Please refresh and try again.");
        }

        return transactionRepository.save(txn);
    }

    @Transactional
    public InventoryTransaction recordStockOut(Long productId, Integer quantity, Long userId) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        requireActiveProduct(product);
        User performedBy = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        int before = product.getCurrentQuantity();
        if (before < quantity) {
            throw new IllegalArgumentException("Insufficient stock available. Please refresh the inventory and try again.");
        }
        int after = before - quantity;

        InventoryTransaction txn = new InventoryTransaction();
        txn.setProduct(product);
        txn.setType("STOCK_OUT");
        txn.setQuantity(quantity);
        txn.setQuantityBefore(before);
        txn.setQuantityAfter(after);
        txn.setStatus("COMPLETED");
        txn.setPerformedBy(performedBy);

        changeDefaultWarehouseStock(product, -quantity);
        product.setCurrentQuantity(after);

        try {
            productRepository.save(product);
            
            notificationService.checkAndNotifyLowStock(product, after);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalStateException("Insufficient stock available. Please refresh the inventory and try again.");
        }

        return transactionRepository.save(txn);
    }

    @Transactional
    public InventoryTransaction requestAdjustment(Long productId, String type, Integer quantity, Long userId, String reason) {
        return requestAdjustment(productId, type, quantity, userId, reason, null);
    }

    @Transactional
    public InventoryTransaction requestAdjustment(Long productId, String type, Integer quantity, Long userId, String reason, Long linkedTransactionId) {
        if (type == null || !List.of("DAMAGED", "LOST", "ADJUSTMENT", "CORRECTION").contains(type)) {
            throw new IllegalArgumentException("Invalid adjustment type");
        }
        if (reason == null || reason.isBlank() || reason.length() > 255) {
            throw new IllegalArgumentException("A reason of 1 to 255 characters is required");
        }
        if (quantity == null || quantity == 0) {
            throw new IllegalArgumentException("Adjustment quantity must be a non-zero integer");
        }
        if (List.of("DAMAGED", "LOST").contains(type)) {
            requirePositiveQuantity(quantity);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        requireActiveProduct(product);
        User performedBy = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        InventoryTransaction txn = new InventoryTransaction();
        txn.setProduct(product);
        txn.setType(type);
        txn.setQuantity(quantity);
        txn.setQuantityBefore(product.getCurrentQuantity());
        // Pending requests have no stock impact. Before/after are finalized on approval.
        txn.setQuantityAfter(product.getCurrentQuantity());
        txn.setStatus("PENDING_APPROVAL");
        txn.setPerformedBy(performedBy);
        txn.setReason(reason.trim());
        if ("CORRECTION".equals(type)) {
            if (linkedTransactionId == null) {
                throw new IllegalArgumentException("A correction must reference the original transaction");
            }
            InventoryTransaction original = transactionRepository.findById(linkedTransactionId)
                    .orElseThrow(() -> new IllegalArgumentException("Original transaction not found"));
            if (!"COMPLETED".equals(original.getStatus()) || !productId.equals(original.getProduct().getId())) {
                throw new IllegalArgumentException("Correction must reference a completed transaction for the same product");
            }
            txn.setLinkedTransaction(original);
        } else if (linkedTransactionId != null) {
            throw new IllegalArgumentException("Only corrections can reference an original transaction");
        }

        return transactionRepository.save(txn);
    }

    @Transactional
    public InventoryTransaction approveAdjustment(Long transactionId, Long approverId) {
        InventoryTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (!"PENDING_APPROVAL".equals(txn.getStatus())) {
            throw new IllegalArgumentException("Only pending transactions can be approved");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Approver not found"));
        requireIndependentApprover(txn, approver, approverId);

        Product product = txn.getProduct();
        requireActiveProduct(product);
        int currentQty = product.getCurrentQuantity();
        int delta = List.of("DAMAGED", "LOST").contains(txn.getType()) ? -txn.getQuantity() : txn.getQuantity();
        int newQty = checkedQuantity(currentQty, delta);

        if (newQty < 0) {
            throw new IllegalArgumentException("Cannot approve: resulting stock would be negative");
        }

        txn.setQuantityBefore(currentQty);
        txn.setQuantityAfter(newQty);
        txn.setStatus("COMPLETED");
        txn.setApprovedBy(approver);

        changeDefaultWarehouseStock(product, delta);
        product.setCurrentQuantity(newQty);

        try {
            productRepository.save(product);
            notificationService.checkAndNotifyLowStock(product, newQty);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalStateException("Product was updated by another transaction. Please refresh and try again.");
        }

        return transactionRepository.save(txn);
    }

    @Transactional
    public InventoryTransaction rejectAdjustment(Long transactionId, Long approverId) {
        InventoryTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (!"PENDING_APPROVAL".equals(txn.getStatus())) {
            throw new IllegalArgumentException("Only pending transactions can be rejected");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Approver not found"));
        requireIndependentApprover(txn, approver, approverId);

        txn.setStatus("REJECTED");
        txn.setApprovedBy(approver);

        return transactionRepository.save(txn);
    }

    private void requireIndependentApprover(InventoryTransaction txn, User approver, Long approverId) {
        if (!Boolean.TRUE.equals(approver.getIsActive()) ||
                (approver.getRole() != User.Role.ADMIN && approver.getRole() != User.Role.MANAGER)) {
            throw new IllegalArgumentException("An active Admin or Manager must review adjustments");
        }
        if (txn.getPerformedBy() != null && approverId.equals(txn.getPerformedBy().getId())) {
            throw new IllegalArgumentException("The requester cannot review their own adjustment");
        }
    }

    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private void requireActiveProduct(Product product) {
        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new IllegalArgumentException("Inactive products cannot be used in new stock movements");
        }
    }

    private int checkedQuantity(int before, int delta) {
        long after = (long) before + delta;
        if (after < 0 || after > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Resulting stock must be between zero and " + Integer.MAX_VALUE);
        }
        return (int) after;
    }

    private void changeDefaultWarehouseStock(Product product, int delta) {
        Warehouse warehouse = warehouseRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new IllegalArgumentException("Configure a default warehouse before recording stock"));
        if (!Boolean.TRUE.equals(warehouse.getIsActive())) {
            throw new IllegalArgumentException("The default warehouse must be active");
        }
        WarehouseStock stock = warehouseStockRepository.findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                .orElseGet(() -> {
                    WarehouseStock created = new WarehouseStock();
                    created.setWarehouse(warehouse);
                    created.setProduct(product);
                    return created;
                });
        long after = (long) stock.getCurrentQuantity() + delta;
        if (after < 0 || after > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Insufficient stock in the default warehouse or quantity limit exceeded");
        }
        stock.setCurrentQuantity((int) after);
        warehouseStockRepository.save(stock);
    }

    public List<InventoryTransaction> getHistoryForProduct(Long productId) {
        return transactionRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public List<InventoryTransaction> getPendingApprovals() {
        return transactionRepository.findByStatus("PENDING_APPROVAL");
    }
    
    public java.util.List<InventoryTransaction> getAllTransactions() {
        return transactionRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    }
}
