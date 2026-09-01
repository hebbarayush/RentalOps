package com.rentalops.maintenance;

import com.rentalops.common.BaseEntity;
import com.rentalops.property.Property;
import com.rentalops.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "maintenance_requests")
public class MaintenanceRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenancePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceStatus status = MaintenanceStatus.OPEN;

    private String managerNotes;
    private Instant resolvedAt;

    // --- AI-assisted triage (populated on creation) ---
    @Column(nullable = false)
    private boolean aiTriaged = false;
    private String aiSource;
    private String aiCategory;
    @Enumerated(EnumType.STRING)
    private MaintenancePriority aiSuggestedPriority;
    @Column(length = 500)
    private String aiSummary;
    private String aiCostBand;
    @Column(length = 2000)
    private String aiDraftReply;

    protected MaintenanceRequest() {
    }

    public MaintenanceRequest(Tenant tenant, Property property, MaintenanceCreateRequest request) {
        this.tenant = tenant;
        this.property = property;
        this.title = request.title();
        this.description = request.description();
        this.priority = request.priority();
    }

    public void updateStatus(MaintenanceUpdateRequest request) {
        this.status = request.status();
        this.managerNotes = request.managerNotes();
        if (request.status() == MaintenanceStatus.RESOLVED) {
            this.resolvedAt = Instant.now();
        }
    }

    public void applyTriage(String source, String category, MaintenancePriority suggestedPriority,
                            String summary, String costBand, String draftReply) {
        this.aiTriaged = true;
        this.aiSource = source;
        this.aiCategory = category;
        this.aiSuggestedPriority = suggestedPriority;
        this.aiSummary = summary;
        this.aiCostBand = costBand;
        this.aiDraftReply = draftReply;
    }

    /** Manager accepts the AI's priority recommendation. */
    public void acceptSuggestedPriority() {
        if (aiSuggestedPriority != null) {
            this.priority = aiSuggestedPriority;
        }
    }

    public Tenant getTenant() { return tenant; }
    public Property getProperty() { return property; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public MaintenancePriority getPriority() { return priority; }
    public MaintenanceStatus getStatus() { return status; }
    public String getManagerNotes() { return managerNotes; }
    public Instant getResolvedAt() { return resolvedAt; }
    public boolean isAiTriaged() { return aiTriaged; }
    public String getAiSource() { return aiSource; }
    public String getAiCategory() { return aiCategory; }
    public MaintenancePriority getAiSuggestedPriority() { return aiSuggestedPriority; }
    public String getAiSummary() { return aiSummary; }
    public String getAiCostBand() { return aiCostBand; }
    public String getAiDraftReply() { return aiDraftReply; }
}

