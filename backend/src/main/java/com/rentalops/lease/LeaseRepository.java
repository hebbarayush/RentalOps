package com.rentalops.lease;

import com.rentalops.property.Property;
import com.rentalops.tenant.Tenant;
import com.rentalops.user.User;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeaseRepository extends JpaRepository<Lease, Long>, JpaSpecificationExecutor<Lease> {
    boolean existsByPropertyAndUnitNumberAndLeaseStatus(Property property, String unitNumber, LeaseStatus leaseStatus);

    boolean existsByPropertyAndLeaseStatus(Property property, LeaseStatus leaseStatus);

    boolean existsByPropertyAndTenant(Property property, Tenant tenant);

    long countByLeaseStatus(LeaseStatus leaseStatus);

    Page<Lease> findByPropertyManager(User manager, Pageable pageable);

    List<Lease> findAllByPropertyManager(User manager);

    Page<Lease> findByTenant(Tenant tenant, Pageable pageable);

    long countByPropertyManagerAndLeaseStatus(User manager, LeaseStatus leaseStatus);

    List<Lease> findByLeaseStatusAndEndDateBetween(LeaseStatus leaseStatus, LocalDate from, LocalDate to);

    List<Lease> findByPropertyManagerAndLeaseStatusAndEndDateBetween(
            User manager, LeaseStatus leaseStatus, LocalDate from, LocalDate to);

    List<Lease> findByLeaseStatusAndEndDateBefore(LeaseStatus leaseStatus, LocalDate date);

    List<Lease> findByLeaseStatusAndNextChargeDateLessThanEqual(LeaseStatus leaseStatus, LocalDate date);
}
