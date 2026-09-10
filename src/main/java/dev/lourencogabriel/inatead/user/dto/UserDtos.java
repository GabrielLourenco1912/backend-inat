package dev.lourencogabriel.inatead.user.dto;

import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.shared.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateRequest(
            @NotBlank String personId,
            @NotBlank @Size(max = 150) String displayName,
            @NotBlank @Email @Size(max = 254) String loginEmail,
            @NotBlank @Size(min = 8)
            @MaxUtf8Bytes(value = 72, message = "Password must not exceed 72 UTF-8 bytes")
            String password,
            @NotNull UserAccountStatus status
    ) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 150) String displayName,
            @NotBlank @Email @Size(max = 254) String loginEmail,
            @Size(min = 8)
            @MaxUtf8Bytes(value = 72, message = "Password must not exceed 72 UTF-8 bytes")
            String password,
            @NotNull UserAccountStatus status
    ) {
    }
}
