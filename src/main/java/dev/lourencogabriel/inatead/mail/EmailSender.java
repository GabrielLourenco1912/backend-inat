package dev.lourencogabriel.inatead.mail;

public interface EmailSender {

    void send(String recipient, String subject, String body);
}
