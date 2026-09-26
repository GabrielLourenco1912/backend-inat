package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.lesson.model.AttendanceRecord;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.project.OnlineAttendanceClosingPolicy;
import dev.lourencogabriel.inatead.shared.model.AttendanceStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnlineLessonAttendanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-06T14:30:00Z");

    @Mock private ActivityRepository activityRepository;
    @Mock private ActivitySubmissionRepository submissionRepository;
    @Mock private LessonParticipantRepository participantRepository;
    @Mock private AttendanceRecordRepository attendanceRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private OnlineAttendanceClosingPolicy attendanceClosingPolicy;
    @InjectMocks private OnlineLessonAttendanceService service;

    private Lesson lesson;
    private LessonParticipant participant;
    private User instructor;

    @BeforeEach
    void setUp() {
        lesson = new Lesson();
        lesson.setId("lesson-1");
        lesson.setDeliveryMode(DeliveryMode.ONLINE);
        lesson.setStatus(LessonStatus.COMPLETED);
        lesson.setStartsAt(Instant.parse("2026-09-06T13:00:00Z"));
        lesson.setEndsAt(Instant.parse("2026-09-06T15:00:00Z"));
        Person instructorPerson = new Person();
        instructorPerson.setId("instructor-person");
        lesson.setInstructorPerson(instructorPerson);

        Learner learner = new Learner();
        learner.setId("learner-1");
        participant = new LessonParticipant();
        participant.setId("participant-1");
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setStatus(ParticipationStatus.EXPECTED);
        instructor = new User("Instructor", "instructor@example.com", "hash");
    }

    @Test
    void shouldRecordPresenceWhenEveryActivityWasGradedAboveSix() {
        stubCandidate(null, 2, List.of(graded("6.01"), graded("9.00")), Optional.empty());
        when(userRepository.findByPerson_Id("instructor-person")).thenReturn(Optional.of(instructor));

        assertTrue(service.recordQualifiedAttendance("participant-1", NOW));

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(attendanceRepository).save(captor.capture());
        AttendanceRecord attendance = captor.getValue();
        assertEquals(AttendanceStatus.PRESENT, attendance.getStatus());
        assertEquals(lesson.getStartsAt(), attendance.getCheckInAt());
        assertEquals(lesson.getEndsAt(), attendance.getCheckOutAt());
        assertEquals(NOW, attendance.getRecordedAt());
        assertSame(instructor, attendance.getRecordedByUser());
    }

    @Test
    void shouldWaitUntilEveryActivityIsGraded() {
        ActivitySubmission ungraded = graded("9.00");
        ungraded.setStatus(SubmissionStatus.SUBMITTED);
        ungraded.setScore(null);
        ungraded.setGradedAt(null);
        stubCandidate(null, 2, List.of(graded("8.00"), ungraded), Optional.empty());

        assertFalse(service.recordQualifiedAttendance("participant-1", NOW));
        verify(attendanceRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectAGradeEqualToSix() {
        stubCandidate(null, 2, List.of(graded("8.00"), graded("6.00")), Optional.empty());

        assertFalse(service.recordQualifiedAttendance("participant-1", NOW));
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void shouldRespectTheEmployerSubmissionCutoff() {
        stubCandidate(null, 1, List.of(graded("8.00")), Optional.of(SUBMITTED_AT));

        assertFalse(service.recordQualifiedAttendance("participant-1", NOW));
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void shouldRequireAtLeastOneActivity() {
        when(participantRepository.findByIdForOnlinePresence("participant-1"))
                .thenReturn(Optional.of(participant));
        when(attendanceRepository.findByLessonParticipant_Id("participant-1"))
                .thenReturn(Optional.empty());
        when(activityRepository.countByLesson_IdAndStatusIn(
                "lesson-1", OnlineLessonAttendanceService.REQUIRED_ACTIVITY_STATUSES
        )).thenReturn(0L);

        assertFalse(service.recordQualifiedAttendance("participant-1", NOW));
        verifyNoInteractions(submissionRepository, userRepository);
    }

    @Test
    void shouldNeverRecordAutomaticPresenceForAnOnsiteLesson() {
        lesson.setDeliveryMode(DeliveryMode.ONSITE);
        when(participantRepository.findByIdForOnlinePresence("participant-1"))
                .thenReturn(Optional.of(participant));

        assertFalse(service.recordQualifiedAttendance("participant-1", NOW));
        verifyNoInteractions(activityRepository, submissionRepository, attendanceRepository, userRepository);
    }

    @Test
    void shouldConvertAnAutomaticAbsenceAfterTheGradesQualify() {
        AttendanceRecord existing = new AttendanceRecord();
        existing.setStatus(AttendanceStatus.ABSENT);
        existing.setNotes(OnlineLessonAttendanceService.AUTOMATIC_ABSENCE_NOTE);
        existing.setRecordedAt(lesson.getEndsAt());
        stubCandidate(existing, 1, List.of(graded("7.00")), Optional.empty());
        when(userRepository.findByPerson_Id("instructor-person")).thenReturn(Optional.of(instructor));

        assertTrue(service.recordQualifiedAttendance("participant-1", NOW));
        verify(attendanceRepository).save(existing);
        assertEquals(AttendanceStatus.PRESENT, existing.getStatus());
        assertEquals(NOW, existing.getRecordedAt());
    }

    @Test
    void shouldPreserveManualAttendance() {
        AttendanceRecord existing = new AttendanceRecord();
        existing.setStatus(AttendanceStatus.ABSENT);
        existing.setNotes("Recorded by the instructor");
        when(participantRepository.findByIdForOnlinePresence("participant-1"))
                .thenReturn(Optional.of(participant));
        when(attendanceRepository.findByLessonParticipant_Id("participant-1"))
                .thenReturn(Optional.of(existing));

        assertFalse(service.recordQualifiedAttendance("participant-1", NOW));
        verify(attendanceRepository, never()).save(any());
        verifyNoInteractions(activityRepository, submissionRepository, userRepository);
    }

    @Test
    void shouldRecordAbsenceForAnExpectedParticipantAfterTheLessonEnds() {
        when(lessonRepository.findByIdForOnlineAttendance("lesson-1"))
                .thenReturn(Optional.of(lesson));
        when(userRepository.findByPerson_Id("instructor-person"))
                .thenReturn(Optional.of(instructor));
        when(participantRepository.findAllForOnlineAttendance(
                "lesson-1", ParticipationStatus.EXPECTED
        )).thenReturn(List.of(participant));
        when(attendanceRepository.findByLessonParticipant_Id("participant-1"))
                .thenReturn(Optional.empty());

        assertEquals(1, service.recordMissingExpectedAttendance("lesson-1", NOW));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttendanceRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(attendanceRepository).saveAll(captor.capture());
        assertEquals(AttendanceStatus.ABSENT, captor.getValue().getFirst().getStatus());
    }

    private void stubCandidate(
            AttendanceRecord existing,
            long requiredActivities,
            List<ActivitySubmission> submissions,
            Optional<Instant> cutoff
    ) {
        when(participantRepository.findByIdForOnlinePresence("participant-1"))
                .thenReturn(Optional.of(participant));
        when(attendanceRepository.findByLessonParticipant_Id("participant-1"))
                .thenReturn(Optional.ofNullable(existing));
        when(activityRepository.countByLesson_IdAndStatusIn(
                "lesson-1", OnlineLessonAttendanceService.REQUIRED_ACTIVITY_STATUSES
        )).thenReturn(requiredActivities);
        when(attendanceClosingPolicy.submissionCutoffFor(participant)).thenReturn(cutoff);
        when(submissionRepository.findAllByActivity_Lesson_IdAndLearner_IdAndActivity_StatusIn(
                "lesson-1", "learner-1", OnlineLessonAttendanceService.REQUIRED_ACTIVITY_STATUSES
        )).thenReturn(submissions);
    }

    private ActivitySubmission graded(String score) {
        ActivitySubmission submission = new ActivitySubmission();
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setScore(new BigDecimal(score));
        submission.setSubmittedAt(SUBMITTED_AT);
        submission.setGradedAt(NOW);
        return submission;
    }
}
