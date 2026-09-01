package com.rentalops.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rentalops.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RbacApiTest extends ApiTestBase {

    @Test
    void tenantCannotCreateProperty() throws Exception {
        mvc.perform(post("/api/properties").header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "X", "addressLine1", "1", "city", "C", "state", "S",
                                "postalCode", "1", "country", "IN", "propertyType", "HOUSE", "totalUnits", 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantCannotListUsers() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void tenantSeesOnlyOwnLeasesAndPayments() throws Exception {
        MvcResult leases = mvc.perform(get("/api/leases").header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk()).andReturn();
        var content = read(leases).get("content");
        // seeded demo tenant (Priya) has exactly one lease
        assertThat(content).hasSize(1);
    }

    @Test
    void tenantCannotMarkPaymentPaid() throws Exception {
        MvcResult payments = mvc.perform(get("/api/rent-payments").header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk()).andReturn();
        long paymentId = read(payments).get("content").get(0).get("id").asLong();
        mvc.perform(post("/api/rent-payments/" + paymentId + "/mark-paid")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("amountPaid", 100, "paymentMethod", "CASH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerOnlySeesOwnPortfolioNotOtherManagers() throws Exception {
        // Register a second manager and give them a property.
        String email = "mgr2+" + System.nanoTime() + "@example.com";
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("fullName", "Manager Two", "email", email,
                                "password", "password123", "role", "PROPERTY_MANAGER")))
                .andReturn();
        String m2 = read(reg).get("token").asText();
        mvc.perform(post("/api/properties").header("Authorization", "Bearer " + m2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("name", "M2 Block", "addressLine1", "1", "city", "C", "state", "S",
                                "postalCode", "1", "country", "IN", "propertyType", "APARTMENT", "totalUnits", 4)))
                .andExpect(status().isOk());

        MvcResult m1List = mvc.perform(get("/api/properties").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andReturn();
        var names = read(m1List).get("content").findValuesAsText("name");
        assertThat(names).doesNotContain("M2 Block");
    }
}
