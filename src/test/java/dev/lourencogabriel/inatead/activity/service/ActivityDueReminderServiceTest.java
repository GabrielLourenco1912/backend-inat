package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.notification.dto.NotificationDtos;
import dev.lourencogabriel.inatead.notification.service.NotificationService;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityDueReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant DUE_AT = NOW.plus(ActivityDueReminderService.REMINDER_ADVANCE);

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private ActivityDueReminderService service;
    private Activity activity;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"));
        service = new ActivityDueReminderService(
                activityRepository,
                userRepository,
                notificationService,
                clock
        );
        activity = activity(DUE_AT);
    }

    @Test
    void shouldNotifyOnlyUsersReturnedAsPendingLearners() {
        User pendingLearner = activeUser("pending-user");
        when(activityRepository.findByIdForDueReminder("activity-1"))
                .thenReturn(Optional.of(activity));
        when(userRepository.findActiveCohortRecipientsWithoutDeliveredSubmission(
                "cohort-1",
                "activity-1",
                LocalDate.of(2026, 9, 10),
                Set.of(
                        SubmissionStatus.SUBMITTED,
                        SubmissionStatus.LATE,
                        SubmissionStatus.GRADED
                )
        )).thenReturn(List.of(pendingLearner));

        assertTrue(service.process("activity-1", NOW));

        ArgumentCaptor<NotificationDtos.CreateRequest> requestCaptor =
                ArgumentCaptor.forClass(NotificationDtos.CreateRequest.class);
        verify(notificationService).createForCohortRecipients(
                requestCaptor.capture(),
                eq("creator-user"),
                eq(List.of(pendingLearner))
        );
        NotificationDtos.CreateRequest request = requestCaptor.getValue();
        assertEquals("ACTIVITY_DUE_SOON", request.notificationType());
        assertEquals(NotificationAudienceType.COHORT, request.audience().type());
        assertEquals("cohort-1", request.audience().targetId());
        assertEquals(NotificationPriority.HIGH, request.priority());
        assertEquals(DUE_AT, request.expiresAt());
        assertNull(request.scheduledAt());
        assertEquals(
                Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                request.channels()
        );
        assertEquals(NOW, activity.getDueReminderProcessedAt());
        verify(activityRepository).save(activity);
    }

    @Test
    void shouldMarkReminderAsProcessedWithoutCreatingEmptyNotification() {
        when(activityRepository.findByIdForDueReminder("activity-1"))
                .thenReturn(Optional.of(activity));
        when(userRepository.findActiveCohortRecipientsWithoutDeliveredSubmission(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        assertFalse(service.process("activity-1", NOW));

        assertEquals(NOW, activity.getDueReminderProcessedAt());
        verify(notificationService, never()).createForCohortRecipients(any(), any(), any());
        verify(activityRepository).save(activity);
    }

    @Test
    void shouldIgnoreActivityBeforeThreeHourWindow() {
        activity = activity(DUE_AT.plusSeconds(1));
        when(activityRepository.findByIdForDueReminder("activity-1"))
                .thenReturn(Optional.of(activity));

        assertFalse(service.process("activity-1", NOW));

        verify(userRepository, never())
                .findActiveCohortRecipientsWithoutDeliveredSubmission(any(), any(), any(), any());
        verify(notificationService, never()).createForCohortRecipients(any(), any(), any());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void shouldIgnorePublishedActivityWhenItsLessonIsCancelled() {
        activity.getLesson().setStatus(LessonStatus.CANCELLED);
        when(activityRepository.findByIdForDueReminder("activity-1"))
                .thenReturn(Optional.of(activity));

        assertFalse(service.process("activity-1", NOW));

        verify(userRepository, never())
                .findActiveCohortRecipientsWithoutDeliveredSubmission(any(), any(), any(), any());
        verify(notificationService, never()).createForCohortRecipients(any(), any(), any());
        verify(activityRepository, never()).save(any());
    }

    private Activity activity(Instant dueAt) {
        Cohort cohort = new Cohort();
        cohort.setId("cohort-1");
        Lesson lesson = new Lesson();
        lesson.setId("lesson-1");
        lesson.setCohort(cohort);
        lesson.setStatus(LessonStatus.SCHEDULED);

        Activity value = new Activity();
        value.setId("activity-1");
        value.setLesson(lesson);
        value.setCreatedByUser(activeUser("creator-user"));
        value.setTitle("Final activity");
        value.setStatus(ActivityStatus.PUBLISHED);
        value.setDueAt(dueAt);
        return value;
    }

    private User activeUser(String id) {
        User user = new User("User " + id, id + "@example.com", "hash");
        user.setUlid(id);
        user.setStatus(UserAccountStatus.ACTIVE);
        return user;
    }
}
