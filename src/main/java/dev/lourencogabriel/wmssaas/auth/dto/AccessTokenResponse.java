package dev.lourencogabriel.wmssaas.auth.dto;

import java.time.Instant;

public record AccessTokenResponse(
        String issuer,
        Instant issuedAt,
        Instant expiresAt,
        String subject,
        String token,
        Instant refreshTokenExpiresAt
) {
    public AccessTokenResponse(AuthResponse authResponse) {
        this(
                authResponse.issuer(),
                authResponse.issuedAt(),
                authResponse.expiresAt(),
                authResponse.subject(),
                authResponse.token(),
                authResponse.refreshTokenExpiresAt()
        );
    }
}
