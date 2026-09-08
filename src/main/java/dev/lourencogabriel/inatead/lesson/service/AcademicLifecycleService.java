package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AcademicLifecycleService {

    private static final Set<LessonStatus> OPEN_LESSON_STATUSES = Set.of(
            LessonStatus.SCHEDULED,
            LessonStatus.IN_PROGRESS
    );

    private final LessonRepository lessonRepository;
    private final ActivityRepository activityRepository;

    @Transactional
    public UpdateSummary updateStatuses(Instant now) {
        int completedLessons = lessonRepository.completeElapsedLessons(
                now,
                OPEN_LESSON_STATUSES,
                LessonStatus.COMPLETED
        );
        int startedLessons = lessonRepository.startCurrentLessons(
                now,
                LessonStatus.SCHEDULED,
                LessonStatus.IN_PROGRESS
        );
        int closedActivities = activityRepository.closeExpiredPublishedActivities(
                now,
                ActivityStatus.PUBLISHED,
                ActivityStatus.CLOSED
        );
        return new UpdateSummary(startedLessons, completedLessons, closedActivities);
    }

    public record UpdateSummary(
            int startedLessons,
            int completedLessons,
            int closedActivities
    ) {
        public boolean hasChanges() {
            return startedLessons > 0 || completedLessons > 0 || closedActivities > 0;
        }
    }
}
