package com.rentalops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rentalops.common.outbox.OutboxProcessor;
import com.rentalops.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class SearchAndNotificationApiTest extends ApiTestBase {

    @Autowired OutboxProcessor outboxProcessor;

    @Test
    void propertySearchFiltersByQueryAndStatus() throws Exception {
        mvc.perform(post("/api/properties").header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "Seaside Towers", "addressLine1", "1", "city", "Panaji",
                                "state", "GA", "postalCode", "1", "country", "IN",
                                "propertyType", "APARTMENT", "totalUnits", 5)))
                .andExpect(status().isOk());

        MvcResult hit = mvc.perform(get("/api/properties?q=seaside").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(hit).get("content").findValuesAsText("name")).contains("Seaside Towers");

        MvcResult miss = mvc.perform(get("/api/properties?q=zzznotfound").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(miss).get("totalElements").asInt()).isZero();

        MvcResult inactive = mvc.perform(get("/api/properties?status=INACTIVE").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(inactive).get("totalElements").asInt()).isZero();
    }

    @Test
    void raisingAndUpdatingMaintenanceProducesNotifications() throws Exception {
        // Tenant raises a request -> manager is notified.
        MvcResult created = mvc.perform(post("/api/maintenance-requests").header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tenantId", 1, "propertyId", 1,
                                "title", "Bathroom light flickering",
                                "description", "The bathroom light keeps flickering on and off",
                                "priority", "LOW")))
                .andExpect(status().isOk()).andReturn();
        long reqId = read(created).get("id").asLong();
        assertThat(read(created).get("triage").get("triaged").asBoolean()).isTrue();

        // The request commits an outbox row, not a notification directly — drain it (in
        // production the OutboxProcessor poller does this every few seconds).
        outboxProcessor.processPendingNow();

        MvcResult mgrNotes = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(mgrNotes).get("content").findValuesAsText("type")).contains("MAINTENANCE_CREATED");

        // Manager updates it -> tenant is notified.
        mvc.perform(put("/api/maintenance-requests/" + reqId).header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("status", "IN_PROGRESS", "managerNotes", "Electrician booked")))
                .andExpect(status().isOk());

        outboxProcessor.processPendingNow();

        MvcResult tenantNotes = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(tenantNotes).get("content").findValuesAsText("type")).contains("MAINTENANCE_UPDATED");
    }

    @Test
    void tenantCanReadOwnReliabilityScore() throws Exception {
        mvc.perform(get("/api/tenants/1/reliability").header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk());
    }
}
