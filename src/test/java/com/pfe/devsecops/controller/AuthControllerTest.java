package com.pfe.devsecops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.devsecops.config.SecurityConfig;
import com.pfe.devsecops.dto.LoginRequest;
import com.pfe.devsecops.dto.LoginResponse;
import com.pfe.devsecops.security.JwtRequestFilter;
import com.pfe.devsecops.security.JwtUtil;
import com.pfe.devsecops.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest ne fait pas un scan complet de l'application : sans import
// explicite, il n'utilise pas le SecurityFilterChain réel de SecurityConfig
// (CSRF désactivé, /api/auth/** permitAll) et retombe sur la chaîne de
// filtres par défaut de Spring Security (CSRF actif, login form/basic auto-
// configurés) — d'où des 403 "Invalid CSRF token" sur des requêtes qui,
// dans l'application réelle, ne passent jamais par ces filtres. On importe
// donc explicitement SecurityConfig (la vraie chaîne de sécurité de
// production) ainsi que JwtRequestFilter, qu'elle autowire ; ce dernier
// dépend lui-même de JwtUtil et UserDetailsService, non scannés par ce
// slice, d'où leur mock ci-dessous (leur comportement interne n'est pas ce
// qui est testé ici).
@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, JwtRequestFilter.class })
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsService userDetailsService;

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

    // ✅ Test 2 — un login invalide doit être rejeté avec 401. Anciennement
    // une échec intentionnelle de démo (J2) qui assertait 200 (succès) sur un
    // login invalide ; converti en test de régression légitime pour le vrai
    // contrat de sécurité (AuthController#login capture toute exception du
    // service et répond 401 — voir AuthController.java).
    @Test
    void testLoginWithInvalidUser_Returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("hacker");
        request.setPassword("wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Invalid credentials"));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertEquals(401, result.getResponse().getStatus(),
                "invalid credentials must be rejected with 401");
    }

    // ✅ Test 3 — passe : health endpoint. AuthController#health est mappée
    // en @GetMapping ; le test appelait POST (405), ce qui n'a jamais été le
    // comportement testé — corrigé pour utiliser le vrai verbe HTTP de
    // l'endpoint.
    @Test
    @WithMockUser
    void testHealthEndpoint_Returns200() throws Exception {
        mockMvc.perform(get("/api/auth/health"))
                .andExpect(status().isOk());
    }
}
