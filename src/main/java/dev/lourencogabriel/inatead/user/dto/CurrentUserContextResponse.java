package dev.lourencogabriel.inatead.user.dto;

import java.util.List;

public record CurrentUserContextResponse(
        UserResponse user,
        String learnerId,
        List<String> organizationIds
) {
}
