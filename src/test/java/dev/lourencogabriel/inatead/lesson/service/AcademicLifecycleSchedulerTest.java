package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.service.ActivityDueReminderService;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
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

    private AcademicLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AcademicLifecycleScheduler(
                activityRepository,
                reminderService,
                lifecycleService,
                Clock.fixed(NOW, ZoneOffset.UTC),
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

        scheduler.synchronizeAcademicStatuses();

        InOrder order = inOrder(reminderService, lifecycleService);
        order.verify(reminderService).process("activity-1", NOW);
        order.verify(reminderService).process("activity-2", NOW);
        order.verify(lifecycleService).updateStatuses(NOW);
    }
}
