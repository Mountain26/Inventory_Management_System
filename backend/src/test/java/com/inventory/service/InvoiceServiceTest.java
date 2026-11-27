package com.inventory.service;

import com.inventory.entity.Invoice;
import com.inventory.entity.PurchaseOrder;
import com.inventory.repository.InvoiceRepository;
import com.inventory.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {
    @Mock InvoiceRepository invoices;
    @Mock PurchaseOrderRepository orders;
    @InjectMocks InvoiceService service;

    @Test void cannotOverpayInvoice() {
        Invoice invoice = new Invoice();
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setPaidAmount(new BigDecimal("80.00"));
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        assertThatThrownBy(() -> service.recordPayment(1L, new BigDecimal("20.01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outstanding");
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("80.00");
        verify(invoices, never()).save(any());
    }

    @Test void exactBalanceMarksInvoicePaid() {
        Invoice invoice = new Invoice();
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setPaidAmount(new BigDecimal("80.00"));
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoices.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.recordPayment(1L, new BigDecimal("20.00")).getStatus()).isEqualTo("PAID");
    }

    @Test void rejectsFractionalCents() {
        assertThatThrownBy(() -> service.recordPayment(1L, new BigDecimal("0.001")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(invoices);
    }

    @Test void rejectsInvoicesForUnapprovedOrders() {
        PurchaseOrder order = new PurchaseOrder();
        order.setStatus("DRAFT");
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-1");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setTotalAmount(new BigDecimal("100.00"));
        assertThatThrownBy(() -> service.createInvoice(1L, invoice)).isInstanceOf(IllegalArgumentException.class);
        verify(invoices, never()).save(any());
    }

    @Test void rejectsDueDatesBeforeInvoiceDate() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-1");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().minusDays(1));
        invoice.setTotalAmount(new BigDecimal("100.00"));
        assertThatThrownBy(() -> service.createInvoice(1L, invoice)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(invoices, orders);
    }
}
