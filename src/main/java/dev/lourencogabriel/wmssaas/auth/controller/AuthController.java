package dev.lourencogabriel.wmssaas.auth.controller;

import dev.lourencogabriel.wmssaas.auth.dto.AuthResponse;
import dev.lourencogabriel.wmssaas.auth.dto.LoginRequest;
import dev.lourencogabriel.wmssaas.auth.dto.RefreshRequest;
import dev.lourencogabriel.wmssaas.auth.dto.RegisterRequest;
import dev.lourencogabriel.wmssaas.auth.http.AuthRequestContext;
import dev.lourencogabriel.wmssaas.auth.http.AuthRequestResolver;
import dev.lourencogabriel.wmssaas.auth.http.AuthResponseFactory;
import dev.lourencogabriel.wmssaas.auth.service.AuthService;
import dev.lourencogabriel.wmssaas.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static dev.lourencogabriel.wmssaas.auth.http.AuthRequestResolver.CLIENT_TYPE_HEADER;
import static dev.lourencogabriel.wmssaas.auth.http.AuthResponseFactory.REFRESH_TOKEN_COOKIE;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRequestResolver requestResolver;
    private final AuthResponseFactory responseFactory;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(
            @RequestBody LoginRequest loginRequest,
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
            @RequestBody RegisterRequest registerRequest,
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
            @RequestBody(required = false) RefreshRequest refreshRequest,
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
