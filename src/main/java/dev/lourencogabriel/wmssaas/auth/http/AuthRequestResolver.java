package dev.lourencogabriel.wmssaas.auth.http;

import dev.lourencogabriel.wmssaas.auth.dto.RefreshRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Component
public class AuthRequestResolver {

    public static final String CLIENT_TYPE_HEADER = "X-Client-Type";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public AuthRequestContext resolve(String clientTypeHeader, HttpServletRequest request) {
        return new AuthRequestContext(
                this.resolveClientType(clientTypeHeader),
                request.getHeader(HttpHeaders.USER_AGENT),
                this.extractIpAddress(request)
        );
    }

    public String resolveRefreshToken(
            AuthClientType clientType,
            RefreshRequest refreshRequest,
            String refreshTokenCookie
    ) {
        String requestRefreshToken = refreshRequest == null ? null : refreshRequest.refreshToken();

        if (clientType == AuthClientType.WEB) {
            return this.firstNonBlank(refreshTokenCookie, requestRefreshToken);
        }

        return this.firstNonBlank(requestRefreshToken, refreshTokenCookie);
    }

    private AuthClientType resolveClientType(String clientTypeHeader) {
        if (!StringUtils.hasText(clientTypeHeader)) {
            return AuthClientType.WEB;
        }

        try {
            return AuthClientType.valueOf(clientTypeHeader.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CLIENT_TYPE_HEADER + " must be web or mobile");
        }
    }

    private String firstNonBlank(String firstValue, String secondValue) {
        if (StringUtils.hasText(firstValue)) {
            return firstValue;
        }

        if (StringUtils.hasText(secondValue)) {
            return secondValue;
        }

        return null;
    }

    private String extractIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);

        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }

        return request.getRemoteAddr();
    }
}
