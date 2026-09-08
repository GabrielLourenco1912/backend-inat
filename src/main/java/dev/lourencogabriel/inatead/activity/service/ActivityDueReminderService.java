package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.notification.dto.NotificationDtos;
import dev.lourencogabriel.inatead.notification.service.NotificationService;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ActivityDueReminderService {

    public static final Duration REMINDER_ADVANCE = Duration.ofHours(3);

    private static final Set<SubmissionStatus> DELIVERED_STATUSES = Set.of(
            SubmissionStatus.SUBMITTED,
            SubmissionStatus.LATE,
            SubmissionStatus.GRADED
    );
    private static final Set<NotificationChannel> CHANNELS = Set.of(
            NotificationChannel.IN_APP,
            NotificationChannel.EMAIL
    );
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy 'às' HH:mm"
    );

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public boolean process(String activityId, Instant now) {
        Activity activity = activityRepository.findByIdForDueReminder(activityId).orElse(null);
        if (!isEligible(activity, now)) {
            return false;
        }

        String cohortId = activity.getLesson().getCohort().getId();
        LocalDate referenceDate = LocalDate.ofInstant(now, clock.getZone());
        List<User> pendingLearners = userRepository
                .findActiveCohortRecipientsWithoutDeliveredSubmission(
                        cohortId,
                        activity.getId(),
                        referenceDate,
                        DELIVERED_STATUSES
                );

        boolean notificationCreated = !pendingLearners.isEmpty();
        if (notificationCreated) {
            String deadline = DEADLINE_FORMAT.withZone(clock.getZone())
                    .format(activity.getDueAt());
            notificationService.createForCohortRecipients(
                    new NotificationDtos.CreateRequest(
                            "ACTIVITY_DUE_SOON",
                            "Prazo próximo: " + activity.getTitle(),
                            "Faltam até três horas para o encerramento da atividade. Prazo: "
                                    + deadline + ".",
                            "/activities/" + activity.getId(),
                            "ACTIVITY",
                            activity.getId(),
                            null,
                            NotificationPriority.HIGH,
                            null,
                            activity.getDueAt(),
                            new NotificationDtos.AudienceRequest(
                                    NotificationAudienceType.COHORT,
                                    cohortId
                            ),
                            CHANNELS
                    ),
                    activity.getCreatedByUser().getUlid(),
                    pendingLearners
            );
        }

        activity.setDueReminderProcessedAt(now);
        activityRepository.save(activity);
        return notificationCreated;
    }

    private boolean isEligible(Activity activity, Instant now) {
        if (activity == null
                || activity.getStatus() != ActivityStatus.PUBLISHED
                || activity.getLesson().getStatus() == LessonStatus.CANCELLED
                || activity.getDueReminderProcessedAt() != null) {
            return false;
        }
        Instant dueAt = activity.getDueAt();
        return now.isBefore(dueAt)
                && !now.isBefore(dueAt.minus(REMINDER_ADVANCE));
    }
}
