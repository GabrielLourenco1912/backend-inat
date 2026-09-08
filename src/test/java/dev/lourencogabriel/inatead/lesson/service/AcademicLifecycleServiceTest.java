package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private ActivityRepository activityRepository;

    private AcademicLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new AcademicLifecycleService(lessonRepository, activityRepository);
    }

    @Test
    void shouldAdvanceLessonAndActivityStatuses() {
        when(lessonRepository.completeElapsedLessons(
                NOW,
                Set.of(LessonStatus.SCHEDULED, LessonStatus.IN_PROGRESS),
                LessonStatus.COMPLETED
        )).thenReturn(2);
        when(lessonRepository.startCurrentLessons(
                NOW,
                LessonStatus.SCHEDULED,
                LessonStatus.IN_PROGRESS
        )).thenReturn(3);
        when(activityRepository.closeExpiredPublishedActivities(
                NOW,
                ActivityStatus.PUBLISHED,
                ActivityStatus.CLOSED
        )).thenReturn(4);

        AcademicLifecycleService.UpdateSummary summary = service.updateStatuses(NOW);

        assertEquals(3, summary.startedLessons());
        assertEquals(2, summary.completedLessons());
        assertEquals(4, summary.closedActivities());
        assertTrue(summary.hasChanges());
        verify(lessonRepository).completeElapsedLessons(
                NOW,
                Set.of(LessonStatus.SCHEDULED, LessonStatus.IN_PROGRESS),
                LessonStatus.COMPLETED
        );
    }
}
