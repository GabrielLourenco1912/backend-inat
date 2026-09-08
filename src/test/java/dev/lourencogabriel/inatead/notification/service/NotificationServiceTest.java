package dev.lourencogabriel.inatead.notification.service;

import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.notification.dto.NotificationDtos;
import dev.lourencogabriel.inatead.notification.model.Notification;
import dev.lourencogabriel.inatead.notification.model.NotificationRecipient;
import dev.lourencogabriel.inatead.notification.repository.NotificationRecipientRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationDeliveryStatus;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationRecipientRepository recipientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CohortRepository cohortRepository;
    @Mock
    private ObjectMapper objectMapper;

    private NotificationService service;
    private AtomicReference<List<NotificationRecipient>> savedRecipients;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
        service = new NotificationService(
                notificationRepository,
                recipientRepository,
                userRepository,
                cohortRepository,
                objectMapper,
                clock
        );
        savedRecipients = new AtomicReference<>();
    }

    @Test
    void shouldCreateNotificationAndChannelsForOneUser() {
        stubPersistence();
        User user = activeUser("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        NotificationDtos.Response response = service.create(
                request(NotificationAudienceType.USER, "user-1", null),
                null
        );

        assertEquals(NotificationAudienceType.USER, response.audience().type());
        assertEquals("user-1", response.audience().targetId());
        assertEquals(2, savedRecipients.get().size());
        NotificationRecipient inApp = recipient(NotificationChannel.IN_APP);
        assertEquals(NotificationDeliveryStatus.DELIVERED, inApp.getDeliveryStatus());
        assertNotNull(inApp.getSentAt());
        assertNotNull(inApp.getDeliveredAt());
        assertEquals(
                NotificationDeliveryStatus.PENDING,
                recipient(NotificationChannel.EMAIL).getDeliveryStatus()
        );
    }

    @Test
    void shouldKeepScheduledInAppRecipientPending() {
        stubPersistence();
        User user = activeUser("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        service.create(
                request(
                        NotificationAudienceType.USER,
                        "user-1",
                        NOW.plusSeconds(3600)
                ),
                null
        );

        assertEquals(
                NotificationDeliveryStatus.PENDING,
                recipient(NotificationChannel.IN_APP).getDeliveryStatus()
        );
    }

    @Test
    void shouldResolveCurrentActiveCohortUsers() {
        stubPersistence();
        Cohort cohort = new Cohort();
        cohort.setId("cohort-1");
        when(cohortRepository.findById("cohort-1")).thenReturn(Optional.of(cohort));
        when(userRepository.findActiveCohortRecipients(
                "cohort-1",
                LocalDate.of(2026, 9, 4)
        )).thenReturn(List.of(activeUser("user-1"), activeUser("user-2")));

        NotificationDtos.Response response = service.create(
                request(NotificationAudienceType.COHORT, "cohort-1", null),
                null
        );

        assertEquals(NotificationAudienceType.COHORT, response.audience().type());
        assertEquals("cohort-1", response.audience().targetId());
        assertEquals(4, savedRecipients.get().size());
    }

    @Test
    void shouldCreateCohortNotificationOnlyForExplicitRecipients() {
        stubPersistence();
        Cohort cohort = new Cohort();
        cohort.setId("cohort-1");
        User pendingLearner = activeUser("pending-user");
        when(cohortRepository.findById("cohort-1")).thenReturn(Optional.of(cohort));

        NotificationDtos.Response response = service.createForCohortRecipients(
                request(NotificationAudienceType.COHORT, "cohort-1", null),
                null,
                List.of(pendingLearner)
        );

        assertEquals(NotificationAudienceType.COHORT, response.audience().type());
        assertEquals("cohort-1", response.audience().targetId());
        assertEquals(2, savedRecipients.get().size());
        assertEquals(
                Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                savedRecipients.get().stream()
                        .map(NotificationRecipient::getChannel)
                        .collect(java.util.stream.Collectors.toSet())
        );
        verify(userRepository, never()).findActiveCohortRecipients(any(), any());
    }

    @Test
    void shouldResolveAllActiveUsers() {
        stubPersistence();
        when(userRepository.findAllByStatusOrderByUlid(UserAccountStatus.ACTIVE))
                .thenReturn(List.of(activeUser("user-1"), activeUser("user-2")));

        NotificationDtos.Response response = service.create(
                request(NotificationAudienceType.ALL, null, null),
                null
        );

        assertEquals(NotificationAudienceType.ALL, response.audience().type());
        assertNull(response.audience().targetId());
        assertEquals(4, savedRecipients.get().size());
    }

    @Test
    void shouldRejectAudienceWithoutActiveUsers() {
        Cohort cohort = new Cohort();
        cohort.setId("cohort-1");
        when(cohortRepository.findById("cohort-1")).thenReturn(Optional.of(cohort));
        when(userRepository.findActiveCohortRecipients(
                "cohort-1",
                LocalDate.of(2026, 9, 4)
        )).thenReturn(List.of());

        assertThrows(
                BusinessRuleException.class,
                () -> service.create(
                        request(NotificationAudienceType.COHORT, "cohort-1", null),
                        null
                )
        );
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void shouldRejectTargetIdForAllAudience() {
        assertThrows(
                BusinessRuleException.class,
                () -> service.create(
                        request(NotificationAudienceType.ALL, "unexpected", null),
                        null
                )
        );
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void shouldDeletePendingNotificationAsAnAggregate() {
        Notification notification = notification();
        when(notificationRepository.findById("notification"))
                .thenReturn(Optional.of(notification));

        service.delete("notification");

        verify(recipientRepository).deleteAllByNotificationId("notification");
        verify(notificationRepository).deleteById("notification");
    }

    @Test
    void shouldRejectDeletingProcessedNotification() {
        Notification notification = notification();
        when(notificationRepository.findById("notification"))
                .thenReturn(Optional.of(notification));
        when(recipientRepository.existsByNotification_IdAndDeliveryStatusNot(
                "notification",
                NotificationDeliveryStatus.PENDING
        )).thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> service.delete("notification")
        );
        verify(recipientRepository, never()).deleteAllByNotificationId("notification");
        verify(notificationRepository, never()).deleteById("notification");
    }

    private NotificationDtos.CreateRequest request(
            NotificationAudienceType audienceType,
            String targetId,
            Instant scheduledAt
    ) {
        return new NotificationDtos.CreateRequest(
                "ACTIVITY_DUE",
                "Activity due",
                "An activity is waiting for you.",
                null,
                "ACTIVITY",
                "01K4B3ZQ2M8T7V6W5X4Y3Z2A1B",
                null,
                NotificationPriority.NORMAL,
                scheduledAt,
                null,
                new NotificationDtos.AudienceRequest(audienceType, targetId),
                Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)
        );
    }

    @SuppressWarnings("unchecked")
    private void stubPersistence() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId("notification");
            return notification;
        });
        when(recipientRepository.saveAll(any())).thenAnswer(invocation -> {
            List<NotificationRecipient> recipients = invocation.getArgument(0);
            savedRecipients.set(recipients);
            return recipients;
        });
    }

    private User activeUser(String id) {
        User user = new User("Recipient", id + "@example.com", "hash");
        user.setUlid(id);
        user.setStatus(UserAccountStatus.ACTIVE);
        return user;
    }

    private Notification notification() {
        Notification notification = new Notification();
        notification.setId("notification");
        notification.setAudienceType(NotificationAudienceType.ALL);
        notification.setNotificationType("SYSTEM_MESSAGE");
        notification.setTitle("System message");
        notification.setMessage("Message");
        notification.setPriority(NotificationPriority.NORMAL);
        return notification;
    }

    private NotificationRecipient recipient(NotificationChannel channel) {
        return savedRecipients.get().stream()
                .filter(value -> value.getChannel() == channel)
                .findFirst()
                .orElseThrow();
    }
}
