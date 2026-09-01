package com.rentalops.maintenance;

import com.rentalops.tenant.Tenant;
import com.rentalops.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MaintenanceRepository
        extends JpaRepository<MaintenanceRequest, Long>, JpaSpecificationExecutor<MaintenanceRequest> {
    long countByStatus(MaintenanceStatus status);

    Page<MaintenanceRequest> findByPropertyManager(User manager, Pageable pageable);

    Page<MaintenanceRequest> findByTenant(Tenant tenant, Pageable pageable);

    long countByPropertyManagerAndStatus(User manager, MaintenanceStatus status);
}
