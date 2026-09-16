package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.model.Activity;
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
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
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

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnlineLessonAttendanceServiceTest {

    private static final Set<ActivityStatus> REQUIRED_ACTIVITY_STATUSES = Set.of(
            ActivityStatus.PUBLISHED,
            ActivityStatus.CLOSED
    );
    private static final Set<SubmissionStatus> DELIVERED_SUBMISSION_STATUSES = Set.of(
            SubmissionStatus.SUBMITTED,
            SubmissionStatus.LATE,
            SubmissionStatus.GRADED
    );

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivitySubmissionRepository submissionRepository;
    @Mock
    private LessonParticipantRepository participantRepository;
    @Mock
    private AttendanceRecordRepository attendanceRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private OnlineLessonAttendanceService service;

    private Lesson lesson;
    private LessonParticipant participant;
    private ActivitySubmission submission;

    @BeforeEach
    void setUp() {
        this.lesson = new Lesson();
        this.lesson.setId("lesson-1");
        this.lesson.setDeliveryMode(DeliveryMode.ONLINE);
        this.lesson.setStartsAt(Instant.parse("2026-09-06T13:00:00Z"));
        this.lesson.setEndsAt(Instant.parse("2026-09-06T15:00:00Z"));

        Activity activity = new Activity();
        activity.setId("activity-1");
        activity.setLesson(this.lesson);
        activity.setStatus(ActivityStatus.PUBLISHED);

        Learner learner = new Learner();
        learner.setId("learner-1");

        this.participant = new LessonParticipant();
        this.participant.setId("participant-1");
        this.participant.setLesson(this.lesson);
        this.participant.setLearner(learner);
        this.participant.setStatus(ParticipationStatus.EXPECTED);

        this.submission = new ActivitySubmission();
        this.submission.setActivity(activity);
        this.submission.setLearner(learner);
        this.submission.setStatus(SubmissionStatus.SUBMITTED);
    }

    @Test
    void shouldRecordPresenceWhenTheOnlyOnlineActivityIsDelivered() {
        User actor = new User("Actor", "actor@example.com", "hash");
        actor.setUlid("actor-user");
        when(participantRepository.findByLessonAndLearnerForUpdate(
                "lesson-1",
                "learner-1"
        )).thenReturn(Optional.of(this.participant));
        when(activityRepository.countByLesson_IdAndStatusIn(
                "lesson-1",
                REQUIRED_ACTIVITY_STATUSES
        )).thenReturn(1L);
        when(submissionRepository
                .countByActivity_Lesson_IdAndLearner_IdAndActivity_StatusInAndStatusIn(
                        "lesson-1",
                        "learner-1",
                        REQUIRED_ACTIVITY_STATUSES,
                        DELIVERED_SUBMISSION_STATUSES
                )).thenReturn(1L);
        when(attendanceRepository.findByLessonParticipant_Id("participant-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findById("actor-user")).thenReturn(Optional.of(actor));

        service.recordWhenAllActivitiesAreDelivered(this.submission, "actor-user");

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(
                AttendanceRecord.class
        );
        verify(attendanceRepository).save(captor.capture());
        AttendanceRecord attendance = captor.getValue();
        assertEquals(AttendanceStatus.PRESENT, attendance.getStatus());
        assertEquals(this.lesson.getStartsAt(), attendance.getCheckInAt());
        assertEquals(this.lesson.getEndsAt(), attendance.getCheckOutAt());
        assertSame(this.participant, attendance.getLessonParticipant());
        assertSame(actor, attendance.getRecordedByUser());
        assertNotNull(attendance.getRecordedAt());
        assertNotNull(attendance.getNotes());
    }

    @Test
    void shouldWaitUntilEveryOnlineActivityIsDelivered() {
        when(participantRepository.findByLessonAndLearnerForUpdate(
                "lesson-1",
                "learner-1"
        )).thenReturn(Optional.of(this.participant));
        when(activityRepository.countByLesson_IdAndStatusIn(
                "lesson-1",
                REQUIRED_ACTIVITY_STATUSES
        )).thenReturn(2L);
        when(submissionRepository
                .countByActivity_Lesson_IdAndLearner_IdAndActivity_StatusInAndStatusIn(
                        "lesson-1",
                        "learner-1",
                        REQUIRED_ACTIVITY_STATUSES,
                        DELIVERED_SUBMISSION_STATUSES
                )).thenReturn(1L);

        service.recordWhenAllActivitiesAreDelivered(this.submission, "actor-user");

        verify(attendanceRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRecordPresenceAfterAllOfMultipleActivitiesAreDelivered() {
        User actor = new User("Actor", "actor@example.com", "hash");
        when(participantRepository.findByLessonAndLearnerForUpdate(
                "lesson-1",
                "learner-1"
        )).thenReturn(Optional.of(this.participant));
        when(activityRepository.countByLesson_IdAndStatusIn(
                "lesson-1",
                REQUIRED_ACTIVITY_STATUSES
        )).thenReturn(3L);
        when(submissionRepository
                .countByActivity_Lesson_IdAndLearner_IdAndActivity_StatusInAndStatusIn(
                        "lesson-1",
                        "learner-1",
                        REQUIRED_ACTIVITY_STATUSES,
                        DELIVERED_SUBMISSION_STATUSES
                )).thenReturn(3L);
        when(attendanceRepository.findByLessonParticipant_Id("participant-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findById("actor-user")).thenReturn(Optional.of(actor));

        service.recordWhenAllActivitiesAreDelivered(this.submission, "actor-user");

        verify(attendanceRepository).save(any(AttendanceRecord.class));
    }

    @Test
    void shouldNeverGenerateAttendanceForAnOnsiteLesson() {
        this.lesson.setDeliveryMode(DeliveryMode.ONSITE);

        service.recordWhenAllActivitiesAreDelivered(this.submission, "actor-user");

        verifyNoInteractions(
                activityRepository,
                submissionRepository,
                participantRepository,
                attendanceRepository,
                userRepository
        );
    }

    @Test
    void shouldUpdateAnExistingAbsenceWhenAllActivitiesAreDelivered() {
        User actor = new User("Actor", "actor@example.com", "hash");
        actor.setUlid("actor-user");
        Instant originalRecordedAt = Instant.parse("2026-09-06T15:00:00Z");
        AttendanceRecord existing = new AttendanceRecord();
        existing.setLessonParticipant(this.participant);
        existing.setStatus(AttendanceStatus.ABSENT);
        existing.setRecordedAt(originalRecordedAt);
        when(participantRepository.findByLessonAndLearnerForUpdate(
                "lesson-1",
                "learner-1"
        )).thenReturn(Optional.of(this.participant));
        when(activityRepository.countByLesson_IdAndStatusIn(
                "lesson-1",
                REQUIRED_ACTIVITY_STATUSES
        )).thenReturn(2L);
        when(submissionRepository
                .countByActivity_Lesson_IdAndLearner_IdAndActivity_StatusInAndStatusIn(
                        "lesson-1",
                        "learner-1",
                        REQUIRED_ACTIVITY_STATUSES,
                        DELIVERED_SUBMISSION_STATUSES
                )).thenReturn(2L);
        when(attendanceRepository.findByLessonParticipant_Id("participant-1"))
                .thenReturn(Optional.of(existing));
        when(userRepository.findById("actor-user")).thenReturn(Optional.of(actor));

        service.recordWhenAllActivitiesAreDelivered(this.submission, "actor-user");

        verify(attendanceRepository).save(existing);
        assertEquals(AttendanceStatus.PRESENT, existing.getStatus());
        assertEquals(this.lesson.getStartsAt(), existing.getCheckInAt());
        assertEquals(this.lesson.getEndsAt(), existing.getCheckOutAt());
        assertEquals(originalRecordedAt, existing.getRecordedAt());
        assertSame(actor, existing.getRecordedByUser());
    }

    @Test
    void shouldRecordAbsenceForExpectedParticipantsMissingAttendanceAfterTheLessonEnds() {
        Person instructorPerson = new Person();
        instructorPerson.setId("instructor-person");
        this.lesson.setInstructorPerson(instructorPerson);
        this.lesson.setStatus(LessonStatus.COMPLETED);
        User instructor = new User("Instructor", "instructor@example.com", "hash");
        when(lessonRepository.findByIdForOnlineAttendance("lesson-1"))
                .thenReturn(Optional.of(this.lesson));
        when(userRepository.findByPerson_Id("instructor-person"))
                .thenReturn(Optional.of(instructor));
        when(participantRepository.findAllForOnlineAttendance(
                "lesson-1",
                ParticipationStatus.EXPECTED
        )).thenReturn(List.of(this.participant));
        when(attendanceRepository.findByLessonParticipant_Id("participant-1"))
                .thenReturn(Optional.empty());
        Instant recordedAt = Instant.parse("2026-09-06T15:01:00Z");

        int created = service.recordMissingExpectedAttendance("lesson-1", recordedAt);

        assertEquals(1, created);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttendanceRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(attendanceRepository).saveAll(captor.capture());
        AttendanceRecord absence = captor.getValue().getFirst();
        assertEquals(AttendanceStatus.ABSENT, absence.getStatus());
        assertEquals(recordedAt, absence.getRecordedAt());
        assertSame(this.participant, absence.getLessonParticipant());
        assertSame(instructor, absence.getRecordedByUser());
    }
}
