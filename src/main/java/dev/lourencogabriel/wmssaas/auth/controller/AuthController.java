package dev.lourencogabriel.wmssaas.auth.controller;

import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.dto.LoginRequest;
import dev.lourencogabriel.wmssaas.auth.dto.RefreshRequest;
import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.auth.service.AuthService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest loginRequest) {
        AuthResponse authResponse = this.authService.authenticate(loginRequest.email(), loginRequest.password());

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>(authResponse, "Authentication Success");

        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody RegisterRequest registerRequest) {
        AuthResponse authResponse = this.authService.register(registerRequest);

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>(authResponse, "Register and authentication Success", HttpStatus.CREATED);

        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody RefreshRequest refreshRequest) {
        AuthResponse authResponse = this.authService.refresh(
                refreshRequest.refreshToken(),
                null,
                null
        );

        ApiResponse<AuthResponse> apiResponse = new ApiResponse<>(authResponse, "Refresh Success");

        return ResponseEntity.ok(apiResponse);
    }
}
