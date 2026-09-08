package dev.lourencogabriel.inatead.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class DocumentTypeDtos {

    private DocumentTypeDtos() {
    }

    public record Request(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*") @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description
    ) {
    }

    public record Response(Short id, String code, String name, String description) {
    }
}
