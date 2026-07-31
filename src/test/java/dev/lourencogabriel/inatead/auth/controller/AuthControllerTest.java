package dev.lourencogabriel.inatead.auth.controller;

import dev.lourencogabriel.inatead.auth.dto.RefreshRequest;
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
}
