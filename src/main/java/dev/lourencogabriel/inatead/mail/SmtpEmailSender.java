package dev.lourencogabriel.inatead.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${api.mail.from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (!StringUtils.hasText(this.fromAddress)) {
            throw new EmailDeliveryException(
                    new IllegalStateException("The sender email is not configured")
            );
        }
        if (!StringUtils.hasText(recipient)) {
            throw new EmailDeliveryException(
                    new IllegalArgumentException("The recipient email is required")
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.fromAddress.trim());
        message.setTo(recipient.trim());
        message.setSubject(sanitizeSubject(subject));
        message.setText(body == null ? "" : body);

        try {
            this.mailSender.send(message);
        } catch (MailException exception) {
            throw new EmailDeliveryException(exception);
        }
    }

    private String sanitizeSubject(String subject) {
        if (!StringUtils.hasText(subject)) {
            return "INAT EAD";
        }
        return subject.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
