package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.service.ActivityDueReminderService;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.AttendanceStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class AcademicLifecycleSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityDueReminderService reminderService;
    @Mock
    private AcademicLifecycleService lifecycleService;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonParticipantRepository participantRepository;
    @Mock
    private OnlineLessonAttendanceService onlineAttendanceService;

    private AcademicLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AcademicLifecycleScheduler(
                activityRepository,
                reminderService,
                lifecycleService,
                lessonRepository,
                participantRepository,
                onlineAttendanceService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                200,
                200
        );
    }

    @Test
    void shouldCreateDueRemindersBeforeClosingExpiredActivities() {
        when(activityRepository.findDueReminderCandidateIds(
                ActivityStatus.PUBLISHED,
                NOW,
                NOW.plus(ActivityDueReminderService.REMINDER_ADVANCE),
                PageRequest.of(0, 200)
        )).thenReturn(List.of("activity-1", "activity-2"));
        when(reminderService.process("activity-1", NOW)).thenReturn(true);
        when(reminderService.process("activity-2", NOW)).thenReturn(false);
        when(lifecycleService.updateStatuses(NOW)).thenReturn(
                new AcademicLifecycleService.UpdateSummary(1, 2, 3)
        );
        when(participantRepository.findOnlinePresenceCandidateIds(
                "",
                ParticipationStatus.EXPECTED,
                DeliveryMode.ONLINE,
                Set.of(LessonStatus.IN_PROGRESS, LessonStatus.COMPLETED),
                OnlineLessonAttendanceService.REQUIRED_ACTIVITY_STATUSES,
                SubmissionStatus.GRADED,
                OnlineLessonAttendanceService.MINIMUM_PASSING_SCORE,
                AttendanceStatus.ABSENT,
                OnlineLessonAttendanceService.AUTOMATIC_ABSENCE_NOTES,
                PageRequest.of(0, 200)
        )).thenReturn(List.of("participant-1"));
        when(onlineAttendanceService.recordQualifiedAttendance("participant-1", NOW))
                .thenReturn(true);
        when(lessonRepository.findOnlineAttendanceCandidateIds(
                NOW,
                DeliveryMode.ONLINE,
                LessonStatus.CANCELLED,
                ParticipationStatus.EXPECTED,
                PageRequest.of(0, 200)
        )).thenReturn(List.of("lesson-1"));
        when(onlineAttendanceService.recordMissingExpectedAttendance("lesson-1", NOW))
                .thenReturn(3);

        scheduler.synchronizeAcademicStatuses();

        InOrder order = inOrder(
                reminderService,
                lifecycleService,
                participantRepository,
                lessonRepository,
                onlineAttendanceService
        );
        order.verify(reminderService).process("activity-1", NOW);
        order.verify(reminderService).process("activity-2", NOW);
        order.verify(lifecycleService).updateStatuses(NOW);
        order.verify(participantRepository).findOnlinePresenceCandidateIds(
                "",
                ParticipationStatus.EXPECTED,
                DeliveryMode.ONLINE,
                Set.of(LessonStatus.IN_PROGRESS, LessonStatus.COMPLETED),
                OnlineLessonAttendanceService.REQUIRED_ACTIVITY_STATUSES,
                SubmissionStatus.GRADED,
                OnlineLessonAttendanceService.MINIMUM_PASSING_SCORE,
                AttendanceStatus.ABSENT,
                OnlineLessonAttendanceService.AUTOMATIC_ABSENCE_NOTES,
                PageRequest.of(0, 200)
        );
        order.verify(onlineAttendanceService)
                .recordQualifiedAttendance("participant-1", NOW);
        order.verify(lessonRepository).findOnlineAttendanceCandidateIds(
                NOW,
                DeliveryMode.ONLINE,
                LessonStatus.CANCELLED,
                ParticipationStatus.EXPECTED,
                PageRequest.of(0, 200)
        );
        order.verify(onlineAttendanceService)
                .recordMissingExpectedAttendance("lesson-1", NOW);
    }

    @Test
    void shouldContinuePastAFullPresenceBatch() {
        scheduler = new AcademicLifecycleScheduler(
                activityRepository,
                reminderService,
                lifecycleService,
                lessonRepository,
                participantRepository,
                onlineAttendanceService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                200,
                2
        );
        when(lifecycleService.updateStatuses(NOW)).thenReturn(
                new AcademicLifecycleService.UpdateSummary(0, 0, 0)
        );
        when(participantRepository.findOnlinePresenceCandidateIds(
                anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "" -> List.of("participant-1", "participant-2");
            case "participant-2" -> List.of("participant-3");
            default -> List.of();
        });

        scheduler.synchronizeAcademicStatuses();

        verify(onlineAttendanceService).recordQualifiedAttendance("participant-1", NOW);
        verify(onlineAttendanceService).recordQualifiedAttendance("participant-2", NOW);
        verify(onlineAttendanceService).recordQualifiedAttendance("participant-3", NOW);
    }
}
