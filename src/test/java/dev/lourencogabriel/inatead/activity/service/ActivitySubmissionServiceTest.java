package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.dto.ActivitySubmissionDtos;
import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.service.OnlineLessonAttendanceService;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivitySubmissionServiceTest {

    @Mock
    private ActivitySubmissionRepository submissionRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private LearnerRepository learnerRepository;
    @Mock
    private LessonParticipantRepository participantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityNotificationService notificationService;
    @Mock
    private OnlineLessonAttendanceService onlineLessonAttendanceService;

    private ActivitySubmissionService service;

    @BeforeEach
    void setUp() {
        this.service = new ActivitySubmissionService(
                this.submissionRepository,
                this.activityRepository,
                this.learnerRepository,
                this.participantRepository,
                this.userRepository,
                this.notificationService,
                this.onlineLessonAttendanceService
        );
    }

    @Test
    void shouldNotifyInstructorWhenLearnerSubmitsActivity() {
        stubDependencies();

        ActivitySubmissionDtos.Response response = this.service.create(
                request(SubmissionStatus.SUBMITTED),
                "actor-user"
        );

        assertEquals(SubmissionStatus.SUBMITTED, response.status());
        verify(this.onlineLessonAttendanceService).recordWhenAllActivitiesAreDelivered(
                any(ActivitySubmission.class),
                org.mockito.ArgumentMatchers.eq("actor-user")
        );
        verify(this.notificationService).notifySubmitted(any(ActivitySubmission.class));
    }

    @Test
    void shouldNotNotifyInstructorForDraftSubmission() {
        stubDependencies();

        this.service.create(request(SubmissionStatus.DRAFT), "actor-user");

        verify(this.onlineLessonAttendanceService, never())
                .recordWhenAllActivitiesAreDelivered(any(), any());
        verify(this.notificationService, never()).notifySubmitted(any(ActivitySubmission.class));
    }

    @Test
    void shouldAcceptLateSubmissionAfterActivityWasAutomaticallyClosed() {
        Activity activity = stubDependencies();
        activity.setStatus(ActivityStatus.CLOSED);
        activity.setDueAt(Instant.parse("2000-01-01T00:00:00Z"));

        ActivitySubmissionDtos.Response response = this.service.create(
                request(SubmissionStatus.SUBMITTED),
                "actor-user"
        );

        assertEquals(SubmissionStatus.LATE, response.status());
        verify(this.notificationService).notifySubmitted(any(ActivitySubmission.class));
    }

    @Test
    void shouldRejectSubmissionWhenTheLessonIsCancelled() {
        Lesson lesson = new Lesson();
        lesson.setId("lesson-1");
        lesson.setStatus(LessonStatus.CANCELLED);
        Activity activity = new Activity();
        activity.setId("activity-1");
        activity.setLesson(lesson);
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setDueAt(Instant.parse("2100-01-01T00:00:00Z"));
        Learner learner = new Learner();
        learner.setId("learner-1");
        when(this.submissionRepository.existsByActivity_IdAndLearner_Id(
                "activity-1",
                "learner-1"
        )).thenReturn(false);
        when(this.activityRepository.findById("activity-1")).thenReturn(Optional.of(activity));
        when(this.learnerRepository.findById("learner-1")).thenReturn(Optional.of(learner));
        activity.getLesson().setStatus(LessonStatus.CANCELLED);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> this.service.create(request(SubmissionStatus.SUBMITTED), "actor-user")
        );

        assertEquals("Submissions are blocked for a cancelled lesson", exception.getMessage());
        verify(this.submissionRepository, never()).save(any(ActivitySubmission.class));
        verify(this.notificationService, never()).notifySubmitted(any(ActivitySubmission.class));
    }

    @Test
    void shouldRejectSubmissionWhenTheLessonIsScheduled() {
        Lesson lesson = new Lesson();
        lesson.setId("lesson-1");
        lesson.setStatus(LessonStatus.SCHEDULED);
        Activity activity = new Activity();
        activity.setId("activity-1");
        activity.setLesson(lesson);
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setDueAt(Instant.parse("2100-01-01T00:00:00Z"));
        Learner learner = new Learner();
        learner.setId("learner-1");
        when(this.submissionRepository.existsByActivity_IdAndLearner_Id(
                "activity-1",
                "learner-1"
        )).thenReturn(false);
        when(this.activityRepository.findById("activity-1")).thenReturn(Optional.of(activity));
        when(this.learnerRepository.findById("learner-1")).thenReturn(Optional.of(learner));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> this.service.create(request(SubmissionStatus.SUBMITTED), "actor-user")
        );

        assertEquals(
                "Activities from scheduled lessons cannot receive submissions",
                exception.getMessage()
        );
        verify(this.submissionRepository, never()).save(any(ActivitySubmission.class));
        verify(this.onlineLessonAttendanceService, never())
                .recordWhenAllActivitiesAreDelivered(any(), any());
        verify(this.notificationService, never()).notifySubmitted(any(ActivitySubmission.class));
    }

    private Activity stubDependencies() {
        Lesson lesson = new Lesson();
        lesson.setId("lesson-1");
        Cohort cohort = new Cohort();
        cohort.setStatus(dev.lourencogabriel.inatead.shared.model.CohortStatus.ACTIVE);
        lesson.setCohort(cohort);
        lesson.setStatus(LessonStatus.IN_PROGRESS);

        Activity activity = new Activity();
        activity.setId("activity-1");
        activity.setLesson(lesson);
        activity.setTitle("Activity");
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setDueAt(Instant.parse("2100-01-01T00:00:00Z"));

        Person person = new Person();
        person.setId("learner-person");
        Learner learner = new Learner();
        learner.setId("learner-1");
        learner.setPerson(person);

        LessonParticipant participant = new LessonParticipant();
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setStatus(ParticipationStatus.EXPECTED);

        when(this.submissionRepository.existsByActivity_IdAndLearner_Id(
                "activity-1",
                "learner-1"
        )).thenReturn(false);
        when(this.activityRepository.findById("activity-1")).thenReturn(Optional.of(activity));
        when(this.learnerRepository.findById("learner-1")).thenReturn(Optional.of(learner));
        when(this.participantRepository.findByLesson_IdAndLearner_Id(
                "lesson-1",
                "learner-1"
        )).thenReturn(Optional.of(participant));
        when(this.submissionRepository.save(any(ActivitySubmission.class))).thenAnswer(invocation -> {
            ActivitySubmission submission = invocation.getArgument(0);
            submission.setId("submission-1");
            return submission;
        });
        return activity;
    }

    private ActivitySubmissionDtos.SaveRequest request(SubmissionStatus status) {
        return new ActivitySubmissionDtos.SaveRequest(
                "activity-1",
                "learner-1",
                "Answer",
                status
        );
    }
}
