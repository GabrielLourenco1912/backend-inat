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

import java.time.Instant;
import java.time.LocalDate;
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
                userRepository
        );

        learner = new Learner();
        learner.setId("learner");
        Cohort sourceCohort = cohort("cohort-a");
        Cohort targetCohort = cohort("cohort-b");
        Lesson sourceLesson = lesson("lesson-a", sourceCohort);
        targetLesson = lesson("lesson-b", targetCohort);

        Contract contract = new Contract();
        contract.setLearner(learner);
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

    private Cohort cohort(String id) {
        Cohort cohort = new Cohort();
        cohort.setId(id);
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
