package dev.lourencogabriel.inatead.lesson.dto;

import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class LessonDtos {

    public static final String HTTP_URL_REGEX =
            "(?i)^(?:$|https?://(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}(?::\\d{1,5})?(?:[/?#][^\\s]*)?)$";

    private LessonDtos() {
    }

    public record Request(
            @NotBlank String cohortId,
            @NotBlank String instructorPersonId,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull DeliveryMode deliveryMode,
            @Size(max = 80) String room,
            @Size(max = 512)
            @Pattern(
                    regexp = HTTP_URL_REGEX,
                    message = "meetingUrl must be a valid HTTP(S) URL"
            )
            String meetingUrl,
            @Size(max = 2048)
            @Pattern(
                    regexp = HTTP_URL_REGEX,
                    message = "externalLessonUrl must be a valid HTTP(S) URL"
            )
            String externalLessonUrl,
            @NotNull LessonStatus status
    ) {
    }

    public record Response(
            String id,
            String cohortId,
            String instructorPersonId,
            String cohortCode,
            String cohortName,
            String instructorName,
            String title,
            String description,
            Instant startsAt,
            Instant endsAt,
            DeliveryMode deliveryMode,
            String room,
            String meetingUrl,
            String externalLessonUrl,
            LessonStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
