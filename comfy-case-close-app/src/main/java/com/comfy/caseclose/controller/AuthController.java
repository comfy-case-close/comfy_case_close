package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.request.LoginRequest;
import com.comfy.caseclose.dto.response.LoginResponseDTO;
import com.comfy.caseclose.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Tokens are stateless JWTs — the client discards it. A server-side denylist would be
        // required to hard-invalidate before expiry; see backend_architecture.md §10.
        return ResponseEntity.noContent().build();
    }
}
