package com.pfe.devsecops.controller;

import com.pfe.devsecops.dto.LoginRequest;
import com.pfe.devsecops.dto.LoginResponse;
import com.pfe.devsecops.dto.RegisterRequest;
import com.pfe.devsecops.model.User;
import com.pfe.devsecops.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    // VULNERABILITY : pas de rate limiting → brute force possible (ZAP détecte)
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Message d'erreur trop verbeux — expose des infos système
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody RegisterRequest request) {
        User user = authService.register(request);
        user.setPassword(null); // masquer le hash
        return ResponseEntity.ok(user);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
