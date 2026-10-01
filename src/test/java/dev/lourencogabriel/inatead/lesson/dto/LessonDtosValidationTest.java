package dev.lourencogabriel.inatead.lesson.dto;

import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonDtosValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptNullBlankAndPublicHttpUrls() {
        assertTrue(validator.validate(request(null, null)).isEmpty());
        assertTrue(validator.validate(request("", "")).isEmpty());
        assertTrue(validator.validate(request(
                "https://meet.example.org/lesson",
                "https://www.youtube.com/watch?v=abc123"
        )).isEmpty());
        assertTrue(validator.validate(request(
                "http://meet.example.org/room",
                "http://media.example.org/classes/lesson.mp4"
        )).isEmpty());
    }

    @Test
    void shouldRejectMalformedOrUnsafeExternalLessonUrls() {
        var invalidScheme = validator.validate(request(null, "javascript:alert(1)"));
        var missingHost = validator.validate(request(null, "https:///lesson.mp4"));
        var whitespace = validator.validate(request(null, "https://example.org/aula com espaco"));

        assertEquals(1, invalidScheme.size());
        assertEquals(1, missingHost.size());
        assertEquals(1, whitespace.size());
        assertTrue(invalidScheme.iterator().next().getPropertyPath().toString()
                .equals("externalLessonUrl"));
    }

    @Test
    void shouldRejectMalformedMeetingUrlWhenProvided() {
        var invalidScheme = validator.validate(request("javascript:alert(1)", null));
        var missingHost = validator.validate(request("https:///meeting", null));
        var whitespace = validator.validate(request("https://example.org/sala com espaco", null));

        assertEquals(1, invalidScheme.size());
        assertEquals(1, missingHost.size());
        assertEquals(1, whitespace.size());
        assertEquals(
                "meetingUrl",
                invalidScheme.iterator().next().getPropertyPath().toString()
        );
    }

    private LessonDtos.Request request(String meetingUrl, String externalLessonUrl) {
        return request(meetingUrl, externalLessonUrl, "Descrição da aula");
    }

    @Test
    void shouldRequireDescriptionAndLimitItsLength() {
        for (String description : new String[]{null, "", "   ", "a".repeat(5001)}) {
            var violations = validator.validate(request(null, null, description));
            assertEquals(1, violations.size());
            assertEquals("description", violations.iterator().next().getPropertyPath().toString());
        }
        assertTrue(validator.validate(request(null, null, "a".repeat(5000))).isEmpty());
    }

    private LessonDtos.Request request(String meetingUrl, String externalLessonUrl, String description) {
        return new LessonDtos.Request(
                "cohort",
                "instructor",
                "External lesson URL validation",
                description,
                Instant.parse("2026-09-10T13:00:00Z"),
                Instant.parse("2026-09-10T15:00:00Z"),
                DeliveryMode.ONLINE,
                null,
                meetingUrl,
                externalLessonUrl,
                LessonStatus.SCHEDULED
        );
    }
}
