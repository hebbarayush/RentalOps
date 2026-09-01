package com.rentalops.payment;

import com.rentalops.common.BaseEntity;
import com.rentalops.lease.Lease;
import com.rentalops.property.Property;
import com.rentalops.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "rent_payments")
public class RentPayment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lease_id", nullable = false)
    private Lease lease;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(nullable = false)
    private BigDecimal amountDue;

    @Column(nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate dueDate;

    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String transactionReference;
    private String notes;

    protected RentPayment() {
    }

    public RentPayment(Lease lease, RentPaymentRequest request) {
        this.lease = lease;
        this.tenant = lease.getTenant();
        this.property = lease.getProperty();
        this.amountDue = request.amountDue();
        this.dueDate = request.dueDate();
        this.notes = request.notes();
    }

    public void markOverdue() {
        if (paymentStatus == PaymentStatus.PENDING) {
            paymentStatus = PaymentStatus.OVERDUE;
        }
    }

    public void markPaid(MarkPaymentRequest request) {
        this.amountPaid = request.amountPaid();
        this.paymentMethod = request.paymentMethod();
        this.transactionReference = request.transactionReference();
        this.paidDate = LocalDate.now();
        this.paymentStatus = amountPaid.compareTo(amountDue) >= 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL;
    }

    public Lease getLease() { return lease; }
    public Tenant getTenant() { return tenant; }
    public Property getProperty() { return property; }
    public BigDecimal getAmountDue() { return amountDue; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getPaidDate() { return paidDate; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getTransactionReference() { return transactionReference; }
    public String getNotes() { return notes; }
}

