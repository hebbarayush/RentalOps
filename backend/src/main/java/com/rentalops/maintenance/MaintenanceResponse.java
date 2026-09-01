package com.rentalops.maintenance;

import java.time.Instant;

public record MaintenanceResponse(
        Long id,
        Long tenantId,
        Long propertyId,
        String title,
        String description,
        MaintenancePriority priority,
        MaintenanceStatus status,
        String managerNotes,
        Instant resolvedAt,
        Triage triage
) {
    public record Triage(
            boolean triaged,
            String source,
            String category,
            MaintenancePriority suggestedPriority,
            String summary,
            String costBand,
            String draftReply
    ) {
    }

    public static MaintenanceResponse from(MaintenanceRequest request) {
        return new MaintenanceResponse(
                request.getId(),
                request.getTenant().getId(),
                request.getProperty().getId(),
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.getStatus(),
                request.getManagerNotes(),
                request.getResolvedAt(),
                new Triage(
                        request.isAiTriaged(),
                        request.getAiSource(),
                        request.getAiCategory(),
                        request.getAiSuggestedPriority(),
                        request.getAiSummary(),
                        request.getAiCostBand(),
                        request.getAiDraftReply())
        );
    }
}
