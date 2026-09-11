package dev.lourencogabriel.inatead.person.model;

import java.util.Arrays;
import java.util.Optional;

public enum PersonTypeCode {
    ADMIN,
    INSTRUCTOR,
    LEARNER,
    EMPLOYER_MANAGER,
    GUARDIAN;

    public static Optional<PersonTypeCode> fromCode(String code) {
        return Arrays.stream(values()).filter(type -> type.name().equals(code)).findFirst();
    }
}
