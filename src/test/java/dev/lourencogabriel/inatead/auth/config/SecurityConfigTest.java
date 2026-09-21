package dev.lourencogabriel.inatead.auth.config;

import dev.lourencogabriel.inatead.auth.http.ApiAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void shouldConfigureEveryAllowedOrigin() {
        List<String> origins = List.of(
                "https://portal.inat.example",
                "https://admin.inat.example"
        );
        SecurityConfig securityConfig = new SecurityConfig(
                "test-only-secret-key-with-at-least-32-characters",
                "https://api.inat.example",
                origins,
                mock(ApiAuthenticationEntryPoint.class)
        );

        CorsConfiguration configuration = securityConfig
                .corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/me"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactlyElementsOf(origins);
        assertThat(configuration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
