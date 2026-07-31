package dev.lourencogabriel.inatead.auth.controller;

import dev.lourencogabriel.inatead.auth.dto.AuthResponse;
import dev.lourencogabriel.inatead.auth.dto.LoginRequest;
import dev.lourencogabriel.inatead.auth.dto.RefreshRequest;
import dev.lourencogabriel.inatead.auth.dto.RegisterRequest;
import dev.lourencogabriel.inatead.auth.http.AuthRequestContext;
import dev.lourencogabriel.inatead.auth.http.AuthRequestResolver;
import dev.lourencogabriel.inatead.auth.http.AuthResponseFactory;
import dev.lourencogabriel.inatead.auth.service.AuthService;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static dev.lourencogabriel.inatead.auth.http.AuthRequestResolver.CLIENT_TYPE_HEADER;
import static dev.lourencogabriel.inatead.auth.http.AuthResponseFactory.REFRESH_TOKEN_COOKIE;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRequestResolver requestResolver;
    private final AuthResponseFactory responseFactory;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientTypeHeader,
            HttpServletRequest request
    ) {
        AuthRequestContext requestContext = this.requestResolver.resolve(clientTypeHeader, request);
        AuthResponse authResponse = this.authService.authenticate(
                loginRequest.email(),
                loginRequest.password(),
                requestContext.userAgent(),
                requestContext.ipAddress()
        );

        return this.responseFactory.build(
                authResponse,
                requestContext.clientType(),
                "Authentication Success",
                HttpStatus.OK
        );
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(
            @Valid @RequestBody RegisterRequest registerRequest,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientTypeHeader,
            HttpServletRequest request
    ) {
        AuthRequestContext requestContext = this.requestResolver.resolve(clientTypeHeader, request);
        AuthResponse authResponse = this.authService.register(
                registerRequest,
                requestContext.userAgent(),
                requestContext.ipAddress()
        );

        return this.responseFactory.build(
                authResponse,
                requestContext.clientType(),
                "Register and authentication Success",
                HttpStatus.CREATED
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(
            @Valid @RequestBody(required = false) RefreshRequest refreshRequest,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientTypeHeader,
            HttpServletRequest request
    ) {
        AuthRequestContext requestContext = this.requestResolver.resolve(clientTypeHeader, request);
        String refreshToken = this.requestResolver.resolveRefreshToken(
                requestContext.clientType(),
                refreshRequest,
                refreshTokenCookie
        );

        AuthResponse authResponse = this.authService.refresh(
                refreshToken,
                requestContext.userAgent(),
                requestContext.ipAddress()
        );

        return this.responseFactory.build(
                authResponse,
                requestContext.clientType(),
                "Refresh Success",
                HttpStatus.OK
        );
    }
}
