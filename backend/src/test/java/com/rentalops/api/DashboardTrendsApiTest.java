package com.rentalops.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rentalops.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class DashboardTrendsApiTest extends ApiTestBase {

    @Test
    void trendsReturnSixZeroFilledMonthsPerSeries() throws Exception {
        MvcResult res = mvc.perform(get("/api/dashboard/trends").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andReturn();

        var body = read(res);
        assertThat(body.get("collection")).hasSize(6);
        assertThat(body.get("occupancy")).hasSize(6);
        assertThat(body.get("maintenance")).hasSize(6);

        // months are oldest-first "yyyy-MM"
        var months = body.get("collection").findValuesAsText("month");
        assertThat(months).isSorted();
        assertThat(months.get(0)).matches("\\d{4}-\\d{2}");

        // the seeded portfolio has an active lease, so at least one month shows units under lease
        assertThat(body.get("occupancy").findValues("unitsUnderLease").stream()
                .anyMatch(n -> n.asLong() > 0)).isTrue();
    }

    @Test
    void managerAndAdminBothReadTrends() throws Exception {
        mvc.perform(get("/api/dashboard/trends").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
