package dev.lourencogabriel.inatead.auth.http;

public record AuthRequestContext(
        AuthClientType clientType,
        String userAgent,
        String ipAddress
) {
}
