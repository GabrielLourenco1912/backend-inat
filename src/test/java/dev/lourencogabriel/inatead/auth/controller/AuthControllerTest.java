package dev.lourencogabriel.inatead.auth.controller;

import dev.lourencogabriel.inatead.auth.dto.AuthResponse;
import dev.lourencogabriel.inatead.auth.dto.RefreshRequest;
import dev.lourencogabriel.inatead.auth.dto.VerifyAuthChallengeRequest;
import dev.lourencogabriel.inatead.auth.http.AuthClientType;
import dev.lourencogabriel.inatead.auth.http.AuthRequestContext;
import dev.lourencogabriel.inatead.auth.http.AuthRequestResolver;
import dev.lourencogabriel.inatead.auth.http.AuthResponseFactory;
import dev.lourencogabriel.inatead.auth.service.AuthService;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthRequestResolver requestResolver;

    @Mock
    private AuthResponseFactory responseFactory;

    @Mock
    private HttpServletRequest httpServletRequest;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        this.authController = new AuthController(
                this.authService,
                this.requestResolver,
                this.responseFactory
        );
    }

    @Test
    void shouldResolveAndRevokeRefreshTokenOnLogout() {
        RefreshRequest refreshRequest = new RefreshRequest("body-refresh-token");
        AuthRequestContext requestContext = new AuthRequestContext(
                AuthClientType.WEB,
                "agent",
                "203.0.113.10"
        );
        ResponseEntity<ApiResponse<?>> expectedResponse = ResponseEntity.ok(
                new ApiResponse<>(null, "Logout Success", HttpStatus.OK)
        );
        when(this.requestResolver.resolve("web", this.httpServletRequest)).thenReturn(requestContext);
        when(this.requestResolver.resolveRefreshToken(
                AuthClientType.WEB,
                refreshRequest,
                "cookie-refresh-token"
        )).thenReturn("cookie-refresh-token");
        when(this.responseFactory.buildLogout(AuthClientType.WEB)).thenReturn(expectedResponse);

        ResponseEntity<ApiResponse<?>> response = this.authController.logout(
                refreshRequest,
                "cookie-refresh-token",
                "web",
                this.httpServletRequest
        );

        assertSame(expectedResponse, response);
        verify(this.authService).logout("cookie-refresh-token");
    }

    @Test
    void shouldIssueTokensOnlyThroughVerifiedChallengeResponse() {
        VerifyAuthChallengeRequest verificationRequest = new VerifyAuthChallengeRequest(
                "01JTESTCHALLENGE000000000000",
                "123456"
        );
        AuthRequestContext requestContext = new AuthRequestContext(
                AuthClientType.WEB,
                "agent",
                "203.0.113.10"
        );
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        AuthResponse authResponse = new AuthResponse(
                "inat",
                now,
                now.plusSeconds(900),
                "user-id",
                "access-token",
                "refresh-token",
                now.plusSeconds(3600)
        );
        ResponseEntity<ApiResponse<?>> expectedResponse = ResponseEntity.ok(
                new ApiResponse<>(null, "Authentication successful", HttpStatus.OK)
        );
        when(this.requestResolver.resolve("web", this.httpServletRequest))
                .thenReturn(requestContext);
        when(this.authService.verifyChallenge(
                verificationRequest.challengeId(),
                verificationRequest.code(),
                requestContext.userAgent(),
                requestContext.ipAddress()
        )).thenReturn(authResponse);
        when(this.responseFactory.build(
                authResponse,
                AuthClientType.WEB,
                "Authentication successful",
                HttpStatus.OK
        )).thenReturn(expectedResponse);

        ResponseEntity<ApiResponse<?>> response = this.authController.verifyChallenge(
                verificationRequest,
                "web",
                this.httpServletRequest
        );

        assertSame(expectedResponse, response);
    }
}
