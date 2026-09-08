package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.lesson.dto.LessonDtos;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonServiceScheduleConflictTest {

    private static final Instant STARTS_AT = Instant.parse("2026-09-10T13:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-09-10T15:00:00Z");

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private CohortRepository cohortRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LessonParticipantRepository participantRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private NotificationRepository notificationRepository;

    private LessonService service;
    private Cohort cohort;
    private Person instructor;

    @BeforeEach
    void setUp() {
        service = new LessonService(
                lessonRepository,
                cohortRepository,
                personRepository,
                userRepository,
                participantRepository,
                activityRepository,
                notificationRepository,
                Clock.fixed(STARTS_AT, ZoneOffset.UTC)
        );

        cohort = new Cohort();
        cohort.setId("cohort");
        cohort.setStartDate(LocalDate.of(2026, 1, 1));
        cohort.setEndDate(LocalDate.of(2026, 12, 31));

        instructor = new Person();
        instructor.setId("instructor");
    }

    @Test
    void shouldRejectCreationWhenInstructorHasOverlappingLesson() {
        stubReferences();
        when(lessonRepository.existsInstructorScheduleConflict(
                "instructor",
                null,
                STARTS_AT,
                ENDS_AT,
                LessonStatus.CANCELLED
        )).thenReturn(true);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.create(request())
        );

        assertEquals("The instructor already has an overlapping lesson", exception.getMessage());
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldPersistAndExposeExternalLessonUrl() {
        stubReferences();
        when(lessonRepository.save(any(Lesson.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonDtos.Response response = service.create(request());

        assertEquals(
                "https://www.youtube.com/watch?v=lesson-test",
                response.externalLessonUrl()
        );
    }

    @Test
    void shouldRejectScheduleUpdateWhenAListedLearnerHasAnotherLesson() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();
        when(participantRepository.existsScheduleConflictForLessonParticipants(
                "lesson",
                STARTS_AT,
                ENDS_AT,
                ParticipationStatus.CANCELLED,
                LessonStatus.CANCELLED
        )).thenReturn(true);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.update("lesson", request())
        );

        assertEquals(
                "At least one learner already has an overlapping lesson",
                exception.getMessage()
        );
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldCancelOpenActivitiesAndExpireDueRemindersWithTheLesson() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();
        when(lessonRepository.save(any(Lesson.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.update("lesson", cancelledRequest());

        verify(activityRepository).cancelOpenActivitiesByLessonId(
                "lesson",
                Set.of(ActivityStatus.DRAFT, ActivityStatus.PUBLISHED),
                ActivityStatus.CANCELLED,
                STARTS_AT
        );
        verify(notificationRepository).expireActiveActivityNotificationsForLesson(
                "lesson",
                "ACTIVITY_DUE_SOON",
                "ACTIVITY",
                STARTS_AT
        );
    }

    private void stubReferences() {
        when(cohortRepository.findById("cohort")).thenReturn(Optional.of(cohort));
        when(personRepository.findById("instructor")).thenReturn(Optional.of(instructor));
        when(userRepository.existsActivePersonWithRole(
                "instructor",
                UserAccountStatus.ACTIVE,
                "INSTRUCTOR"
        )).thenReturn(true);
    }

    private LessonDtos.Request request() {
        return new LessonDtos.Request(
                "cohort",
                "instructor",
                "Schedule conflict test",
                STARTS_AT,
                ENDS_AT,
                DeliveryMode.ONSITE,
                "Room 1",
                null,
                "https://www.youtube.com/watch?v=lesson-test",
                LessonStatus.SCHEDULED
        );
    }

    private LessonDtos.Request cancelledRequest() {
        return new LessonDtos.Request(
                "cohort",
                "instructor",
                "Cancelled lesson",
                STARTS_AT,
                ENDS_AT,
                DeliveryMode.ONSITE,
                "Room 1",
                null,
                null,
                LessonStatus.CANCELLED
        );
    }

    private Lesson lesson() {
        Lesson lesson = new Lesson();
        lesson.setCohort(cohort);
        lesson.setInstructorPerson(instructor);
        lesson.setTitle("Existing lesson");
        lesson.setStartsAt(STARTS_AT.minusSeconds(3600));
        lesson.setEndsAt(ENDS_AT.minusSeconds(3600));
        lesson.setDeliveryMode(DeliveryMode.ONSITE);
        lesson.setRoom("Room 1");
        lesson.setStatus(LessonStatus.SCHEDULED);
        return lesson;
    }
}
