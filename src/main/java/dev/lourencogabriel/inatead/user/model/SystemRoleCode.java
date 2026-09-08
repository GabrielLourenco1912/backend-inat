package dev.lourencogabriel.inatead.user.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum SystemRoleCode {
    ADMIN,
    INSTRUCTOR,
    LEARNER,
    EMPLOYER_MANAGER;

    private static final Set<String> SUPPORTED_CODES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isSupported(String code) {
        return code != null && SUPPORTED_CODES.contains(code);
    }

    public static Set<String> supportedCodes() {
        return SUPPORTED_CODES;
    }
}
