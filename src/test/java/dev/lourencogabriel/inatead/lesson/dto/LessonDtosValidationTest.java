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
        assertTrue(validator.validate(request(null)).isEmpty());
        assertTrue(validator.validate(request("")).isEmpty());
        assertTrue(validator.validate(request("https://www.youtube.com/watch?v=abc123")).isEmpty());
        assertTrue(validator.validate(request("http://media.example.org/classes/lesson.mp4")).isEmpty());
    }

    @Test
    void shouldRejectMalformedOrUnsafeExternalLessonUrls() {
        var invalidScheme = validator.validate(request("javascript:alert(1)"));
        var missingHost = validator.validate(request("https:///lesson.mp4"));
        var whitespace = validator.validate(request("https://example.org/aula com espaco"));

        assertEquals(1, invalidScheme.size());
        assertEquals(1, missingHost.size());
        assertEquals(1, whitespace.size());
        assertTrue(invalidScheme.iterator().next().getPropertyPath().toString()
                .equals("externalLessonUrl"));
    }

    private LessonDtos.Request request(String externalLessonUrl) {
        return new LessonDtos.Request(
                "cohort",
                "instructor",
                "External lesson URL validation",
                Instant.parse("2026-09-10T13:00:00Z"),
                Instant.parse("2026-09-10T15:00:00Z"),
                DeliveryMode.ONLINE,
                null,
                "https://meet.example.org/lesson",
                externalLessonUrl,
                LessonStatus.SCHEDULED
        );
    }
}
