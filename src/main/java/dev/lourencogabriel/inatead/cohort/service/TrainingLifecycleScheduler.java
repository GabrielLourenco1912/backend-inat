package dev.lourencogabriel.inatead.cohort.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(
        name = "api.academic.lifecycle.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TrainingLifecycleScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingLifecycleScheduler.class);

    private final TrainingLifecycleService lifecycleService;
    private final Clock clock;

    public TrainingLifecycleScheduler(TrainingLifecycleService lifecycleService, Clock clock) {
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${api.academic.lifecycle.interval:60000}")
    public void synchronizeTrainingStatuses() {
        var summary = lifecycleService.updateStatuses(LocalDate.now(clock), clock.instant());
        if (summary.hasChanges()) {
            LOGGER.info(
                    "Training lifecycle synchronized: contractsEnded={}, cohortsCompleted={}, "
                            + "enrollmentsCompleted={}",
                    summary.endedContracts(),
                    summary.completedCohorts(),
                    summary.completedEnrollments()
            );
        }
    }
}
