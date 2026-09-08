package dev.lourencogabriel.inatead.user.exception;

public class PersonNotFoundForRegistrationException extends RuntimeException {

    public PersonNotFoundForRegistrationException() {
        super("A person record with the supplied email is required for registration");
    }
}
