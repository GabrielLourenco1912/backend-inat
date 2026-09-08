package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.notification.dto.NotificationDtos;
import dev.lourencogabriel.inatead.notification.service.NotificationService;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;

    private ActivityNotificationService service;

    @BeforeEach
    void setUp() {
        this.service = new ActivityNotificationService(
                this.notificationService,
                this.userRepository,
                Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo"))
        );
    }

    @Test
    void shouldNotifyActiveCohortUsersWhenActivityIsPublished() {
        Activity activity = activity();
        when(this.userRepository.findActiveCohortRecipients(
                "cohort-1",
                LocalDate.of(2026, 9, 4)
        )).thenReturn(List.of(user("learner-user")));

        this.service.notifyPublished(activity);

        ArgumentCaptor<NotificationDtos.CreateRequest> captor = ArgumentCaptor.forClass(
                NotificationDtos.CreateRequest.class
        );
        verify(this.notificationService).create(captor.capture(), eq("creator-user"));
        NotificationDtos.CreateRequest request = captor.getValue();
        assertEquals("ACTIVITY_PUBLISHED", request.notificationType());
        assertEquals(NotificationAudienceType.COHORT, request.audience().type());
        assertEquals("cohort-1", request.audience().targetId());
        assertEquals("ACTIVITY", request.contextType());
        assertEquals("activity-1", request.contextId());
        assertEquals(activity.getAvailableAt(), request.scheduledAt());
        assertNull(request.expiresAt());
        assertEquals(
                Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                request.channels()
        );
    }

    @Test
    void shouldNotCreateNotificationForCohortWithoutActiveUsers() {
        Activity activity = activity();
        when(this.userRepository.findActiveCohortRecipients(
                "cohort-1",
                LocalDate.of(2026, 9, 4)
        )).thenReturn(List.of());

        this.service.notifyPublished(activity);

        verify(this.notificationService, never()).create(any(), any());
    }

    @Test
    void shouldNotifyActiveLessonInstructorWhenSubmissionIsSent() {
        ActivitySubmission submission = submission();
        User instructor = user("instructor-user");
        when(this.userRepository.findByPerson_IdAndStatus(
                "instructor-person",
                UserAccountStatus.ACTIVE
        )).thenReturn(Optional.of(instructor));

        this.service.notifySubmitted(submission);

        ArgumentCaptor<NotificationDtos.CreateRequest> captor = ArgumentCaptor.forClass(
                NotificationDtos.CreateRequest.class
        );
        verify(this.notificationService).create(captor.capture(), eq(null));
        NotificationDtos.CreateRequest request = captor.getValue();
        assertEquals("ACTIVITY_SUBMITTED", request.notificationType());
        assertEquals(NotificationAudienceType.USER, request.audience().type());
        assertEquals("instructor-user", request.audience().targetId());
        assertEquals("ACTIVITY_SUBMISSION", request.contextType());
        assertEquals("submission-1", request.contextId());
        assertEquals(
                Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                request.channels()
        );
    }

    private Activity activity() {
        Cohort cohort = new Cohort();
        cohort.setId("cohort-1");
        cohort.setName("Cohort One");

        Person instructorPerson = new Person();
        instructorPerson.setId("instructor-person");

        Lesson lesson = new Lesson();
        lesson.setId("lesson-1");
        lesson.setCohort(cohort);
        lesson.setInstructorPerson(instructorPerson);

        Activity activity = new Activity();
        activity.setId("activity-1");
        activity.setLesson(lesson);
        activity.setCreatedByUser(user("creator-user"));
        activity.setTitle("Final project");
        activity.setAvailableAt(NOW.plusSeconds(3600));
        activity.setDueAt(NOW.plusSeconds(7200));
        return activity;
    }

    private ActivitySubmission submission() {
        Person learnerPerson = new Person();
        learnerPerson.setId("learner-person");
        learnerPerson.setFullName("Learner Name");

        Learner learner = new Learner();
        learner.setId("learner-1");
        learner.setPerson(learnerPerson);

        ActivitySubmission submission = new ActivitySubmission();
        submission.setId("submission-1");
        submission.setActivity(activity());
        submission.setLearner(learner);
        return submission;
    }

    private User user(String id) {
        User user = new User("User " + id, id + "@example.com", "hash");
        user.setUlid(id);
        user.setStatus(UserAccountStatus.ACTIVE);
        return user;
    }
}
