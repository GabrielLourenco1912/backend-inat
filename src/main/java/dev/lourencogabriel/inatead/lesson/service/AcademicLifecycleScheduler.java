package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.service.ActivityDueReminderService;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "api.academic.lifecycle.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AcademicLifecycleScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            AcademicLifecycleScheduler.class
    );

    private final ActivityRepository activityRepository;
    private final ActivityDueReminderService reminderService;
    private final AcademicLifecycleService lifecycleService;
    private final Clock clock;
    private final int reminderBatchSize;

    public AcademicLifecycleScheduler(
            ActivityRepository activityRepository,
            ActivityDueReminderService reminderService,
            AcademicLifecycleService lifecycleService,
            Clock clock,
            @Value("${api.academic.lifecycle.reminder-batch-size:200}") int reminderBatchSize
    ) {
        this.activityRepository = activityRepository;
        this.reminderService = reminderService;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
        this.reminderBatchSize = Math.max(1, Math.min(reminderBatchSize, 1000));
    }

    @Scheduled(fixedDelayString = "${api.academic.lifecycle.interval:60000}")
    public void synchronizeAcademicStatuses() {
        Instant now = Instant.now(clock);
        List<String> candidates = activityRepository.findDueReminderCandidateIds(
                ActivityStatus.PUBLISHED,
                now,
                now.plus(ActivityDueReminderService.REMINDER_ADVANCE),
                PageRequest.of(0, reminderBatchSize)
        );
        int remindersCreated = 0;
        for (String activityId : candidates) {
            try {
                if (reminderService.process(activityId, now)) {
                    remindersCreated++;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Could not process due reminder for activity {}", activityId, exception);
            }
        }

        AcademicLifecycleService.UpdateSummary summary = lifecycleService.updateStatuses(now);
        if (remindersCreated > 0 || summary.hasChanges()) {
            LOGGER.info(
                    "Academic lifecycle synchronized: reminders={}, lessonsStarted={}, "
                            + "lessonsCompleted={}, activitiesClosed={}",
                    remindersCreated,
                    summary.startedLessons(),
                    summary.completedLessons(),
                    summary.closedActivities()
            );
        }
    }
}
