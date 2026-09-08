package dev.lourencogabriel.inatead.user.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class UserRoleDtos {

    private UserRoleDtos() {
    }

    public record GrantRequest(@NotNull Short roleId) {
    }

    public record Response(
            String userId,
            Short roleId,
            String roleCode,
            String roleName,
            String grantedByUserId,
            Instant grantedAt
    ) {
    }
}
