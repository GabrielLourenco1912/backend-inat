package dev.lourencogabriel.inatead.contact.dto;

import dev.lourencogabriel.inatead.contact.model.ContactMessageStatus;
import dev.lourencogabriel.inatead.contact.model.ContactMessageType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ContactMessageDtos {

    private ContactMessageDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
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
