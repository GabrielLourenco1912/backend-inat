package dev.lourencogabriel.inatead.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private BusinessTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    public static LocalDate date(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }
}
