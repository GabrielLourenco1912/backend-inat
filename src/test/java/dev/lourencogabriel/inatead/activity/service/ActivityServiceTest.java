package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.dto.ActivityDtos;
import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final Instant AVAILABLE_AT = Instant.parse("2026-09-05T12:00:00Z");
    private static final Instant DUE_AT = Instant.parse("2026-09-06T12:00:00Z");

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityNotificationService notificationService;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        this.service = new ActivityService(
                this.activityRepository,
                this.lessonRepository,
                this.userRepository,
                this.notificationService
        );
    }

    @Test
    void shouldNotifyCohortWhenCreatingPublishedActivity() {
        stubDependencies();

        ActivityDtos.Response response = this.service.create(
                request(ActivityStatus.PUBLISHED),
                "creator-user"
        );

        assertEquals("activity-1", response.id());
        verify(this.notificationService).notifyPublished(any(Activity.class));
    }

    @Test
    void shouldNotNotifyCohortWhileActivityRemainsDraft() {
        stubDependencies();

        this.service.create(request(ActivityStatus.DRAFT), "creator-user");

        verify(this.notificationService, never()).notifyPublished(any(Activity.class));
    }

    @Test
    void shouldRejectEveryNewActivityForACancelledLesson() {
        Lesson lesson = lesson();
        lesson.setStatus(LessonStatus.CANCELLED);
        User creator = new User("Creator", "creator@example.com", "hash");
        creator.setUlid("creator-user");
        when(this.lessonRepository.findById("lesson-1")).thenReturn(Optional.of(lesson));
        when(this.userRepository.findById("creator-user")).thenReturn(Optional.of(creator));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> this.service.create(request(ActivityStatus.CANCELLED), "creator-user")
        );

        assertEquals(
                "Activities cannot be created for a cancelled lesson",
                exception.getMessage()
        );
        verify(this.activityRepository, never()).save(any(Activity.class));
        verify(this.notificationService, never()).notifyPublished(any(Activity.class));
    }

    @Test
    void shouldResetDueReminderWhenDeadlineChanges() {
        Lesson lesson = lesson();
        Activity activity = new Activity();
        activity.setId("activity-1");
        activity.setLesson(lesson);
        activity.setCreatedByUser(new User("Creator", "creator@example.com", "hash"));
        activity.setTitle("Activity");
        activity.setDescription("Description");
        activity.setAvailableAt(AVAILABLE_AT);
        activity.setDueAt(DUE_AT);
        activity.setDueReminderProcessedAt(AVAILABLE_AT);
        activity.setStatus(ActivityStatus.PUBLISHED);
        when(this.activityRepository.findById("activity-1")).thenReturn(Optional.of(activity));
        when(this.lessonRepository.findById("lesson-1")).thenReturn(Optional.of(lesson));
        when(this.activityRepository.save(any(Activity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        this.service.update(
                "activity-1",
                new ActivityDtos.Request(
                        "lesson-1",
                        "Activity",
                        "Description",
                        AVAILABLE_AT,
                        DUE_AT.plusSeconds(3600),
                        null,
                        ActivityStatus.PUBLISHED
                )
        );

        assertNull(activity.getDueReminderProcessedAt());
    }

    @Test
    void shouldHideDraftsFromLessonActivityListForLearners() {
        Lesson lesson = lesson();
        Activity draft = activity(lesson, "draft", ActivityStatus.DRAFT);
        Activity published = activity(lesson, "published", ActivityStatus.PUBLISHED);
        when(this.lessonRepository.existsById("lesson-1")).thenReturn(true);
        when(this.activityRepository.findAllByLesson_IdOrderByAvailableAtAsc("lesson-1"))
                .thenReturn(List.of(draft, published));

        List<ActivityDtos.Response> activities = this.service.findByLesson("lesson-1", false);

        assertEquals(
                List.of("published"),
                activities.stream().map(ActivityDtos.Response::id).toList()
        );
    }

    @Test
    void shouldIncludeDraftsForLessonManagers() {
        Lesson lesson = lesson();
        Activity draft = activity(lesson, "draft", ActivityStatus.DRAFT);
        when(this.lessonRepository.existsById("lesson-1")).thenReturn(true);
        when(this.activityRepository.findAllByLesson_IdOrderByAvailableAtAsc("lesson-1"))
                .thenReturn(List.of(draft));

        List<ActivityDtos.Response> activities = this.service.findByLesson("lesson-1", true);

        assertEquals(
                List.of("draft"),
                activities.stream().map(ActivityDtos.Response::id).toList()
        );
    }

    private Lesson stubDependencies() {
        Lesson lesson = lesson();
        User creator = new User("Creator", "creator@example.com", "hash");
        creator.setUlid("creator-user");

        when(this.lessonRepository.findById("lesson-1")).thenReturn(Optional.of(lesson));
        when(this.userRepository.findById("creator-user")).thenReturn(Optional.of(creator));
        when(this.activityRepository.save(any(Activity.class))).thenAnswer(invocation -> {
            Activity activity = invocation.getArgument(0);
            activity.setId("activity-1");
            return activity;
        });
        return lesson;
    }

    private Lesson lesson() {
        Lesson lesson = new Lesson();
        lesson.setId("lesson-1");
        lesson.setCohort(new Cohort());
        lesson.setStatus(LessonStatus.SCHEDULED);
        return lesson;
    }

    private Activity activity(Lesson lesson, String id, ActivityStatus status) {
        User creator = new User("Creator", "creator@example.com", "hash");
        creator.setUlid("creator-user");
        Activity activity = new Activity();
        activity.setId(id);
        activity.setLesson(lesson);
        activity.setCreatedByUser(creator);
        activity.setTitle("Activity " + id);
        activity.setDescription("Description");
        activity.setAvailableAt(AVAILABLE_AT);
        activity.setDueAt(DUE_AT);
        activity.setStatus(status);
        return activity;
    }

    private ActivityDtos.Request request(ActivityStatus status) {
        return new ActivityDtos.Request(
                "lesson-1",
                "Activity",
                "Description",
                AVAILABLE_AT,
                DUE_AT,
                null,
                status
        );
    }
}
