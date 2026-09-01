package com.rentalops.tenant;

import com.rentalops.payment.PaymentStatus;
import com.rentalops.payment.RentPayment;
import com.rentalops.payment.RentPaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes a tenant's rent-payment reliability score from their charge history.
 *
 * <p>The score is a recency-weighted on-time ratio (payments due in the last 3 months count
 * full weight, 4-6 months back count 60%, older count 30% — {@link #decayWeight}), penalised
 * for currently-overdue charges and average lateness. {@code reasons} makes the score
 * explainable, and {@code predictedLateRisk} is a simple heuristic: a tenant is flagged when
 * they have an overdue charge right now, at least two late/overdue charges in the last 90
 * days, or the score has dropped into the POOR band.
 */
@Service
public class TenantReliabilityService {
    private final RentPaymentRepository paymentRepository;
    private final TenantService tenantService;

    public TenantReliabilityService(RentPaymentRepository paymentRepository, TenantService tenantService) {
        this.paymentRepository = paymentRepository;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public ReliabilityResponse forTenant(Long tenantId) {
        Tenant tenant = tenantService.requireAccessible(tenantId);
        return compute(tenant, paymentRepository.findByTenantIdOrderByDueDateAsc(tenantId));
    }

    /** Used by dashboards; assumes access has already been checked for the whole portfolio. */
    public ReliabilityResponse compute(Tenant tenant) {
        return compute(tenant, paymentRepository.findByTenantIdOrderByDueDateAsc(tenant.getId()));
    }

    private ReliabilityResponse compute(Tenant tenant, List<RentPayment> payments) {
        LocalDate today = LocalDate.now();
        int onTime = 0;
        int late = 0;
        int currentlyOverdue = 0;
        int recentLateOrOverdue = 0; // due within the last 90 days
        long totalDaysLate = 0;
        double weightedOnTime = 0;
        double weightedTotal = 0;
        BigDecimal outstanding = BigDecimal.ZERO;
        LocalDate nextDue = null;

        for (RentPayment p : payments) {
            boolean fullyPaid = p.getPaymentStatus() == PaymentStatus.PAID;
            boolean dueWithin90Days = ChronoUnit.DAYS.between(p.getDueDate(), today) <= 90;

            if (!fullyPaid) {
                outstanding = outstanding.add(p.getAmountDue().subtract(p.getAmountPaid()));
                if (nextDue == null || p.getDueDate().isBefore(nextDue)) {
                    nextDue = p.getDueDate();
                }
            }
            if (fullyPaid && p.getPaidDate() != null) {
                long daysLate = ChronoUnit.DAYS.between(p.getDueDate(), p.getPaidDate());
                double weight = decayWeight(p.getDueDate(), today);
                weightedTotal += weight;
                if (daysLate <= 0) {
                    onTime++;
                    weightedOnTime += weight;
                } else {
                    late++;
                    totalDaysLate += daysLate;
                    if (dueWithin90Days) {
                        recentLateOrOverdue++;
                    }
                }
            } else if (p.getDueDate().isBefore(today)) {
                currentlyOverdue++;
                if (dueWithin90Days) {
                    recentLateOrOverdue++;
                }
            }
        }

        int settled = onTime + late;
        double avgDaysLate = late == 0 ? 0.0 : Math.round((double) totalDaysLate / late * 10) / 10.0;
        double weightedOnTimeRatio = weightedTotal == 0 ? 1.0 : weightedOnTime / weightedTotal;

        int score = (int) Math.round(100 * weightedOnTimeRatio);
        score -= Math.min(40, currentlyOverdue * 8);
        score -= (int) Math.min(20, Math.round(avgDaysLate));
        score = Math.max(0, Math.min(100, score));

        String band = settled == 0 && currentlyOverdue == 0 ? "NEW"
                : score >= 85 ? "EXCELLENT"
                : score >= 70 ? "GOOD"
                : score >= 50 ? "FAIR"
                : "POOR";

        boolean predictedLateRisk = currentlyOverdue > 0
                || recentLateOrOverdue >= 2
                || band.equals("POOR");

        List<String> reasons = explain(onTime, late, currentlyOverdue, recentLateOrOverdue, avgDaysLate, band);

        return new ReliabilityResponse(
                tenant.getId(), tenant.getFullName(),
                settled + currentlyOverdue, onTime, late, currentlyOverdue,
                avgDaysLate,
                score, band, predictedLateRisk, reasons,
                outstanding, nextDue);
    }

    private static List<String> explain(int onTime, int late, int currentlyOverdue,
                                         int recentLateOrOverdue, double avgDaysLate, String band) {
        List<String> reasons = new ArrayList<>();
        if (band.equals("NEW")) {
            reasons.add("No rent charges yet — nothing to score.");
            return reasons;
        }
        if (currentlyOverdue > 0) {
            reasons.add(currentlyOverdue + " payment(s) currently overdue.");
        }
        if (late > 0) {
            reasons.add(late + " payment(s) paid late (avg " + avgDaysLate + " day(s) late).");
        }
        if (recentLateOrOverdue >= 2) {
            reasons.add(recentLateOrOverdue + " late or overdue payment(s) in the last 90 days.");
        }
        if (onTime > 0) {
            reasons.add(onTime + " payment(s) paid on time.");
        }
        if (reasons.isEmpty()) {
            reasons.add("Clean payment history — every charge paid on or before its due date.");
        }
        return reasons;
    }

    /** Recent charges (due within 3 months) count in full; older ones count for less. */
    private static double decayWeight(LocalDate dueDate, LocalDate today) {
        long monthsAgo = ChronoUnit.MONTHS.between(dueDate, today);
        if (monthsAgo <= 3) {
            return 1.0;
        }
        if (monthsAgo <= 6) {
            return 0.6;
        }
        return 0.3;
    }
}
