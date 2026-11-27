package com.inventory.service;

import com.inventory.entity.*;
import com.inventory.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryTransactionService inventoryTransactionService;

    @Autowired
    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            InventoryTransactionService inventoryTransactionService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.inventoryTransactionService = inventoryTransactionService;
    }

    @Transactional
    public PurchaseOrder createPurchaseOrder(Long supplierId, Long createdByUserId, List<LineItemRequest> items) {
        return createPurchaseOrder(supplierId, createdByUserId, items, null);
    }

    @Transactional
    public PurchaseOrder createPurchaseOrder(Long supplierId, Long createdByUserId, List<LineItemRequest> items, String notes) {
        if (notes != null && notes.length() > 500) {
            throw new IllegalArgumentException("PO notes must be at most 500 characters");
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));

        User createdBy = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Purchase order must have at least one item");
        }
        if (!Boolean.TRUE.equals(supplier.getIsActive())) {
            throw new IllegalArgumentException("Supplier must be active");
        }
        java.util.Set<Long> productIds = new java.util.HashSet<>();
        for (LineItemRequest item : items) {
            if (item == null || item.productId() == null || item.quantity() == null || item.quantity() <= 0 ||
                    item.unitPrice() == null || item.unitPrice().signum() < 0 || item.unitPrice().scale() > 2 ||
                    item.unitPrice().compareTo(new java.math.BigDecimal("99999999.99")) > 0) {
                throw new IllegalArgumentException("Each PO line needs a product, positive quantity and non-negative price with at most two decimals");
            }
            if (!productIds.add(item.productId())) {
                throw new IllegalArgumentException("A product can appear only once per purchase order");
            }
        }

        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setCreatedBy(createdBy);
        order.setStatus("DRAFT");
        order.setNotes(notes);
        PurchaseOrder savedOrder = purchaseOrderRepository.save(order);

        for (LineItemRequest item : items) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.productId()));
            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new IllegalArgumentException("Purchase order products must be active");
            }

            PurchaseOrderItem poItem = new PurchaseOrderItem();
            poItem.setPurchaseOrder(savedOrder);
            poItem.setProduct(product);
            poItem.setQuantityOrdered(item.quantity());
            poItem.setUnitPrice(item.unitPrice());
            purchaseOrderItemRepository.save(poItem);
        }

        return savedOrder;
    }

    public List<PurchaseOrder> getAllPurchaseOrders() {
        return purchaseOrderRepository.findAll();
    }

    public PurchaseOrder getPurchaseOrderById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
    }

    public List<PurchaseOrderItem> getItemsForOrder(Long purchaseOrderId) {
        return purchaseOrderItemRepository.findByPurchaseOrderId(purchaseOrderId);
    }
    
    public List<PurchaseOrder> getOrdersBySupplier(Long supplierId) {
        return purchaseOrderRepository.findBySupplierId(supplierId);
    }
    
    public List<PurchaseOrder> getOrdersByStatus(String status) {
        return purchaseOrderRepository.findByStatus(status);
    }
    
    @Transactional
    public PurchaseOrder submitForApproval(Long poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        if (!"DRAFT".equals(po.getStatus())) {
            throw new IllegalArgumentException("Only DRAFT orders can be submitted for approval");
        }
        po.setStatus("PENDING_APPROVAL");
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder approvePurchaseOrder(Long poId, Long approverId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        if (!"PENDING_APPROVAL".equals(po.getStatus())) {
            throw new IllegalArgumentException("Only PENDING_APPROVAL orders can be approved");
        }
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Approver not found"));
        requireIndependentApprover(po, approver, approverId);
        po.setStatus("APPROVED");
        po.setApprovedBy(approver);
        po.setApprovedAt(java.time.LocalDateTime.now());
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder rejectPurchaseOrder(Long poId, Long approverId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("A rejection reason of 1 to 500 characters is required");
        }
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        if (!"PENDING_APPROVAL".equals(po.getStatus())) {
            throw new IllegalArgumentException("Only PENDING_APPROVAL orders can be rejected");
        }
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Approver not found"));
        requireIndependentApprover(po, approver, approverId);
        po.setStatus("CANCELLED");
        po.setApprovedBy(approver);
        po.setApprovedAt(java.time.LocalDateTime.now());
        po.setRejectionReason(reason.trim());
        return purchaseOrderRepository.save(po);
    }

    @org.springframework.transaction.annotation.Transactional
    public PurchaseOrder receiveItems(Long poId, java.util.List<ReceiveItemRequest> receives, Long userId) {
        if (receives == null || receives.isEmpty()) {
            throw new IllegalArgumentException("At least one receipt line is required");
        }
        java.util.Set<Long> itemIds = new java.util.HashSet<>();
        for (ReceiveItemRequest receive : receives) {
            if (receive == null || receive.purchaseOrderItemId() == null || receive.quantityReceived() == null || receive.quantityReceived() <= 0) {
                throw new IllegalArgumentException("Receipt lines need an item ID and a positive quantity");
            }
            if (!itemIds.add(receive.purchaseOrderItemId())) {
                throw new IllegalArgumentException("Duplicate receipt lines are not allowed");
            }
        }
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        if (!"APPROVED".equals(po.getStatus()) && !"PARTIALLY_RECEIVED".equals(po.getStatus())) {
            throw new IllegalArgumentException("Only APPROVED or PARTIALLY_RECEIVED orders can receive items");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        for (ReceiveItemRequest receive : receives) {
            PurchaseOrderItem item = purchaseOrderItemRepository.findById(receive.purchaseOrderItemId())
                    .orElseThrow(() -> new IllegalArgumentException("PO Item not found: " + receive.purchaseOrderItemId()));
            if (!item.getPurchaseOrder().getId().equals(poId)) {
                throw new IllegalArgumentException("Item does not belong to this PO");
            }
            int remaining = item.getQuantityOrdered() - item.getQuantityReceived();
            if (receive.quantityReceived() > remaining) {
                throw new IllegalArgumentException("Cannot receive more than ordered for item: " + item.getProduct().getName());
            }
            item.setQuantityReceived(item.getQuantityReceived() + receive.quantityReceived());
            purchaseOrderItemRepository.save(item);

            inventoryTransactionService.recordPurchaseOrderReceipt(
                    item.getProduct().getId(), receive.quantityReceived(), user.getId(), po);
        }

        // Determine PO status
        java.util.List<PurchaseOrderItem> allItems = purchaseOrderItemRepository.findByPurchaseOrderId(poId);
        boolean allFullyReceived = allItems.stream()
                .allMatch(i -> i.getQuantityReceived().equals(i.getQuantityOrdered()));
        po.setStatus(allFullyReceived ? "RECEIVED" : "PARTIALLY_RECEIVED");
        if (allFullyReceived) {
            po.setReceivedDate(java.time.LocalDateTime.now());
        }
        return purchaseOrderRepository.save(po);
    }

    public record LineItemRequest(Long productId, Integer quantity, java.math.BigDecimal unitPrice) {}
    public record ReceiveItemRequest(Long purchaseOrderItemId, Integer quantityReceived) {}

    private void requireIndependentApprover(PurchaseOrder po, User approver, Long approverId) {
        if (!Boolean.TRUE.equals(approver.getIsActive()) ||
                (approver.getRole() != User.Role.ADMIN && approver.getRole() != User.Role.MANAGER)) {
            throw new IllegalArgumentException("An active Admin or Manager must review purchase orders");
        }
        if (po.getCreatedBy() != null && approverId.equals(po.getCreatedBy().getId())) {
            throw new IllegalArgumentException("The creator cannot review their own purchase order");
        }
    }
}
