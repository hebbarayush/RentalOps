package com.rentalops.property;

import com.rentalops.user.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, Long>, JpaSpecificationExecutor<Property> {
    Page<Property> findByManager(User manager, Pageable pageable);

    /**
     * Locks the property row for the duration of the transaction. Used around unit-occupancy
     * changes (lease activation/termination) so two concurrent activations for the same
     * property can't both pass the "unit is free" check before either commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Property p where p.id = :id")
    Optional<Property> lockById(@Param("id") Long id);

    long countByManager(User manager);

    @Query("select distinct l.property from com.rentalops.lease.Lease l where l.tenant.id = :tenantId")
    List<Property> findLeasedByTenantId(@Param("tenantId") Long tenantId);

    @Query("select coalesce(sum(p.totalUnits), 0) from Property p")
    long sumTotalUnits();

    @Query("select coalesce(sum(p.occupiedUnits), 0) from Property p")
    long sumOccupiedUnits();

    @Query("select coalesce(sum(p.totalUnits), 0) from Property p where p.manager = :manager")
    long sumTotalUnitsByManager(@Param("manager") User manager);

    @Query("select coalesce(sum(p.occupiedUnits), 0) from Property p where p.manager = :manager")
    long sumOccupiedUnitsByManager(@Param("manager") User manager);
}
