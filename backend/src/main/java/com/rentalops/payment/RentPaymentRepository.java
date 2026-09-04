package com.rentalops.payment;

import com.rentalops.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentPaymentRepository
        extends JpaRepository<RentPayment, Long>, JpaSpecificationExecutor<RentPayment> {
    long countByPaymentStatus(PaymentStatus paymentStatus);

    Page<RentPayment> findByPropertyManager(User manager, Pageable pageable);

    Page<RentPayment> findByTenantId(Long tenantId, Pageable pageable);

    List<RentPayment> findByPaymentStatusAndDueDateBefore(PaymentStatus paymentStatus, LocalDate date);

    List<RentPayment> findByDueDateBetween(LocalDate from, LocalDate to);

    @Query("select p from RentPayment p where p.property.manager = :manager and p.dueDate between :from and :to")
    List<RentPayment> findByManagerAndDueDateBetween(
            @Param("manager") User manager, @Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByLeaseIdAndDueDate(Long leaseId, LocalDate dueDate);

    List<RentPayment> findByTenantIdOrderByDueDateAsc(Long tenantId);

    @Query("select coalesce(sum(p.amountDue), 0) from RentPayment p")
    BigDecimal sumAmountDue();

    @Query("select coalesce(sum(p.amountPaid), 0) from RentPayment p")
    BigDecimal sumAmountPaid();

    @Query("select coalesce(sum(p.amountDue), 0) from RentPayment p where p.property.manager = :manager")
    BigDecimal sumAmountDueByManager(@Param("manager") User manager);

    @Query("select coalesce(sum(p.amountPaid), 0) from RentPayment p where p.property.manager = :manager")
    BigDecimal sumAmountPaidByManager(@Param("manager") User manager);
}
