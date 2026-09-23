package dev.lourencogabriel.inatead.mail;

public record EmailMessage(
        String recipient,
        String subject,
        String textBody,
        String htmlBody
) {
}
