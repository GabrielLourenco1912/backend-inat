package dev.lourencogabriel.inatead.user.exception;

public class PersonAlreadyLinkedToUserException extends RuntimeException {

    public PersonAlreadyLinkedToUserException() {
        super("This person is already linked to a user");
    }
}
