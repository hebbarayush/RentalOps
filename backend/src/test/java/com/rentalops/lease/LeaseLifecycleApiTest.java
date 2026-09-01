package com.rentalops.lease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rentalops.support.ApiTestBase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class LeaseLifecycleApiTest extends ApiTestBase {

    private long createProperty(int units) throws Exception {
        MvcResult r = mvc.perform(post("/api/properties").header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "Lifecycle " + System.nanoTime(), "addressLine1", "1", "city", "C",
                                "state", "S", "postalCode", "1", "country", "IN",
                                "propertyType", "APARTMENT", "totalUnits", units)))
                .andExpect(status().isOk()).andReturn();
        return read(r).get("id").asLong();
    }

    private long createTenant() throws Exception {
        MvcResult r = mvc.perform(post("/api/tenants").header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("fullName", "T " + System.nanoTime(),
                                "email", "t" + System.nanoTime() + "@example.com",
                                "phone", "9000000000", "status", "ACTIVE")))
                .andExpect(status().isOk()).andReturn();
        return read(r).get("id").asLong();
    }

    private long createLease(long propertyId, long tenantId, String unit) throws Exception {
        MvcResult r = mvc.perform(post("/api/leases").header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("propertyId", propertyId, "tenantId", tenantId, "unitNumber", unit,
                                "startDate", LocalDate.now().toString(),
                                "endDate", LocalDate.now().plusYears(1).toString(),
                                "monthlyRent", 20000, "securityDeposit", 40000)))
                .andExpect(status().isOk()).andReturn();
        return read(r).get("id").asLong();
    }

    private int occupiedUnits(long propertyId) throws Exception {
        MvcResult r = mvc.perform(get("/api/properties/" + propertyId).header("Authorization", "Bearer " + managerToken))
                .andReturn();
        return read(r).get("occupiedUnits").asInt();
    }

    @Test
    void activateAndTerminateTrackOccupancy() throws Exception {
        long p = createProperty(3);
        long t = createTenant();
        long lease = createLease(p, t, "A-1");

        assertThat(occupiedUnits(p)).isZero();

        mvc.perform(post("/api/leases/" + lease + "/activate").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        assertThat(occupiedUnits(p)).isEqualTo(1);

        mvc.perform(post("/api/leases/" + lease + "/terminate").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        assertThat(occupiedUnits(p)).isZero();
    }

    @Test
    void cannotDeactivatePropertyWithActiveLease() throws Exception {
        long p = createProperty(2);
        long t = createTenant();
        long lease = createLease(p, t, "A-1");
        mvc.perform(post("/api/leases/" + lease + "/activate").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/properties/" + p).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateFailsWhenPropertyFull() throws Exception {
        long p = createProperty(1);
        long t1 = createTenant();
        long t2 = createTenant();
        long l1 = createLease(p, t1, "A-1");
        long l2 = createLease(p, t2, "A-2");
        mvc.perform(post("/api/leases/" + l1 + "/activate").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/leases/" + l2 + "/activate").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renewCreatesLinkedDraftLease() throws Exception {
        long p = createProperty(2);
        long t = createTenant();
        long lease = createLease(p, t, "A-1");

        MvcResult r = mvc.perform(post("/api/leases/" + lease + "/renew").header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("endDate", LocalDate.now().plusYears(3).toString(), "monthlyRent", 22000)))
                .andExpect(status().isOk()).andReturn();
        var renewal = read(r);
        assertThat(renewal.get("leaseStatus").asText()).isEqualTo("DRAFT");
        assertThat(renewal.get("renewedFromLeaseId").asLong()).isEqualTo(lease);
        assertThat(renewal.get("monthlyRent").asInt()).isEqualTo(22000);
    }
}
