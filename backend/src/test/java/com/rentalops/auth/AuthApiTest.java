package com.rentalops.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rentalops.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends ApiTestBase {

    @Test
    void registerThenLoginThenMe() throws Exception {
        String email = "newmgr+" + System.nanoTime() + "@example.com";
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("fullName", "New Manager", "email", email,
                                "password", "password123", "role", "PROPERTY_MANAGER")))
                .andExpect(status().isOk())
                .andReturn();
        String token = read(reg).get("token").asText();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(login(email, "password123")).isNotNull();
    }

    @Test
    void loginWithWrongPasswordIs401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", "manager@rentalops.dev", "password", "nope")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/properties"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void tenantWithoutLinkedProfileGets404OnMe() throws Exception {
        String email = "lonetenant+" + System.nanoTime() + "@example.com";
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("fullName", "Lone Tenant", "email", email,
                                "password", "password123", "role", "TENANT")))
                .andReturn();
        String token = read(reg).get("token").asText();

        mvc.perform(get("/api/tenants/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void forgotPasswordReturnsDevTokenAndResetWorks() throws Exception {
        String email = "resetme+" + System.nanoTime() + "@example.com";
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("fullName", "Reset Me", "email", email,
                                "password", "password123", "role", "PROPERTY_MANAGER")))
                .andReturn();
        assertThat(read(reg).get("token")).isNotNull();

        MvcResult forgot = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email)))
                .andExpect(status().isOk())
                .andReturn();
        String devToken = read(forgot).get("devToken").asText();

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(body("token", devToken, "newPassword", "brandNewPass1")))
                .andExpect(status().isOk());

        assertThat(login(email, "brandNewPass1")).isNotNull();
        assertThat(login(email, "password123")).isNull();
    }
}
