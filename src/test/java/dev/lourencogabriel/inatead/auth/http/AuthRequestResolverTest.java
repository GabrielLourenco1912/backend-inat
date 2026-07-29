package dev.lourencogabriel.inatead.auth.http;

import dev.lourencogabriel.inatead.auth.dto.RefreshRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRequestResolverTest {

    private final AuthRequestResolver resolver = new AuthRequestResolver();

    @Test
    void shouldDefaultToWebAndExtractRequestMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "test-browser");
        request.setRemoteAddr("192.0.2.10");

        AuthRequestContext context = this.resolver.resolve(null, request);

        assertAll(
                () -> assertEquals(AuthClientType.WEB, context.clientType()),
                () -> assertEquals("test-browser", context.userAgent()),
                () -> assertEquals("192.0.2.10", context.ipAddress())
        );
    }

    @Test
    void shouldNormalizeClientTypeAndUseFirstForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.20, 203.0.113.30");

        AuthRequestContext context = this.resolver.resolve(" mobile ", request);

        assertAll(
                () -> assertEquals(AuthClientType.MOBILE, context.clientType()),
                () -> assertEquals("198.51.100.20", context.ipAddress())
        );
    }

    @Test
    void shouldRejectUnknownClientType() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> this.resolver.resolve("desktop", request)
        );

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode()),
                () -> assertEquals("X-Client-Type must be web or mobile", exception.getReason())
        );
    }

    @Test
    void shouldPreferCookieForWebAndBodyForMobile() {
        RefreshRequest body = new RefreshRequest("body-token");

        assertAll(
                () -> assertEquals(
                        "cookie-token",
                        this.resolver.resolveRefreshToken(AuthClientType.WEB, body, "cookie-token")
                ),
                () -> assertEquals(
                        "body-token",
                        this.resolver.resolveRefreshToken(AuthClientType.MOBILE, body, "cookie-token")
                )
        );
    }

    @Test
    void shouldUseAvailableRefreshTokenAsFallback() {
        RefreshRequest body = new RefreshRequest("body-token");
        RefreshRequest blankBody = new RefreshRequest(" ");

        assertAll(
                () -> assertEquals(
                        "body-token",
                        this.resolver.resolveRefreshToken(AuthClientType.WEB, body, null)
                ),
                () -> assertEquals(
                        "cookie-token",
                        this.resolver.resolveRefreshToken(AuthClientType.MOBILE, blankBody, "cookie-token")
                ),
                () -> assertNull(this.resolver.resolveRefreshToken(AuthClientType.WEB, null, null))
        );
    }
}
