package dev.lourencogabriel.inatead.auth.controller;

import dev.lourencogabriel.inatead.auth.dto.AuthChallengeResponse;
import dev.lourencogabriel.inatead.auth.dto.RegisterRequest;
import dev.lourencogabriel.inatead.auth.http.AuthClientType;
import dev.lourencogabriel.inatead.auth.http.AuthRequestResolver;
import dev.lourencogabriel.inatead.auth.http.AuthRequestContext;
import dev.lourencogabriel.inatead.auth.http.AuthResponseFactory;
import dev.lourencogabriel.inatead.auth.model.AuthChallengePurpose;
import dev.lourencogabriel.inatead.auth.service.AuthService;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerValidationTest {

    private AuthService authService;
    private AuthRequestResolver requestResolver;
    private AuthResponseFactory responseFactory;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.authService = mock(AuthService.class);
        this.requestResolver = mock(AuthRequestResolver.class);
        this.responseFactory = mock(AuthResponseFactory.class);

        AuthController controller = new AuthController(
                this.authService,
                this.requestResolver,
                this.responseFactory
        );

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRejectInvalidLoginRequest() throws Exception {
        this.mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath("$.data[*].field", containsInAnyOrder("email", "password")));

        this.verifyRequestWasNotProcessed();
    }

    @Test
    void shouldRejectInvalidRegisterRequest() throws Exception {
        this.mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "email": "invalid-email",
                                  "regulationAccepted": true,
                                  "regulationAcceptedAt": "2026-09-07T14:32:10.123Z",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath(
                        "$.data[*].field",
                        containsInAnyOrder("name", "email", "password")
                ));

        this.verifyRequestWasNotProcessed();
    }

    @Test
    void shouldRejectRegisterRequestWithoutRegulationAcceptanceAndTimestamp() throws Exception {
        this.mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Test User",
                                  "email": "test@example.com",
                                  "regulationAccepted": false,
                                  "password": "plain-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath(
                        "$.data[*].field",
                        containsInAnyOrder("regulationAccepted", "regulationAcceptedAt")
                ));

        this.verifyRequestWasNotProcessed();
    }

    @Test
    void shouldAcceptRegisterRequestWithExplicitRegulationAcceptance() throws Exception {
        Instant acceptedAt = Instant.parse("2026-09-07T14:32:10.123Z");
        AuthRequestContext requestContext = new AuthRequestContext(
                AuthClientType.WEB,
                "agent",
                "203.0.113.10"
        );
        AuthChallengeResponse challenge = new AuthChallengeResponse(
                "01JTESTCHALLENGE000000000000",
                AuthChallengePurpose.EMAIL_VERIFICATION,
                "t***@example.com",
                acceptedAt.plusSeconds(600),
                5
        );
        when(this.requestResolver.resolve(eq("web"), any(HttpServletRequest.class)))
                .thenReturn(requestContext);
        when(this.authService.register(
                any(RegisterRequest.class),
                eq(requestContext.userAgent()),
                eq(requestContext.ipAddress())
        )).thenReturn(challenge);

        this.mockMvc.perform(post("/api/auth/register")
                        .header("X-Client-Type", "web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Test User",
                                  "email": "test@example.com",
                                  "regulationAccepted": true,
                                  "regulationAcceptedAt": "2026-09-07T14:32:10.123Z",
                                  "password": "plain-password"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message")
                        .value("Registration created; verification code sent"))
                .andExpect(jsonPath("$.data.purpose").value("EMAIL_VERIFICATION"));

        ArgumentCaptor<RegisterRequest> requestCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(this.authService).register(
                requestCaptor.capture(),
                eq(requestContext.userAgent()),
                eq(requestContext.ipAddress())
        );
        assertAll(
                () -> assertTrue(requestCaptor.getValue().regulationAccepted()),
                () -> assertEquals(acceptedAt, requestCaptor.getValue().regulationAcceptedAt())
        );
    }

    @Test
    void shouldRejectRegisterRequestFieldsThatExceedTheirLimits() throws Exception {
        String requestBody = """
                {
                  "name": "%s",
                  "email": "%s@example.com",
                  "regulationAccepted": true,
                  "regulationAcceptedAt": "2026-09-07T14:32:10.123Z",
                  "password": "%s"
                }
                """.formatted(
                "n".repeat(101),
                "e".repeat(243),
                "p".repeat(73)
        );

        this.mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[*].field", hasItems("name", "email", "password")));

        this.verifyRequestWasNotProcessed();
    }

    @Test
    void shouldApplyBcryptByteLimitToUnicodePassword() throws Exception {
        String requestBody = """
                {
                  "name": "Test User",
                  "email": "test@example.com",
                  "regulationAccepted": true,
                  "regulationAcceptedAt": "2026-09-07T14:32:10.123Z",
                  "password": "%s"
                }
                """.formatted("á".repeat(37));

        this.mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("password"))
                .andExpect(jsonPath("$.data[0].message")
                        .value("Password must not exceed 72 bytes when UTF-8 encoded"));

        this.verifyRequestWasNotProcessed();
    }

    @Test
    void shouldRejectMalformedAuthenticationChallengeCode() throws Exception {
        this.mockMvc.perform(post("/api/auth/challenge/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "challengeId": " ",
                                  "code": "12345x"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath(
                        "$.data[*].field",
                        containsInAnyOrder("challengeId", "code")
                ));

        this.verifyRequestWasNotProcessed();
    }

    @Test
    void shouldRejectInvalidForgotPasswordEmail() throws Exception {
        this.mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath("$.data[0].field").value("email"));

        this.verifyRequestWasNotProcessed();
    }

    @Test
    void shouldRejectInvalidResetPasswordRequest() throws Exception {
        this.mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "challengeId": " ",
                                  "code": "12ab56",
                                  "newPassword": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath(
                        "$.data[*].field",
                        containsInAnyOrder("challengeId", "code", "newPassword")
                ));

        this.verifyRequestWasNotProcessed();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/refresh", "/api/auth/logout"})
    void shouldRejectMalformedRefreshTokenInRequestBody(String endpoint) throws Exception {
        this.mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "malformed-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath("$.data[0].field").value("refreshToken"))
                .andExpect(jsonPath("$.data[0].message").value("Refresh token has an invalid format"));

        this.verifyRequestWasNotProcessed();
    }

    private void verifyRequestWasNotProcessed() {
        verifyNoInteractions(this.authService, this.requestResolver, this.responseFactory);
    }
}
