package com.rentalops.tenant;

import com.rentalops.common.BaseEntity;
import com.rentalops.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    /** Optional link to a registered TENANT-role user account, enabling the tenant portal. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    private String emergencyContactName;
    private String emergencyContactPhone;
    private String governmentIdNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status = TenantStatus.PENDING;

    protected Tenant() {
    }

    public Tenant(User manager, TenantRequest request) {
        this.manager = manager;
        update(request);
    }

    public void update(TenantRequest request) {
        this.fullName = request.fullName();
        this.email = request.email().toLowerCase();
        this.phone = request.phone();
        this.emergencyContactName = request.emergencyContactName();
        this.emergencyContactPhone = request.emergencyContactPhone();
        this.governmentIdNumber = request.governmentIdNumber();
        this.status = request.status();
    }

    public void linkUser(User user) {
        this.user = user;
    }

    public User getManager() { return manager; }
    public User getUser() { return user; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public String getGovernmentIdNumber() { return governmentIdNumber; }
    public TenantStatus getStatus() { return status; }
}

