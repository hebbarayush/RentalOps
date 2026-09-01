package com.rentalops.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure unit test of the deterministic (no-API-key) triage classifier. */
class MaintenanceTriageServiceTest {

    private final MaintenanceTriageService triage = new MaintenanceTriageService("", "claude-opus-5");

    @Test
    void classifiesPlumbing() {
        var r = triage.triage("Kitchen tap leaking", "Water dripping from the faucet under the sink");
        assertThat(r.source()).isEqualTo("RULES");
        assertThat(r.category()).isEqualTo("PLUMBING");
        assertThat(r.draftReply()).isNotBlank();
        assertThat(r.costBand()).isNotBlank();
    }

    @Test
    void safetyKeywordsEscalateToUrgent() {
        var r = triage.triage("Gas smell in flat", "Strong smell of gas near the stove");
        assertThat(r.priority()).isEqualTo(MaintenancePriority.URGENT);
    }

    @Test
    void unknownFallsBackToGeneral() {
        var r = triage.triage("Question about parking", "Where can visitors park?");
        assertThat(r.category()).isEqualTo("GENERAL");
        assertThat(r.priority()).isNotNull();
    }

    @Test
    void electricalDetected() {
        var r = triage.triage("Power socket sparking", "The socket in the bedroom sparks when I plug something in");
        assertThat(r.category()).isEqualTo("ELECTRICAL");
        assertThat(r.priority()).isEqualTo(MaintenancePriority.URGENT);
    }
}
