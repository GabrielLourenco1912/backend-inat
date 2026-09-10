package dev.lourencogabriel.inatead.auth.exception;

import org.springframework.security.core.AuthenticationException;

public class PasswordResetUnavailableException extends AuthenticationException {

    public PasswordResetUnavailableException() {
        super("Password reset is unavailable for this account");
    }
}
