package dev.lourencogabriel.inatead.auth.dto;

import dev.lourencogabriel.inatead.auth.model.AuthChallengePurpose;

import java.time.Instant;

public record AuthChallengeResponse(
        String challengeId,
        AuthChallengePurpose purpose,
        String maskedEmail,
        Instant expiresAt,
        int maxAttempts
) {
}
