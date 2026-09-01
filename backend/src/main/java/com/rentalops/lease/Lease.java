package com.rentalops.lease;

import com.rentalops.common.BaseEntity;
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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "leases")
public class Lease extends BaseEntity {
    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String unitNumber;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private BigDecimal monthlyRent;

    @Column(nullable = false)
    private BigDecimal securityDeposit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaseStatus leaseStatus = LeaseStatus.DRAFT;

    private String agreementFileUrl;

    /** Day of month rent falls due (1-28). Derived from the start date. */
    @Column(nullable = false)
    private int billingDayOfMonth = 1;

    /** Due date of the next rent charge to be generated; null until the lease is active. */
    private LocalDate nextChargeDate;

    /** Set when this lease was created by renewing {@code renewedFromLeaseId}. */
    private Long renewedFromLeaseId;

    protected Lease() {
    }

    public Lease(Property property, Tenant tenant, LeaseRequest request) {
        this.property = property;
        this.tenant = tenant;
        update(request);
    }

    public void update(LeaseRequest request) {
        if (!request.startDate().isBefore(request.endDate())) {
            throw new IllegalArgumentException("Lease start date must be before end date");
        }
        this.unitNumber = request.unitNumber();
        this.startDate = request.startDate();
        this.endDate = request.endDate();
        this.monthlyRent = request.monthlyRent();
        this.securityDeposit = request.securityDeposit();
        this.agreementFileUrl = request.agreementFileUrl();
        this.billingDayOfMonth = Math.min(request.startDate().getDayOfMonth(), 28);
    }

    public void activate() {
        leaseStatus = LeaseStatus.ACTIVE;
        if (nextChargeDate == null) {
            nextChargeDate = firstChargeOnOrAfter(maxDate(startDate, LocalDate.now()));
        }
    }

    public void terminate() { leaseStatus = LeaseStatus.TERMINATED; }
    public void expire() { leaseStatus = LeaseStatus.EXPIRED; }
    public boolean isActive() { return leaseStatus == LeaseStatus.ACTIVE; }

    /** Advance the billing pointer by one month after a charge has been generated. */
    public void advanceNextChargeDate() {
        if (nextChargeDate != null) {
            LocalDate next = nextChargeDate.plusMonths(1);
            nextChargeDate = next.withDayOfMonth(Math.min(billingDayOfMonth, next.lengthOfMonth()));
        }
    }

    public void markRenewedFrom(Long leaseId) {
        this.renewedFromLeaseId = leaseId;
    }

    private LocalDate firstChargeOnOrAfter(LocalDate from) {
        LocalDate candidate = from.withDayOfMonth(Math.min(billingDayOfMonth, from.lengthOfMonth()));
        return candidate.isBefore(from) ? candidate.plusMonths(1) : candidate;
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    public Property getProperty() { return property; }
    public Tenant getTenant() { return tenant; }
    public String getUnitNumber() { return unitNumber; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getMonthlyRent() { return monthlyRent; }
    public BigDecimal getSecurityDeposit() { return securityDeposit; }
    public LeaseStatus getLeaseStatus() { return leaseStatus; }
    public String getAgreementFileUrl() { return agreementFileUrl; }
    public int getBillingDayOfMonth() { return billingDayOfMonth; }
    public LocalDate getNextChargeDate() { return nextChargeDate; }
    public Long getRenewedFromLeaseId() { return renewedFromLeaseId; }
    public long getVersion() { return version; }
}
