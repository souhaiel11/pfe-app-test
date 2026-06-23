package com.pfe.devsecops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.devsecops.dto.LoginRequest;
import com.pfe.devsecops.dto.LoginResponse;
import com.pfe.devsecops.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // ✅ Test 1 — passe : login valide retourne 200
    @Test
    void testLoginWithValidUser_Returns200() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        LoginResponse mockResponse = new LoginResponse("mock-jwt-token", "admin", "ADMIN");
        when(authService.login(any(LoginRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // ❌ Test 2 — ÉCHOUE INTENTIONNELLEMENT (J2)
    // Login invalide retourne 401, mais on attend 200 → FAIL
    @Test
    void testLoginWithInvalidUser_ShouldFail() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("hacker");
        request.setPassword("wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Invalid credentials"));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        // INTENTIONNELLEMENT FAUX : le vrai status est 401, on attend 200 → test FAIL
        assertEquals(200, result.getResponse().getStatus(),
                "This test is intentionally failing for DevSecOps demo");
    }

    // ✅ Test 3 — passe : health endpoint
    @Test
    @WithMockUser
    void testHealthEndpoint_Returns200() throws Exception {
        mockMvc.perform(post("/api/auth/health"))
                .andExpect(status().isOk());
    }
}
