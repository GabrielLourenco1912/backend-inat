package dev.lourencogabriel.inatead.notification.service;

import dev.lourencogabriel.inatead.notification.dto.NotificationRecipientDtos;
import dev.lourencogabriel.inatead.notification.model.Notification;
import dev.lourencogabriel.inatead.notification.model.NotificationRecipient;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRecipientServiceTest {

    @Mock
    private NotificationRecipientRepository recipientRepository;
    @Mock
    private UserRepository userRepository;

    private NotificationRecipientService service;
    private Notification notification;
    private User recipientUser;

    @BeforeEach
    void setUp() {
        service = new NotificationRecipientService(
                recipientRepository,
                userRepository
        );
        notification = new Notification();
        notification.setId("notification");
        notification.setNotificationType("LESSON_TRANSFERRED");
        notification.setTitle("Lesson changed");
        notification.setMessage("You were reassigned for this date.");
        notification.setPriority(NotificationPriority.HIGH);

        recipientUser = new User("Learner", "learner@example.com", "hash");
        recipientUser.setUlid("recipient");
    }

    @Test
    void shouldRejectDeliveryTransitionAfterDelivered() {
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId("delivery");
        recipient.setNotification(notification);
        recipient.setRecipientUser(recipientUser);
        recipient.setChannel(NotificationChannel.IN_APP);
        recipient.setDeliveryStatus(NotificationDeliveryStatus.DELIVERED);
        when(recipientRepository.findById("delivery")).thenReturn(Optional.of(recipient));

        assertThrows(
                BusinessRuleException.class,
                () -> service.updateDelivery(
                        "delivery",
                        new NotificationRecipientDtos.DeliveryRequest(
                                NotificationDeliveryStatus.FAILED,
                                "late failure"
                        )
                )
        );
    }

}
