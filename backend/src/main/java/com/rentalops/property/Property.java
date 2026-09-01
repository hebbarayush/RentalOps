package com.rentalops.property;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "properties")
public class Property extends BaseEntity {
    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @Column(nullable = false)
    private String name;

    @Column(length = 1500)
    private String description;

    @Column(nullable = false)
    private String addressLine1;

    private String addressLine2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String postalCode;

    @Column(nullable = false)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyType propertyType;

    @Column(nullable = false)
    private int totalUnits;

    @Column(nullable = false)
    private int occupiedUnits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status = PropertyStatus.ACTIVE;

    protected Property() {
    }

    public Property(User manager, PropertyRequest request) {
        this.manager = manager;
        update(request);
        this.occupiedUnits = 0;
    }

    public void update(PropertyRequest request) {
        this.name = request.name();
        this.description = request.description();
        this.addressLine1 = request.addressLine1();
        this.addressLine2 = request.addressLine2();
        this.city = request.city();
        this.state = request.state();
        this.postalCode = request.postalCode();
        this.country = request.country();
        this.propertyType = request.propertyType();
        this.totalUnits = request.totalUnits();
    }

    public void deactivate() {
        this.status = PropertyStatus.INACTIVE;
    }

    /** Called when a lease on this property becomes ACTIVE. */
    public void occupyUnit() {
        if (occupiedUnits >= totalUnits) {
            throw new IllegalArgumentException("Property has no vacant units");
        }
        occupiedUnits++;
    }

    /** Called when an ACTIVE lease on this property ends (terminated or expired). */
    public void releaseUnit() {
        if (occupiedUnits > 0) {
            occupiedUnits--;
        }
    }

    public User getManager() { return manager; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public PropertyType getPropertyType() { return propertyType; }
    public int getTotalUnits() { return totalUnits; }
    public int getOccupiedUnits() { return occupiedUnits; }
    public PropertyStatus getStatus() { return status; }
    public long getVersion() { return version; }
}

