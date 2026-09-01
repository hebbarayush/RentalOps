package com.rentalops.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.rentalops.lease.Lease;
import com.rentalops.lease.LeaseRepository;
import com.rentalops.lease.LeaseRequest;
import com.rentalops.payment.MarkPaymentRequest;
import com.rentalops.payment.PaymentMethod;
import com.rentalops.payment.RentPayment;
import com.rentalops.payment.RentPaymentRepository;
import com.rentalops.payment.RentPaymentRequest;
import com.rentalops.property.Property;
import com.rentalops.property.PropertyRepository;
import com.rentalops.property.PropertyRequest;
import com.rentalops.property.PropertyType;
import com.rentalops.user.User;
import com.rentalops.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TenantReliabilityServiceTest {

    @Autowired TenantReliabilityService reliability;
    @Autowired PropertyRepository propertyRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired LeaseRepository leaseRepository;
    @Autowired RentPaymentRepository paymentRepository;
    @Autowired UserRepository userRepository;

    private Tenant tenantWithHistory(boolean payLate, boolean leaveOverdue) {
        User manager = userRepository.findByEmail("manager@rentalops.dev").orElseThrow();
        Property property = propertyRepository.save(new Property(manager, new PropertyRequest(
                "Score Block", null, "1 Rd", null, "C", "S", "1", "IN", PropertyType.APARTMENT, 10)));
        Tenant tenant = tenantRepository.save(new Tenant(manager, new TenantRequest(
                "Score Tenant", "score" + System.nanoTime() + "@example.com", "9000000000",
                null, null, null, TenantStatus.ACTIVE)));
        Lease lease = new Lease(property, tenant, new LeaseRequest(
                property.getId(), tenant.getId(), "S-1",
                LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6),
                new BigDecimal("10000.00"), new BigDecimal("20000.00"), null));
        lease.activate();
        lease = leaseRepository.save(lease);

        for (int i = 4; i >= 1; i--) {
            LocalDate due = LocalDate.now().minusMonths(i);
            RentPayment p = new RentPayment(lease, new RentPaymentRequest(
                    lease.getId(), new BigDecimal("10000.00"), due, "Rent"));
            p.markPaid(new MarkPaymentRequest(new BigDecimal("10000.00"), PaymentMethod.UPI, "ref"));
            // markPaid stamps paidDate = today; simulate on-time vs late via due date distance
            paymentRepository.save(p);
        }
        if (leaveOverdue) {
            paymentRepository.save(new RentPayment(lease, new RentPaymentRequest(
                    lease.getId(), new BigDecimal("10000.00"), LocalDate.now().minusDays(20), "Overdue rent")));
        }
        return tenant;
    }

    @Test
    void overdueChargeFlagsLateRisk() {
        Tenant tenant = tenantWithHistory(true, true);
        ReliabilityResponse r = reliability.compute(tenant);
        assertThat(r.currentlyOverdueCount()).isEqualTo(1);
        assertThat(r.predictedLateRisk()).isTrue();
        assertThat(r.outstanding()).isEqualByComparingTo("10000.00");
        assertThat(r.reasons()).isNotEmpty();
        assertThat(r.reasons()).anySatisfy(reason -> assertThat(reason).contains("overdue"));
    }

    @Test
    void cleanHistoryScoresWell() {
        // A tenant with no charges at all is "NEW" and not at risk.
        User manager = userRepository.findByEmail("manager@rentalops.dev").orElseThrow();
        Tenant tenant = tenantRepository.save(new Tenant(manager, new TenantRequest(
                "Fresh Tenant", "fresh" + System.nanoTime() + "@example.com", "9000000000",
                null, null, null, TenantStatus.ACTIVE)));
        ReliabilityResponse r = reliability.compute(tenant);
        assertThat(r.band()).isEqualTo("NEW");
        assertThat(r.predictedLateRisk()).isFalse();
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.reasons()).containsExactly("No rent charges yet — nothing to score.");
    }
}
