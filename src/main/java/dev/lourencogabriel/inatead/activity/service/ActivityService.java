package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.dto.ActivityDtos;
import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final ActivityNotificationService notificationService;

    @Transactional
    public ActivityDtos.Response create(ActivityDtos.Request request, String actorUserId) {
        Activity activity = new Activity();
        apply(activity, request);
        activity.setCreatedByUser(EntityLookup.require(userRepository, actorUserId, "User"));
        validate(activity, true);
        Activity saved = activityRepository.save(activity);
        if (saved.getStatus() == ActivityStatus.PUBLISHED) {
            this.notificationService.notifyPublished(saved);
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ActivityDtos.Response findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityDtos.Response> findAll(Pageable pageable) {
        return PageResponse.from(activityRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<ActivityDtos.Response> findByLesson(String lessonId, boolean includeDrafts) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        return activityRepository.findAllByLesson_IdOrderByAvailableAtAsc(lessonId).stream()
                .filter(activity -> includeDrafts || activity.getStatus() != ActivityStatus.DRAFT)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ActivityDtos.Response update(String id, ActivityDtos.Request request) {
        Activity activity = require(id);
        boolean wasPublished = activity.getStatus() == ActivityStatus.PUBLISHED;
        Instant previousDueAt = activity.getDueAt();
        if (!activity.getLesson().getId().equals(request.lessonId())) {
            throw new BusinessRuleException("lessonId cannot be changed");
        }
        apply(activity, request);
        if (!Objects.equals(previousDueAt, activity.getDueAt())
                || (!wasPublished && activity.getStatus() == ActivityStatus.PUBLISHED)) {
            activity.setDueReminderProcessedAt(null);
        }
        validate(activity, false);
        Activity saved = activityRepository.save(activity);
        if (!wasPublished && saved.getStatus() == ActivityStatus.PUBLISHED) {
            this.notificationService.notifyPublished(saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        activityRepository.delete(require(id));
    }

    private void apply(Activity activity, ActivityDtos.Request request) {
        activity.setLesson(EntityLookup.require(lessonRepository, request.lessonId(), "Lesson"));
        activity.setTitle(request.title().trim());
        activity.setDescription(request.description().trim());
        activity.setAvailableAt(request.availableAt());
        activity.setDueAt(request.dueAt());
        activity.setMaxScore(request.maxScore());
        activity.setStatus(request.status());
    }

    private void validate(Activity activity, boolean creating) {
        if (!activity.getDueAt().isAfter(activity.getAvailableAt())) {
            throw new BusinessRuleException("Activity due time must be after availability");
        }
        if (creating && activity.getLesson().getStatus() == LessonStatus.CANCELLED) {
            throw new BusinessRuleException("Activities cannot be created for a cancelled lesson");
        }
        if (activity.getLesson().getStatus() == LessonStatus.CANCELLED
                && activity.getStatus() != ActivityStatus.CANCELLED) {
            throw new BusinessRuleException("Only cancelled activities may belong to a cancelled lesson");
        }
    }

    private Activity require(String id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", id));
    }

    private ActivityDtos.Response toResponse(Activity activity) {
        return new ActivityDtos.Response(
                activity.getId(),
                activity.getLesson().getId(),
                activity.getCreatedByUser().getUlid(),
                activity.getTitle(),
                activity.getDescription(),
                activity.getAvailableAt(),
                activity.getDueAt(),
                activity.getMaxScore(),
                activity.getStatus(),
                activity.getCreatedAt(),
                activity.getUpdatedAt()
        );
    }
}
