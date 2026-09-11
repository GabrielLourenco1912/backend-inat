package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.project.ApprenticeshipPolicy;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.lesson.dto.LessonDtos;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationType;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    @Mock
    private ApprenticeshipPolicy apprenticeshipPolicy;
    @Mock
    private LessonParticipantService participantService;

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
                Clock.fixed(STARTS_AT, ZoneId.of("America/Sao_Paulo")),
                apprenticeshipPolicy,
                participantService
        );

        cohort = new Cohort();
        cohort.setId("cohort");
        cohort.setStartDate(LocalDate.of(2026, 1, 1));
        cohort.setEndDate(LocalDate.of(2026, 12, 31));
        cohort.setStatus(CohortStatus.ACTIVE);

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

    @Test
    void shouldAllowAnOnlineLessonWithoutMeetingUrl() {
        stubReferences();
        when(lessonRepository.save(any(Lesson.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonDtos.Response response = service.create(new LessonDtos.Request(
                "cohort",
                "instructor",
                "Online lesson without meeting",
                STARTS_AT,
                ENDS_AT,
                DeliveryMode.ONLINE,
                null,
                null,
                null,
                LessonStatus.SCHEDULED
        ));

        assertEquals(DeliveryMode.ONLINE, response.deliveryMode());
        assertNull(response.meetingUrl());
    }

    @Test
    void shouldAutomaticallyGenerateRosterWhenCreatingAnOnlineLesson() {
        stubReferences();
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> {
            Lesson saved = invocation.getArgument(0);
            saved.setId("online-lesson");
            return saved;
        });

        service.create(onlineRequest());

        verify(participantService).generateRegularRoster("online-lesson");
    }

    @Test
    void shouldKeepOnsiteRosterGenerationManual() {
        stubReferences();
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request());

        verify(participantService, never()).generateRegularRoster(any());
    }

    @Test
    void shouldRejectSwitchingToOnlineWhenAnExistingParticipantIsIneligible() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        LessonParticipant participant = extraParticipant(lesson);
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();
        when(participantRepository.findAllByLesson_Id("lesson")).thenReturn(List.of(participant));
        doThrow(new BusinessRuleException("Online lessons require 30 hours"))
                .when(apprenticeshipPolicy).validateParticipation(participant, lesson, LocalDate.of(2026, 9, 10));

        assertThrows(BusinessRuleException.class, () -> service.update("lesson", onlineRequest()));

        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldRevalidateEligibleParticipantsWhenSwitchingToOnline() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        LessonParticipant participant = extraParticipant(lesson);
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();
        when(participantRepository.findAllByLesson_Id("lesson")).thenReturn(List.of(participant));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(DeliveryMode.ONLINE, service.update("lesson", onlineRequest()).deliveryMode());

        verify(apprenticeshipPolicy).validateParticipation(participant, lesson, LocalDate.of(2026, 9, 10));
    }

    @Test
    void shouldIgnoreCancelledParticipantsWhenSwitchingToOnline() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        LessonParticipant participant = extraParticipant(lesson);
        participant.setStatus(ParticipationStatus.CANCELLED);
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();
        when(participantRepository.findAllByLesson_Id("lesson")).thenReturn(List.of(participant));
        when(lessonRepository.save(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update("lesson", onlineRequest());

        verify(apprenticeshipPolicy, never()).validateParticipation(any(), any(), any());
    }

    @Test
    void shouldRequireScheduledStatusOnCreation() {
        when(cohortRepository.findById("cohort")).thenReturn(Optional.of(cohort));
        when(personRepository.findById("instructor")).thenReturn(Optional.of(instructor));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.create(request(LessonStatus.COMPLETED))
        );

        assertEquals("A lesson must be created with SCHEDULED status", exception.getMessage());
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldRejectAnOpenLessonForANonActiveCohort() {
        cohort.setStatus(CohortStatus.PLANNED);
        stubReferences();

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.create(request())
        );

        assertEquals("An open lesson requires an active cohort", exception.getMessage());
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldRejectAStatusRegressionFromCompletedToScheduled() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        lesson.setStatus(LessonStatus.COMPLETED);
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.update("lesson", request())
        );

        assertEquals(
                "Invalid lesson status transition from COMPLETED to SCHEDULED",
                exception.getMessage()
        );
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldRejectReopeningACancelledLesson() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        lesson.setStatus(LessonStatus.CANCELLED);
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.update("lesson", request())
        );

        assertEquals(
                "Invalid lesson status transition from CANCELLED to SCHEDULED",
                exception.getMessage()
        );
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldAllowCompletingAnElapsedInProgressLesson() {
        Instant startsAt = STARTS_AT.minusSeconds(7200);
        Instant endsAt = STARTS_AT.minusSeconds(3600);
        Lesson lesson = lesson();
        lesson.setId("lesson");
        lesson.setStartsAt(startsAt);
        lesson.setEndsAt(endsAt);
        lesson.setStatus(LessonStatus.IN_PROGRESS);
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();
        when(lessonRepository.save(any(Lesson.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonDtos.Response response = service.update("lesson", request(
                "cohort",
                startsAt,
                endsAt,
                LessonStatus.COMPLETED
        ));

        assertEquals(LessonStatus.COMPLETED, response.status());
    }

    @Test
    void shouldRejectInProgressStatusBeforeTheLessonStarts() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.update("lesson", request(
                        "cohort",
                        STARTS_AT.plusSeconds(3600),
                        ENDS_AT.plusSeconds(3600),
                        LessonStatus.IN_PROGRESS
                ))
        );

        assertEquals(
                "IN_PROGRESS status requires the current time to be inside the lesson period",
                exception.getMessage()
        );
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldRejectMovingARegularRosterToAnotherCohort() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        Cohort targetCohort = new Cohort();
        targetCohort.setId("target-cohort");
        targetCohort.setStartDate(LocalDate.of(2026, 1, 1));
        targetCohort.setEndDate(LocalDate.of(2026, 12, 31));
        targetCohort.setStatus(CohortStatus.ACTIVE);

        Learner learner = new Learner();
        learner.setId("learner");
        learner.setStatus(dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE);
        var learnerPerson = new Person();
        learnerPerson.setStatus(dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE);
        learner.setPerson(learnerPerson);
        Contract contract = new Contract();
        contract.setLearner(learner);
        contract.setStatus(dev.lourencogabriel.inatead.shared.model.ContractStatus.ACTIVE);
        CohortEnrollment enrollment = new CohortEnrollment();
        enrollment.setContract(contract);
        enrollment.setCohort(cohort);
        enrollment.setStartDate(LocalDate.of(2026, 1, 1));
        enrollment.setEndDate(LocalDate.of(2026, 12, 31));
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        LessonParticipant participant = new LessonParticipant();
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setSourceEnrollment(enrollment);
        participant.setParticipationType(ParticipationType.REGULAR);
        participant.setStatus(ParticipationStatus.EXPECTED);

        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        when(cohortRepository.findById("target-cohort")).thenReturn(Optional.of(targetCohort));
        when(personRepository.findById("instructor")).thenReturn(Optional.of(instructor));
        when(userRepository.existsActivePersonWithRole(
                "instructor",
                UserAccountStatus.ACTIVE,
                "INSTRUCTOR"
        )).thenReturn(true);
        when(participantRepository.findAllByLesson_Id("lesson"))
                .thenReturn(List.of(participant));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.update("lesson", request(
                        "target-cohort",
                        STARTS_AT,
                        ENDS_AT,
                        LessonStatus.SCHEDULED
                ))
        );

        assertEquals(
                "A regular participant must belong to the host cohort",
                exception.getMessage()
        );
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldRejectMovingALessonOutsideAnExistingEnrollmentPeriod() {
        Lesson lesson = lesson();
        lesson.setId("lesson");

        Learner learner = new Learner();
        learner.setId("learner");
        learner.setStatus(dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE);
        var learnerPerson = new Person();
        learnerPerson.setStatus(dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE);
        learner.setPerson(learnerPerson);
        Contract contract = new Contract();
        contract.setLearner(learner);
        contract.setStatus(dev.lourencogabriel.inatead.shared.model.ContractStatus.ACTIVE);
        CohortEnrollment enrollment = new CohortEnrollment();
        enrollment.setContract(contract);
        enrollment.setCohort(cohort);
        enrollment.setStartDate(LocalDate.of(2026, 1, 1));
        enrollment.setEndDate(LocalDate.of(2026, 9, 10));
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        LessonParticipant participant = new LessonParticipant();
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setSourceEnrollment(enrollment);
        participant.setParticipationType(ParticipationType.REGULAR);
        participant.setStatus(ParticipationStatus.EXPECTED);

        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        stubReferences();
        when(participantRepository.findAllByLesson_Id("lesson"))
                .thenReturn(List.of(participant));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.update("lesson", request(
                        "cohort",
                        STARTS_AT.plusSeconds(86400),
                        ENDS_AT.plusSeconds(86400),
                        LessonStatus.SCHEDULED
                ))
        );

        assertEquals(
                "The source enrollment is not active on the lesson date",
                exception.getMessage()
        );
        verify(lessonRepository, never()).save(any());
    }

    @Test
    void shouldUseTheApplicationTimeZoneForTheCohortDateBoundary() {
        cohort.setStartDate(LocalDate.of(2026, 9, 10));
        cohort.setEndDate(LocalDate.of(2026, 9, 10));
        stubReferences();
        when(lessonRepository.save(any(Lesson.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonDtos.Response response = service.create(request(
                "cohort",
                Instant.parse("2026-09-11T01:00:00Z"),
                Instant.parse("2026-09-11T02:00:00Z"),
                LessonStatus.SCHEDULED
        ));

        assertEquals("cohort", response.cohortId());
    }

    @Test
    void shouldRejectDeletingANonScheduledLesson() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        lesson.setStatus(LessonStatus.COMPLETED);
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.delete("lesson")
        );

        assertEquals("Only a scheduled lesson can be deleted", exception.getMessage());
        verify(lessonRepository, never()).delete(any());
    }

    @Test
    void shouldRejectDeletingALessonWithParticipants() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        when(participantRepository.existsByLesson_Id("lesson")).thenReturn(true);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.delete("lesson")
        );

        assertEquals("A lesson with participants cannot be deleted", exception.getMessage());
        verify(lessonRepository, never()).delete(any());
    }

    @Test
    void shouldRejectDeletingALessonWithActivities() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        when(activityRepository.existsByLesson_Id("lesson")).thenReturn(true);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.delete("lesson")
        );

        assertEquals("A lesson with activities cannot be deleted", exception.getMessage());
        verify(lessonRepository, never()).delete(any());
    }

    @Test
    void shouldDeleteAnEmptyScheduledLesson() {
        Lesson lesson = lesson();
        lesson.setId("lesson");
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));

        service.delete("lesson");

        verify(lessonRepository).delete(lesson);
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
        return request(LessonStatus.SCHEDULED);
    }

    private LessonDtos.Request request(LessonStatus status) {
        return request("cohort", STARTS_AT, ENDS_AT, status);
    }

    private LessonDtos.Request request(
            String cohortId,
            Instant startsAt,
            Instant endsAt,
            LessonStatus status
    ) {
        return new LessonDtos.Request(
                cohortId,
                "instructor",
                "Schedule conflict test",
                startsAt,
                endsAt,
                DeliveryMode.ONSITE,
                "Room 1",
                null,
                "https://www.youtube.com/watch?v=lesson-test",
                status
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

    private LessonDtos.Request onlineRequest() {
        return new LessonDtos.Request("cohort", "instructor", "Online lesson", STARTS_AT, ENDS_AT,
                DeliveryMode.ONLINE, null, null, null, LessonStatus.SCHEDULED);
    }

    private LessonParticipant extraParticipant(Lesson lesson) {
        Learner learner = new Learner();
        learner.setId("learner");
        learner.setPerson(new Person());
        LessonParticipant participant = new LessonParticipant();
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setParticipationType(ParticipationType.EXTRA);
        participant.setStatus(ParticipationStatus.EXPECTED);
        participant.setAssignmentReason("Additional lesson");
        return participant;
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
