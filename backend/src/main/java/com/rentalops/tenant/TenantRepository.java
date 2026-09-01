package com.rentalops.tenant;

import com.rentalops.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TenantRepository extends JpaRepository<Tenant, Long>, JpaSpecificationExecutor<Tenant> {
    Page<Tenant> findByManager(User manager, Pageable pageable);

    List<Tenant> findByManager(User manager);

    long countByManager(User manager);

    Optional<Tenant> findByUser(User user);
}
