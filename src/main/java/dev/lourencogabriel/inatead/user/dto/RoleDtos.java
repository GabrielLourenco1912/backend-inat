package dev.lourencogabriel.inatead.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "(?i)(ADMIN|INSTRUCTOR|LEARNER|EMPLOYER_MANAGER)")
            String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description
    ) {
    }

    public record Response(
            Short id,
            String code,
            String name,
            String description
    ) {
    }
}
