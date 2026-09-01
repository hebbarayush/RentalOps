package com.rentalops.config;

import com.rentalops.lease.Lease;
import com.rentalops.lease.LeaseRepository;
import com.rentalops.lease.LeaseRequest;
import com.rentalops.maintenance.MaintenanceCreateRequest;
import com.rentalops.maintenance.MaintenancePriority;
import com.rentalops.maintenance.MaintenanceRepository;
import com.rentalops.maintenance.MaintenanceRequest;
import com.rentalops.maintenance.MaintenanceTriageService;
import com.rentalops.payment.MarkPaymentRequest;
import com.rentalops.payment.PaymentMethod;
import com.rentalops.payment.RentPayment;
import com.rentalops.payment.RentPaymentRepository;
import com.rentalops.payment.RentPaymentRequest;
import com.rentalops.property.Property;
import com.rentalops.property.PropertyRepository;
import com.rentalops.property.PropertyRequest;
import com.rentalops.property.PropertyType;
import com.rentalops.tenant.Tenant;
import com.rentalops.tenant.TenantRepository;
import com.rentalops.tenant.TenantRequest;
import com.rentalops.tenant.TenantStatus;
import com.rentalops.user.Role;
import com.rentalops.user.RoleName;
import com.rentalops.user.RoleRepository;
import com.rentalops.user.User;
import com.rentalops.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds baseline reference data (roles + demo users) on every startup, and a small set of
 * realistic domain data (properties, tenants, leases, payments, maintenance) the first time
 * the database is empty so the UI has something to render.
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final LeaseRepository leaseRepository;
    private final RentPaymentRepository rentPaymentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final MaintenanceTriageService triageService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            RoleRepository roleRepository,
            UserRepository userRepository,
            PropertyRepository propertyRepository,
            TenantRepository tenantRepository,
            LeaseRepository leaseRepository,
            RentPaymentRepository rentPaymentRepository,
            MaintenanceRepository maintenanceRepository,
            MaintenanceTriageService triageService,
            PasswordEncoder passwordEncoder
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.tenantRepository = tenantRepository;
        this.leaseRepository = leaseRepository;
        this.rentPaymentRepository = rentPaymentRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.triageService = triageService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        for (RoleName roleName : RoleName.values()) {
            roleRepository.findByName(roleName).orElseGet(() -> roleRepository.save(new Role(roleName)));
        }
        seedUser("Admin User", "admin@rentalops.dev", RoleName.ADMIN);
        User manager = seedUser("Property Manager", "manager@rentalops.dev", RoleName.PROPERTY_MANAGER);
        User tenantUser = seedUser("Demo Tenant", "tenant@rentalops.dev", RoleName.TENANT);

        if (propertyRepository.count() == 0) {
            seedDomainData(manager, tenantUser);
        }
    }

    private User seedUser(String fullName, String email, RoleName roleName) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            User user = new User(fullName, email, passwordEncoder.encode("password123"), "9999999999");
            user.addRole(role);
            return userRepository.save(user);
        });
    }

    private void seedDomainData(User manager, User tenantUser) {
        Property maple = propertyRepository.save(new Property(manager, new PropertyRequest(
                "Maple Court Apartments", "12-unit block near the metro station",
                "18 Maple Street", null, "Bengaluru", "Karnataka", "560001", "India",
                PropertyType.APARTMENT, 12)));
        Property rosewood = propertyRepository.save(new Property(manager, new PropertyRequest(
                "Rosewood Villa", "Standalone 4BHK villa with garden",
                "7 Rosewood Lane", null, "Bengaluru", "Karnataka", "560034", "India",
                PropertyType.VILLA, 1)));

        Tenant priya = new Tenant(manager, new TenantRequest(
                "Priya Nair", "priya.nair@example.com", "9800000001",
                "Anil Nair", "9800000010", "GID-4821", TenantStatus.ACTIVE));
        priya.linkUser(tenantUser); // so tenant@rentalops.dev can use the tenant portal
        priya = tenantRepository.save(priya);
        Tenant rahul = tenantRepository.save(new Tenant(manager, new TenantRequest(
                "Rahul Verma", "rahul.verma@example.com", "9800000002",
                null, null, null, TenantStatus.PENDING)));

        LocalDate today = LocalDate.now();
        Lease priyaLease = new Lease(maple, priya, new LeaseRequest(
                maple.getId(), priya.getId(), "A-3",
                today.minusMonths(1), today.plusYears(1).plusMonths(1),
                new BigDecimal("25000.00"), new BigDecimal("50000.00"), null));
        priyaLease.activate();
        priyaLease = leaseRepository.save(priyaLease);
        maple.occupyUnit();
        maple = propertyRepository.save(maple);

        // A second lease left in DRAFT so the "activate" workflow has something to act on.
        leaseRepository.save(new Lease(rosewood, rahul, new LeaseRequest(
                rosewood.getId(), rahul.getId(), "Villa",
                today.plusWeeks(2), today.plusYears(1).plusWeeks(2),
                new BigDecimal("60000.00"), new BigDecimal("120000.00"), null)));

        RentPayment augustRent = new RentPayment(priyaLease, new RentPaymentRequest(
                priyaLease.getId(), new BigDecimal("25000.00"), today.minusDays(20), "Rent for last month"));
        augustRent.markPaid(new MarkPaymentRequest(new BigDecimal("25000.00"), PaymentMethod.UPI, "UPI-88421"));
        rentPaymentRepository.save(augustRent);
        rentPaymentRepository.save(new RentPayment(priyaLease, new RentPaymentRequest(
                priyaLease.getId(), new BigDecimal("25000.00"), today.plusDays(10), "Rent for this month")));

        seedMaintenance(priya, maple, "Leaking kitchen tap",
                "Constant drip under the sink, water pooling in the cabinet.", MaintenancePriority.HIGH);
        seedMaintenance(priya, maple, "Lift making grinding noise",
                "Elevator makes a loud noise between floors 2 and 3.", MaintenancePriority.MEDIUM);
    }

    private void seedMaintenance(Tenant tenant, Property property, String title, String description,
                                 MaintenancePriority priority) {
        MaintenanceRequest request = new MaintenanceRequest(tenant, property,
                new MaintenanceCreateRequest(tenant.getId(), property.getId(), title, description, priority));
        MaintenanceTriageService.Result t = triageService.triage(title, description);
        request.applyTriage(t.source(), t.category(), t.priority(), t.summary(), t.costBand(), t.draftReply());
        maintenanceRepository.save(request);
    }
}
