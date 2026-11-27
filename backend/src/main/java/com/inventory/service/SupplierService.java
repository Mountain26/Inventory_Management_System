package com.inventory.service;

import com.inventory.entity.Supplier;
import com.inventory.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final com.inventory.repository.PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    public SupplierService(SupplierRepository supplierRepository,
                           com.inventory.repository.PurchaseOrderRepository purchaseOrderRepository) {
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    public Supplier createSupplier(Supplier supplier) {
        return supplierRepository.save(supplier);
    }

    public List<Supplier> getAllActiveSuppliers() {
        return supplierRepository.findByIsActiveTrue();
    }

    public Supplier getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
    }

    public Supplier updateSupplier(Long id, Supplier updatedData) {
        Supplier existing = getSupplierById(id);
        existing.setName(updatedData.getName());
        existing.setContactPerson(updatedData.getContactPerson());
        existing.setPhone(updatedData.getPhone());
        existing.setEmail(updatedData.getEmail());
        existing.setAddress(updatedData.getAddress());
        return supplierRepository.save(existing);
    }

    public void deactivateSupplier(Long id) {
        Supplier existing = getSupplierById(id);
        existing.setIsActive(false);
        supplierRepository.save(existing);
    }

    public java.util.List<com.inventory.entity.PurchaseOrder> getPurchaseHistoryForSupplier(Long supplierId) {
        Supplier supplier = getSupplierById(supplierId);
        return purchaseOrderRepository.findBySupplierId(supplierId);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public java.util.List<SupplierReportEntry> getSupplierReport() {
        var totals = supplierRepository.findReportTotals().stream().collect(java.util.stream.Collectors.toMap(
                com.inventory.repository.SupplierRepository.SupplierTotals::getSupplierId, value -> value));
        return supplierRepository.findAllActiveSorted().stream().map(s -> {
            var total = totals.get(s.getId());
            return new SupplierReportEntry(s, total == null ? 0 : total.getApprovedOrderCount(),
                    total == null ? java.math.BigDecimal.ZERO : total.getOrderedValue(),
                    total == null ? java.math.BigDecimal.ZERO : total.getReceivedValue());
        }).toList();
    }

    public record SupplierReportEntry(com.inventory.entity.Supplier supplier, long approvedOrderCount,
                                      java.math.BigDecimal orderedValue, java.math.BigDecimal receivedValue) {}
}
