package dev.lourencogabriel.inatead.auth.controller;

import dev.lourencogabriel.inatead.auth.http.AuthRequestResolver;
import dev.lourencogabriel.inatead.auth.http.AuthResponseFactory;
import dev.lourencogabriel.inatead.auth.service.AuthService;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void shouldRejectRegisterRequestFieldsThatExceedTheirLimits() throws Exception {
        String requestBody = """
                {
                  "name": "%s",
                  "email": "%s@example.com",
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
