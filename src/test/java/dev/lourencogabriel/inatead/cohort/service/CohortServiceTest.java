package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.cohort.dto.CohortDtos;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.service.LifecycleStatusHistoryService;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CohortServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo")
    );

    @Mock
    private CohortRepository cohortRepository;
    @Mock
    private CohortEnrollmentRepository enrollmentRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private CohortEnrollmentLifecycleService enrollmentLifecycleService;
    @Mock
    private LifecycleStatusHistoryService historyService;

    private CohortService service;

    @BeforeEach
    void setUp() {
        service = new CohortService(
                cohortRepository,
                enrollmentRepository,
                lessonRepository,
                activityRepository,
                notificationRepository,
                enrollmentLifecycleService,
                historyService,
                CLOCK
        );
        lenient().when(historyService.find(any(), any())).thenReturn(List.of());
    }

    @Test
    void shouldOnlyCreatePlannedCohorts() {
        assertThrows(BusinessRuleException.class, () -> service.create(request(
                CohortStatus.ACTIVE,
                null,
                null
        )));
    }

    @Test
    void shouldRejectCapacityBelowExistingReservations() {
        Cohort cohort = cohort(CohortStatus.ACTIVE);
        when(cohortRepository.findByIdForUpdate("cohort")).thenReturn(Optional.of(cohort));
        when(enrollmentRepository.countByCohort_IdAndStatusIn(any(), any())).thenReturn(2L);

        assertThrows(BusinessRuleException.class, () -> service.update(
                "cohort",
                request(CohortStatus.ACTIVE, 1, null),
                "admin"
        ));
    }

    @Test
    void shouldRejectCodeChangeAfterCohortActivation() {
        Cohort cohort = cohort(CohortStatus.ACTIVE);
        when(cohortRepository.findByIdForUpdate("cohort")).thenReturn(Optional.of(cohort));
        CohortDtos.Request changedCode = new CohortDtos.Request(
                "NEW-CODE", "Administration", 1, "MORNING",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                null, CohortStatus.ACTIVE, null
        );

        assertThrows(BusinessRuleException.class, () -> service.update("cohort", changedCode, "admin"));
    }

    @Test
    void shouldCascadeCohortCancellationToEnrollmentsLessonsAndActivities() {
        Cohort cohort = cohort(CohortStatus.ACTIVE);
        Lesson lesson = new Lesson();
        lesson.setId("lesson");
        lesson.setStatus(LessonStatus.SCHEDULED);
        when(cohortRepository.findByIdForUpdate("cohort")).thenReturn(Optional.of(cohort));
        when(cohortRepository.save(any(Cohort.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonRepository.findAllByCohort_Id("cohort")).thenReturn(List.of(lesson));

        CohortDtos.Response response = service.update(
                "cohort",
                request(CohortStatus.CANCELLED, null, "Cancelled by administration"),
                "admin"
        );

        assertEquals(CohortStatus.CANCELLED, response.status());
        assertEquals(LessonStatus.CANCELLED, lesson.getStatus());
        verify(enrollmentLifecycleService).cancelForCohort(
                "cohort",
                LocalDate.now(CLOCK),
                CLOCK.instant(),
                "admin",
                "Cohort cancelled: Cancelled by administration"
        );
        verify(activityRepository).cancelOpenActivitiesByLessonId(
                any(),
                any(),
                any(),
                any()
        );
        verify(notificationRepository).expireActiveActivityNotificationsForLesson(
                any(),
                any(),
                any(),
                any()
        );
    }

    private Cohort cohort(CohortStatus status) {
        Cohort cohort = new Cohort();
        cohort.setId("cohort");
        cohort.setCode("ADM-26A");
        cohort.setName("Administration");
        cohort.setDefaultWeekday(1);
        cohort.setShiftCode("MORNING");
        cohort.setStartDate(LocalDate.of(2026, 1, 1));
        cohort.setEndDate(LocalDate.of(2026, 12, 31));
        cohort.setStatus(status);
        return cohort;
    }

    private CohortDtos.Request request(
            CohortStatus status,
            Integer maxLearners,
            String reason
    ) {
        return new CohortDtos.Request(
                "ADM-26A",
                "Administration",
                1,
                "MORNING",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                maxLearners,
                status,
                reason
        );
    }
}
