package dev.lourencogabriel.inatead.shared.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeConfigurationTest {

    @Test
    void usesUtcForInstantsAndSaoPauloForBusinessDates() {
        Clock clock = new TimeConfiguration().applicationClock();
        Instant instant = Instant.parse("2026-09-24T01:11:04Z");

        assertEquals(ZoneOffset.UTC, clock.getZone());
        assertEquals(LocalDate.of(2026, 9, 23), BusinessTime.date(instant));
        assertEquals(
                LocalDate.of(2026, 9, 23),
                BusinessTime.today(Clock.fixed(instant, ZoneOffset.UTC))
        );
    }
}
