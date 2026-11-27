package com.inventory.service;

import com.inventory.entity.Invoice;
import com.inventory.entity.PurchaseOrder;
import com.inventory.repository.InvoiceRepository;
import com.inventory.repository.PurchaseOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    public InvoiceService(InvoiceRepository invoiceRepository, PurchaseOrderRepository purchaseOrderRepository) {
        this.invoiceRepository = invoiceRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Transactional
    public Invoice createInvoice(Long purchaseOrderId, Invoice invoice) {
        if (invoice == null || invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank() ||
                invoice.getInvoiceNumber().length() > 50 || invoice.getInvoiceDate() == null) {
            throw new IllegalArgumentException("Invoice number and invoice date are required");
        }
        requireMoney(invoice.getTotalAmount());
        if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(invoice.getInvoiceDate())) {
            throw new IllegalArgumentException("Due date cannot be before invoice date");
        }
        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        if (!List.of("APPROVED", "PARTIALLY_RECEIVED", "RECEIVED").contains(po.getStatus())) {
            throw new IllegalArgumentException("Invoices require an approved purchase order");
        }
                
        if (invoiceRepository.existsByInvoiceNumber(invoice.getInvoiceNumber())) {
            throw new IllegalArgumentException("Invoice number already exists");
        }
        
        invoice.setInvoiceNumber(invoice.getInvoiceNumber().trim());
        invoice.setId(null);
        invoice.setPurchaseOrder(po);
        
        if (invoice.getPaidAmount() == null) {
            invoice.setPaidAmount(BigDecimal.ZERO);
        }
        if (invoice.getPaidAmount().signum() < 0 || invoice.getPaidAmount().scale() > 2 ||
                invoice.getPaidAmount().compareTo(invoice.getTotalAmount()) > 0) {
            throw new IllegalArgumentException("Paid amount must be between zero and the invoice total with at most two decimals");
        }
        
        if (invoice.getPaidAmount().compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus("PAID");
        } else if (invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus("PARTIAL");
        } else {
            invoice.setStatus("UNPAID");
        }
        
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice recordPayment(Long invoiceId, BigDecimal amount) {
        requireMoney(amount);
        
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
                
        BigDecimal newPaidAmount = invoice.getPaidAmount().add(amount);
        if (newPaidAmount.compareTo(invoice.getTotalAmount()) > 0) {
            throw new IllegalArgumentException("Payment exceeds the outstanding invoice balance");
        }
        invoice.setPaidAmount(newPaidAmount);
        
        if (newPaidAmount.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus("PAID");
        } else {
            invoice.setStatus("PARTIAL");
        }
        
        return invoiceRepository.save(invoice);
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
    }

    public List<Invoice> getInvoicesByPurchaseOrder(Long poId) {
        return invoiceRepository.findByPurchaseOrderId(poId);
    }

    private void requireMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Amount must be positive with at most two decimals and within the invoice limit");
        }
    }
}
