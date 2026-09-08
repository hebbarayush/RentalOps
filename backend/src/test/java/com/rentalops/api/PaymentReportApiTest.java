package com.rentalops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rentalops.common.outbox.OutboxProcessor;
import com.rentalops.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The tenant "I've paid" → manager confirm/dismiss flow. The seeded data has an unpaid
 * "Rent for this month" charge (id 2) for Priya (tenant 1), who is linked to tenant@rentalops.dev.
 */
class PaymentReportApiTest extends ApiTestBase {

    @Autowired OutboxProcessor outboxProcessor;

    private static final long CHARGE_ID = 2;

    @Test
    void tenantReportsPayment_managerIsNotified_thenConfirms() throws Exception {
        // Tenant reports the payment — charge stays unpaid, carries a "reported" marker.
        MvcResult reported = mvc.perform(post("/api/rent-payments/" + CHARGE_ID + "/report-payment")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("paymentMethod", "UPI", "transactionReference", "UPI-99887")))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(reported).get("reportedPaidAt").isNull()).isFalse();
        assertThat(read(reported).get("paymentStatus").asText()).isNotEqualTo("PAID");

        outboxProcessor.processPendingNow();

        MvcResult mgrNotes = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(mgrNotes).get("content").findValuesAsText("type"))
                .contains("RENT_PAYMENT_REPORTED");

        // Manager confirms.
        MvcResult confirmed = mvc.perform(post("/api/rent-payments/" + CHARGE_ID + "/mark-paid")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("amountPaid", 25000, "paymentMethod", "UPI")))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(confirmed).get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(read(confirmed).get("reportedPaidAt").isNull()).isTrue();

        outboxProcessor.processPendingNow();
        MvcResult tenantNotes = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(tenantNotes).get("content").findValuesAsText("type"))
                .contains("RENT_PAYMENT_CONFIRMED");
    }

    @Test
    void managerDismissesReport_tenantIsNotified() throws Exception {
        mvc.perform(post("/api/rent-payments/" + CHARGE_ID + "/report-payment")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("paymentMethod", "CASH")))
                .andExpect(status().isOk());

        MvcResult dismissed = mvc.perform(post("/api/rent-payments/" + CHARGE_ID + "/dismiss-report")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(dismissed).get("reportedPaidAt").isNull()).isTrue();
        assertThat(read(dismissed).get("paymentStatus").asText()).isNotEqualTo("PAID");

        outboxProcessor.processPendingNow();
        MvcResult tenantNotes = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(read(tenantNotes).get("content").findValuesAsText("type"))
                .contains("RENT_PAYMENT_UNCONFIRMED");
    }

    @Test
    void cannotReportAnAlreadyPaidCharge() throws Exception {
        // Charge 1 is the seeded "last month" rent — already PAID.
        mvc.perform(post("/api/rent-payments/1/report-payment")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("paymentMethod", "UPI")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantCannotReportSomeoneElsesCharge() throws Exception {
        // Give Rahul (tenant 2) an active lease + charge, then have Priya's login try to report it.
        mvc.perform(post("/api/leases/2/activate").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        MvcResult charge = mvc.perform(post("/api/rent-payments").header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("leaseId", 2, "amountDue", 60000, "dueDate", "2099-01-01")))
                .andExpect(status().isOk()).andReturn();
        long otherId = read(charge).get("id").asLong();

        mvc.perform(post("/api/rent-payments/" + otherId + "/report-payment")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("paymentMethod", "UPI")))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotReport_andTenantCannotDismissOrMarkPaid() throws Exception {
        mvc.perform(post("/api/rent-payments/" + CHARGE_ID + "/report-payment")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("paymentMethod", "UPI")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/rent-payments/" + CHARGE_ID + "/dismiss-report")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/rent-payments/" + CHARGE_ID + "/mark-paid")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("amountPaid", 25000, "paymentMethod", "UPI")))
                .andExpect(status().isForbidden());
    }
}
