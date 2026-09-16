package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.service.ActivityDueReminderService;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

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
    private OnlineLessonAttendanceService onlineAttendanceService;

    private AcademicLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AcademicLifecycleScheduler(
                activityRepository,
                reminderService,
                lifecycleService,
                lessonRepository,
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
                lessonRepository,
                onlineAttendanceService
        );
        order.verify(reminderService).process("activity-1", NOW);
        order.verify(reminderService).process("activity-2", NOW);
        order.verify(lifecycleService).updateStatuses(NOW);
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
}
