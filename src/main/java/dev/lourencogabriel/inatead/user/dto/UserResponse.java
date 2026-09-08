package dev.lourencogabriel.inatead.user.dto;

import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        String id,
        String personId,
        String displayName,
        String loginEmail,
        UserAccountStatus status,
        Set<String> roles,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
}