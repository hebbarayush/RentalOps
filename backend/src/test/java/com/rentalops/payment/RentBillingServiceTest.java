package com.rentalops.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.rentalops.lease.Lease;
import com.rentalops.lease.LeaseRepository;
import com.rentalops.lease.LeaseRequest;
import com.rentalops.property.Property;
import com.rentalops.property.PropertyRepository;
import com.rentalops.property.PropertyRequest;
import com.rentalops.property.PropertyType;
import com.rentalops.tenant.Tenant;
import com.rentalops.tenant.TenantRepository;
import com.rentalops.tenant.TenantRequest;
import com.rentalops.tenant.TenantStatus;
import com.rentalops.user.User;
import com.rentalops.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "app.rent.generate-lead-days=400")
class RentBillingServiceTest {

    @Autowired RentBillingService billing;
    @Autowired LeaseRepository leaseRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired RentPaymentRepository paymentRepository;

    private Lease activeLease(LocalDate start, LocalDate end, String rent) {
        User manager = userRepository.findByEmail("manager@rentalops.dev").orElseThrow();
        Property property = propertyRepository.save(new Property(manager, new PropertyRequest(
                "Billing Test Block", null, "1 Road", null, "City", "State", "0001", "IN",
                PropertyType.APARTMENT, 10)));
        Tenant tenant = tenantRepository.save(new Tenant(manager, new TenantRequest(
                "Billing Tenant", "billing" + System.nanoTime() + "@example.com", "9000000000",
                null, null, null, TenantStatus.ACTIVE)));
        Lease lease = new Lease(property, tenant, new LeaseRequest(
                property.getId(), tenant.getId(), "B-1", start, end,
                new BigDecimal(rent), new BigDecimal("50000.00"), null));
        lease.activate();
        return leaseRepository.save(lease);
    }

    @Test
    void generatesOneChargePerMonthUpToEndDate() {
        LocalDate start = LocalDate.now().minusDays(3);
        LocalDate end = start.plusMonths(12);
        Lease lease = activeLease(start, end, "20000.00");

        int created = billing.generateForLease(lease.getId());

        assertThat(created).isBetween(11, 13);
        assertThat(paymentRepository.findByTenantIdOrderByDueDateAsc(lease.getTenant().getId()))
                .allSatisfy(p -> {
                    assertThat(p.getAmountDue()).isEqualByComparingTo("20000.00");
                    assertThat(p.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
                });
    }

    @Test
    void isIdempotent() {
        Lease lease = activeLease(LocalDate.now().minusDays(3), LocalDate.now().plusMonths(6), "15000.00");
        int first = billing.generateForLease(lease.getId());
        int second = billing.generateForLease(lease.getId());

        assertThat(first).isGreaterThan(0);
        assertThat(second).isZero();
    }
}
