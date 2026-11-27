package com.inventory.repository;

import com.inventory.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByIsActiveTrue();

    @Query("SELECT s FROM Supplier s WHERE s.isActive = true ORDER BY s.name")
    List<Supplier> findAllActiveSorted();

    @Query(value = """
            SELECT s.id AS supplierId, COUNT(DISTINCT po.id) AS approvedOrderCount,
                   COALESCE(SUM(i.unit_price * i.quantity_ordered), 0) AS orderedValue,
                   COALESCE(SUM(i.unit_price * i.quantity_received), 0) AS receivedValue
            FROM supplier s
            LEFT JOIN purchase_order po ON po.supplier_id = s.id
                AND po.status IN ('APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED')
            LEFT JOIN purchase_order_item i ON i.purchase_order_id = po.id
            WHERE s.is_active = TRUE
            GROUP BY s.id
            """, nativeQuery = true)
    List<SupplierTotals> findReportTotals();

    interface SupplierTotals {
        Long getSupplierId();
        Long getApprovedOrderCount();
        java.math.BigDecimal getOrderedValue();
        java.math.BigDecimal getReceivedValue();
    }
}
