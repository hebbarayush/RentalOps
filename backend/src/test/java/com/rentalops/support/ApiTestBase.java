package com.rentalops.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base for HTTP-level tests. The {@code DataSeeder} runs on context start, so the three demo
 * users and the sample portfolio are available to every test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class ApiTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected String managerToken;
    protected String adminToken;
    protected String tenantToken;

    @BeforeEach
    void authenticate() throws Exception {
        managerToken = login("manager@rentalops.dev", "password123");
        adminToken = login("admin@rentalops.dev", "password123");
        tenantToken = login("tenant@rentalops.dev", "password123");
    }

    protected String login(String email, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email, "password", password)))
                .andReturn();
        if (res.getResponse().getStatus() != 200) {
            return null;
        }
        return json.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    protected String body(Object... kv) throws Exception {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i].toString(), kv[i + 1]);
        }
        return json.writeValueAsString(map);
    }

    protected JsonNode read(MvcResult res) throws Exception {
        return json.readTree(res.getResponse().getContentAsString());
    }
}
