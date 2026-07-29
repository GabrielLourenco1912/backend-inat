package dev.lourencogabriel.wmssaas.auth.http;

public record AuthRequestContext(
        AuthClientType clientType,
        String userAgent,
        String ipAddress
) {
}
