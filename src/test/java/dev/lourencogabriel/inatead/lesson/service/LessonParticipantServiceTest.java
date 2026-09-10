package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.dto.LessonParticipantDtos;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationType;
import dev.lourencogabriel.inatead.user.model.User;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonParticipantServiceTest {

    @Mock
    private LessonParticipantRepository participantRepository;
    @Mock
    private AttendanceRecordRepository attendanceRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LearnerRepository learnerRepository;
    @Mock
    private CohortEnrollmentRepository enrollmentRepository;
    @Mock
    private UserRepository userRepository;

    private LessonParticipantService service;
    private LessonParticipant source;
    private Lesson targetLesson;
    private Learner learner;

    @BeforeEach
    void setUp() {
        service = new LessonParticipantService(
                participantRepository,
                attendanceRepository,
                lessonRepository,
                learnerRepository,
                enrollmentRepository,
                userRepository,
                Clock.fixed(
                        Instant.parse("2026-08-20T12:00:00Z"),
                        ZoneId.of("America/Sao_Paulo")
                )
        );

        learner = new Learner();
        learner.setId("learner");
        learner.setStatus(dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE);
        var learnerPerson = new dev.lourencogabriel.inatead.person.model.Person();
        learnerPerson.setStatus(dev.lourencogabriel.inatead.shared.model.RecordStatus.ACTIVE);
        learner.setPerson(learnerPerson);
        Cohort sourceCohort = cohort("cohort-a");
        Cohort targetCohort = cohort("cohort-b");
        Lesson sourceLesson = lesson("lesson-a", sourceCohort);
        targetLesson = lesson("lesson-b", targetCohort);

        Contract contract = new Contract();
        contract.setLearner(learner);
        contract.setStatus(dev.lourencogabriel.inatead.shared.model.ContractStatus.ACTIVE);
        CohortEnrollment enrollment = new CohortEnrollment();
        enrollment.setId("enrollment");
        enrollment.setContract(contract);
        enrollment.setCohort(sourceCohort);
        enrollment.setStartDate(LocalDate.of(2026, 1, 1));
        enrollment.setEndDate(LocalDate.of(2026, 12, 31));
        enrollment.setStatus(EnrollmentStatus.ACTIVE);

        source = new LessonParticipant();
        source.setId("participant-a");
        source.setLesson(sourceLesson);
        source.setLearner(learner);
        source.setSourceEnrollment(enrollment);
        source.setParticipationType(ParticipationType.REGULAR);
        source.setStatus(ParticipationStatus.EXPECTED);
    }

    @Test
    void shouldMoveExpectedRosterWithoutChangingHomeEnrollment() {
        CohortEnrollment homeEnrollment = source.getSourceEnrollment();
        User actor = new User("Staff", "staff@example.com", "hash");
        actor.setUlid("actor");
        when(participantRepository.findById("participant-a")).thenReturn(Optional.of(source));
        when(attendanceRepository.existsByLessonParticipant_Id("participant-a")).thenReturn(false);
        when(lessonRepository.findById("lesson-b")).thenReturn(Optional.of(targetLesson));
        when(participantRepository.existsByLesson_IdAndLearner_Id("lesson-b", "learner"))
                .thenReturn(false);
        when(userRepository.findById("actor")).thenReturn(Optional.of(actor));
        when(participantRepository.save(any(LessonParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonParticipantDtos.ReassignmentResponse response = service.reassign(
                new LessonParticipantDtos.ReassignRequest(
                        "participant-a",
                        "lesson-b",
                        "Operational reassignment"
                ),
                "actor"
        );

        assertEquals(ParticipationStatus.CANCELLED, source.getStatus());
        assertEquals(ParticipationType.TRANSFERRED, response.target().participationType());
        assertEquals("lesson-b", response.target().lessonId());
        assertEquals("enrollment", response.target().sourceEnrollmentId());
        assertSame(homeEnrollment, source.getSourceEnrollment());
        verify(participantRepository).save(source);
    }

    @Test
    void shouldRejectReassignmentAfterConcreteAttendanceExists() {
        when(participantRepository.findById("participant-a")).thenReturn(Optional.of(source));
        when(attendanceRepository.existsByLessonParticipant_Id("participant-a")).thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> service.reassign(
                        new LessonParticipantDtos.ReassignRequest(
                                "participant-a",
                                "lesson-b",
                                "Too late"
                        ),
                        "actor"
                )
        );

        verify(lessonRepository, never()).findById("lesson-b");
        verify(participantRepository, never()).save(any());
    }

    @Test
    void shouldRejectParticipantWhenLearnerHasOverlappingLesson() {
        when(lessonRepository.findById("lesson-b")).thenReturn(Optional.of(targetLesson));
        when(learnerRepository.findById("learner")).thenReturn(Optional.of(learner));
        when(participantRepository.existsByLesson_IdAndLearner_Id("lesson-b", "learner"))
                .thenReturn(false);
        when(participantRepository.existsLearnerScheduleConflict(
                "learner",
                "lesson-b",
                targetLesson.getStartsAt(),
                targetLesson.getEndsAt(),
                ParticipationStatus.CANCELLED,
                LessonStatus.CANCELLED
        )).thenReturn(true);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.create(
                        new LessonParticipantDtos.Request(
                                "lesson-b",
                                "learner",
                                null,
                                ParticipationType.MAKEUP,
                                ParticipationStatus.EXPECTED,
                                "Makeup class"
                        ),
                        null
                )
        );

        assertEquals("The learner already has an overlapping lesson", exception.getMessage());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void shouldRequireTheAdministrativeReassignmentFlowForTransferredParticipants() {
        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.create(
                        new LessonParticipantDtos.Request(
                                "lesson-b",
                                "learner",
                                "enrollment",
                                ParticipationType.TRANSFERRED,
                                ParticipationStatus.EXPECTED,
                                "Direct transfer"
                        ),
                        null
                )
        );

        assertEquals(
                "Transferred participation must be created through the reassignment endpoint",
                exception.getMessage()
        );
        verify(lessonRepository, never()).findById(any());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void shouldNotAllowChangingAnExistingParticipationType() {
        when(participantRepository.findById("participant-a")).thenReturn(Optional.of(source));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.update(
                        "participant-a",
                        new LessonParticipantDtos.Request(
                                "lesson-a",
                                "learner",
                                "enrollment",
                                ParticipationType.TRANSFERRED,
                                ParticipationStatus.EXPECTED,
                                "Type change"
                        ),
                        "actor"
                )
        );

        assertEquals("participationType cannot be changed", exception.getMessage());
        verify(lessonRepository, never()).findById(any());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void shouldRejectDeletingAParticipantFromAClosedLesson() {
        source.getLesson().setStatus(LessonStatus.COMPLETED);
        when(participantRepository.findById("participant-a")).thenReturn(Optional.of(source));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.delete("participant-a")
        );

        assertEquals("Participants cannot be changed in a closed lesson", exception.getMessage());
        verify(attendanceRepository, never()).existsByLessonParticipant_Id(any());
        verify(participantRepository, never()).delete(any());
    }

    @Test
    void shouldUseTheApplicationTimeZoneForEnrollmentCoverage() {
        targetLesson.setStartsAt(Instant.parse("2026-08-21T01:00:00Z"));
        targetLesson.setEndsAt(Instant.parse("2026-08-21T02:00:00Z"));

        Contract contract = new Contract();
        contract.setLearner(learner);
        contract.setStatus(dev.lourencogabriel.inatead.shared.model.ContractStatus.ACTIVE);
        CohortEnrollment targetEnrollment = new CohortEnrollment();
        targetEnrollment.setId("target-enrollment");
        targetEnrollment.setContract(contract);
        targetEnrollment.setCohort(targetLesson.getCohort());
        targetEnrollment.setStartDate(LocalDate.of(2026, 1, 1));
        targetEnrollment.setEndDate(LocalDate.of(2026, 8, 20));
        targetEnrollment.setStatus(EnrollmentStatus.ACTIVE);

        when(lessonRepository.findById("lesson-b")).thenReturn(Optional.of(targetLesson));
        when(learnerRepository.findById("learner")).thenReturn(Optional.of(learner));
        when(enrollmentRepository.findById("target-enrollment"))
                .thenReturn(Optional.of(targetEnrollment));
        when(participantRepository.existsByLesson_IdAndLearner_Id("lesson-b", "learner"))
                .thenReturn(false);
        when(participantRepository.save(any(LessonParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonParticipantDtos.Response response = service.create(
                new LessonParticipantDtos.Request(
                        "lesson-b",
                        "learner",
                        "target-enrollment",
                        ParticipationType.REGULAR,
                        ParticipationStatus.EXPECTED,
                        null
                ),
                null
        );

        assertEquals("target-enrollment", response.sourceEnrollmentId());
    }

    @Test
    void shouldUseTheApplicationTimeZoneWhenComparingReassignmentDates() {
        source.getLesson().setStartsAt(Instant.parse("2026-08-20T23:00:00Z"));
        source.getLesson().setEndsAt(Instant.parse("2026-08-21T00:00:00Z"));
        targetLesson.setStartsAt(Instant.parse("2026-08-21T01:00:00Z"));
        targetLesson.setEndsAt(Instant.parse("2026-08-21T02:00:00Z"));

        User actor = new User("Staff", "staff@example.com", "hash");
        actor.setUlid("actor");
        when(participantRepository.findById("participant-a")).thenReturn(Optional.of(source));
        when(attendanceRepository.existsByLessonParticipant_Id("participant-a")).thenReturn(false);
        when(lessonRepository.findById("lesson-b")).thenReturn(Optional.of(targetLesson));
        when(participantRepository.existsByLesson_IdAndLearner_Id("lesson-b", "learner"))
                .thenReturn(false);
        when(userRepository.findById("actor")).thenReturn(Optional.of(actor));
        when(participantRepository.save(any(LessonParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonParticipantDtos.ReassignmentResponse response = service.reassign(
                new LessonParticipantDtos.ReassignRequest(
                        "participant-a",
                        "lesson-b",
                        "Same date in Sao Paulo"
                ),
                "actor"
        );

        assertEquals("lesson-b", response.target().lessonId());
    }

    private Cohort cohort(String id) {
        Cohort cohort = new Cohort();
        cohort.setId(id);
        cohort.setStatus(dev.lourencogabriel.inatead.shared.model.CohortStatus.ACTIVE);
        return cohort;
    }

    private Lesson lesson(String id, Cohort cohort) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setCohort(cohort);
        lesson.setStatus(LessonStatus.SCHEDULED);
        lesson.setStartsAt(Instant.parse("2026-08-20T12:00:00Z"));
        lesson.setEndsAt(Instant.parse("2026-08-20T14:00:00Z"));
        return lesson;
    }
}
