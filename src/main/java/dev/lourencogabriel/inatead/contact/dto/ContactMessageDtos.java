package dev.lourencogabriel.inatead.contact.dto;

import dev.lourencogabriel.inatead.contact.model.ContactMessageStatus;
import dev.lourencogabriel.inatead.contact.model.ContactMessageType;
import jakarta.validation.constraints.*;

import java.time.Instant;

public final class ContactMessageDtos {

    private ContactMessageDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(
                    regexp = "[1-9]{2}(?:9\\d{8}|[2-5]\\d{7})",
                    message = "Informe DDD e telefone sem formatação")
            String phone,
            @NotNull ContactMessageType contactType,
            @NotBlank @Size(max = 5000) String message
    ) {
    }

    public record StatusRequest(
            @NotNull ContactMessageStatus status
    ) {
    }

    public record Response(
            String id,
            String name,
            String email,
            String phone,
            ContactMessageType contactType,
            String message,
            ContactMessageStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
