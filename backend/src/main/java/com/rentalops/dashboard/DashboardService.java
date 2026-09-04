package com.rentalops.dashboard;

import com.rentalops.auth.CurrentUserService;
import com.rentalops.dashboard.DashboardTrendsResponse.CollectionPoint;
import com.rentalops.dashboard.DashboardTrendsResponse.MaintenancePoint;
import com.rentalops.dashboard.DashboardTrendsResponse.OccupancyPoint;
import com.rentalops.lease.Lease;
import com.rentalops.lease.LeaseRepository;
import com.rentalops.lease.LeaseService;
import com.rentalops.lease.LeaseStatus;
import com.rentalops.maintenance.MaintenanceRepository;
import com.rentalops.maintenance.MaintenanceRequest;
import com.rentalops.maintenance.MaintenanceStatus;
import com.rentalops.payment.RentPayment;
import com.rentalops.payment.RentPaymentRepository;
import com.rentalops.property.PropertyRepository;
import com.rentalops.tenant.ReliabilityResponse;
import com.rentalops.tenant.Tenant;
import com.rentalops.tenant.TenantReliabilityService;
import com.rentalops.tenant.TenantRepository;
import com.rentalops.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final LeaseRepository leaseRepository;
    private final RentPaymentRepository rentPaymentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final LeaseService leaseService;
    private final TenantReliabilityService reliabilityService;
    private final CurrentUserService currentUserService;

    public DashboardService(
            PropertyRepository propertyRepository,
            TenantRepository tenantRepository,
            LeaseRepository leaseRepository,
            RentPaymentRepository rentPaymentRepository,
            MaintenanceRepository maintenanceRepository,
            LeaseService leaseService,
            TenantReliabilityService reliabilityService,
            CurrentUserService currentUserService
    ) {
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.leaseRepository = leaseRepository;
        this.rentPaymentRepository = rentPaymentRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.leaseService = leaseService;
        this.reliabilityService = reliabilityService;
        this.currentUserService = currentUserService;
    }

    /**
     * Tenants likely to pay their next rent late, and the outstanding balance they represent.
     * Admins see every tenant; managers see only their own portfolio.
     */
    @Cacheable(cacheNames = "rentAtRisk", key = "@currentUserService.requireCurrentUser().id")
    @Transactional(readOnly = true)
    public RentAtRiskResponse rentAtRisk() {
        User current = currentUserService.requireCurrentUser();
        List<Tenant> tenants = currentUserService.isAdmin(current)
                ? tenantRepository.findAll()
                : tenantRepository.findByManager(current);

        List<ReliabilityResponse> atRisk = tenants.stream()
                .map(reliabilityService::compute)
                .filter(r -> r.predictedLateRisk() || r.currentlyOverdueCount() > 0)
                .sorted(Comparator.comparingInt(ReliabilityResponse::score))
                .toList();

        BigDecimal exposure = atRisk.stream()
                .map(ReliabilityResponse::outstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RentAtRiskResponse(atRisk.size(), exposure, atRisk);
    }

    /**
     * Six months of movement behind the snapshot: rent expected vs collected, units under lease,
     * and maintenance opened vs resolved. Same scoping as {@link #summary()}.
     */
    @Cacheable(cacheNames = "dashboardTrends", key = "@currentUserService.requireCurrentUser().id")
    @Transactional(readOnly = true)
    public DashboardTrendsResponse trends() {
        User current = currentUserService.requireCurrentUser();
        boolean admin = currentUserService.isAdmin(current);

        YearMonth first = YearMonth.now().minusMonths(5);
        List<YearMonth> window = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            window.add(first.plusMonths(i));
        }
        LocalDate windowStart = first.atDay(1);
        LocalDate windowEnd = YearMonth.now().atEndOfMonth();

        List<RentPayment> payments = admin
                ? rentPaymentRepository.findByDueDateBetween(windowStart, windowEnd)
                : rentPaymentRepository.findByManagerAndDueDateBetween(current, windowStart, windowEnd);
        List<Lease> leases = admin
                ? leaseRepository.findAll()
                : leaseRepository.findAllByPropertyManager(current);
        List<MaintenanceRequest> requests = admin
                ? maintenanceRepository.findAll()
                : maintenanceRepository.findAllByPropertyManager(current);
        long totalUnits = admin
                ? propertyRepository.sumTotalUnits()
                : propertyRepository.sumTotalUnitsByManager(current);

        List<CollectionPoint> collection = new ArrayList<>();
        List<OccupancyPoint> occupancy = new ArrayList<>();
        List<MaintenancePoint> maintenance = new ArrayList<>();

        for (YearMonth ym : window) {
            String label = ym.toString();
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();

            BigDecimal expected = BigDecimal.ZERO;
            BigDecimal collected = BigDecimal.ZERO;
            for (RentPayment p : payments) {
                if (YearMonth.from(p.getDueDate()).equals(ym)) {
                    expected = expected.add(p.getAmountDue());
                    collected = collected.add(p.getAmountPaid());
                }
            }
            collection.add(new CollectionPoint(label, expected, collected));

            long underLease = leases.stream()
                    .filter(l -> l.getLeaseStatus() == LeaseStatus.ACTIVE || l.getLeaseStatus() == LeaseStatus.EXPIRED)
                    .filter(l -> !l.getStartDate().isAfter(monthEnd) && !l.getEndDate().isBefore(monthStart))
                    .count();
            occupancy.add(new OccupancyPoint(label, underLease, totalUnits));

            long opened = requests.stream().filter(r -> monthOf(r.getCreatedAt()).equals(ym)).count();
            long resolved = requests.stream()
                    .filter(r -> r.getResolvedAt() != null && monthOf(r.getResolvedAt()).equals(ym))
                    .count();
            maintenance.add(new MaintenancePoint(label, opened, resolved));
        }

        return new DashboardTrendsResponse(collection, occupancy, maintenance);
    }

    private static YearMonth monthOf(Instant instant) {
        return YearMonth.from(instant.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    /** Admins see system-wide totals; property managers see only their own portfolio. */
    @Cacheable(cacheNames = "dashboardSummary", key = "@currentUserService.requireCurrentUser().id")
    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        User current = currentUserService.requireCurrentUser();
        boolean admin = currentUserService.isAdmin(current);

        long totalProperties = admin ? propertyRepository.count() : propertyRepository.countByManager(current);
        long totalUnits = admin ? propertyRepository.sumTotalUnits() : propertyRepository.sumTotalUnitsByManager(current);
        long occupiedUnits = admin ? propertyRepository.sumOccupiedUnits() : propertyRepository.sumOccupiedUnitsByManager(current);
        long activeTenants = admin ? tenantRepository.count() : tenantRepository.countByManager(current);
        long activeLeases = admin
                ? leaseRepository.countByLeaseStatus(LeaseStatus.ACTIVE)
                : leaseRepository.countByPropertyManagerAndLeaseStatus(current, LeaseStatus.ACTIVE);
        long openMaintenance = admin
                ? maintenanceRepository.countByStatus(MaintenanceStatus.OPEN)
                : maintenanceRepository.countByPropertyManagerAndStatus(current, MaintenanceStatus.OPEN);
        BigDecimal expected = admin
                ? rentPaymentRepository.sumAmountDue()
                : rentPaymentRepository.sumAmountDueByManager(current);
        BigDecimal collected = admin
                ? rentPaymentRepository.sumAmountPaid()
                : rentPaymentRepository.sumAmountPaidByManager(current);
        long expiring = leaseService.expiringThisMonth().size();

        return new DashboardSummaryResponse(
                totalProperties,
                totalUnits,
                occupiedUnits,
                Math.max(0, totalUnits - occupiedUnits),
                activeTenants,
                activeLeases,
                expiring,
                expected,
                collected,
                expected.subtract(collected),
                openMaintenance
        );
    }
}
