package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.notification.dto.NotificationDtos;
import dev.lourencogabriel.inatead.notification.service.NotificationService;
import dev.lourencogabriel.inatead.shared.model.NotificationAudienceType;
import dev.lourencogabriel.inatead.shared.model.NotificationChannel;
import dev.lourencogabriel.inatead.shared.model.NotificationPriority;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ActivityNotificationService {

    private static final Set<NotificationChannel> CHANNELS = Set.of(
            NotificationChannel.IN_APP,
            NotificationChannel.EMAIL
    );
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy 'às' HH:mm"
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final Clock clock;

    public void notifyPublished(Activity activity) {
        String cohortId = activity.getLesson().getCohort().getId();
        if (this.userRepository.findActiveCohortRecipients(
                cohortId,
                BusinessTime.today(this.clock)
        ).isEmpty()) {
            return;
        }

        String deadline = DEADLINE_FORMAT.withZone(BusinessTime.ZONE)
                .format(activity.getDueAt());
        this.notificationService.create(
                new NotificationDtos.CreateRequest(
                        "ACTIVITY_PUBLISHED",
                        "Nova atividade: " + activity.getTitle(),
                        "Uma nova atividade foi publicada. Prazo para envio: " + deadline + ".",
                        "/activities/" + activity.getId(),
                        "ACTIVITY",
                        activity.getId(),
                        null,
                        NotificationPriority.NORMAL,
                        activity.getAvailableAt(),
                        null,
                        new NotificationDtos.AudienceRequest(
                                NotificationAudienceType.COHORT,
                                cohortId
                        ),
                        CHANNELS
                ),
                activity.getCreatedByUser().getUlid()
        );
    }

    public void notifySubmitted(ActivitySubmission submission) {
        String instructorPersonId = submission.getActivity()
                .getLesson()
                .getInstructorPerson()
                .getId();
        User instructor = this.userRepository.findByPerson_IdAndStatus(
                instructorPersonId,
                UserAccountStatus.ACTIVE
        ).orElse(null);
        if (instructor == null) {
            return;
        }

        this.notificationService.create(
                new NotificationDtos.CreateRequest(
                        "ACTIVITY_SUBMITTED",
                        "Nova entrega: " + submission.getActivity().getTitle(),
                        submission.getLearner().getPerson().getFullName()
                                + " enviou uma atividade para avaliação.",
                        "/activity-submissions/" + submission.getId(),
                        "ACTIVITY_SUBMISSION",
                        submission.getId(),
                        null,
                        NotificationPriority.NORMAL,
                        null,
                        null,
                        new NotificationDtos.AudienceRequest(
                                NotificationAudienceType.USER,
                                instructor.getUlid()
                        ),
                        CHANNELS
                ),
                null
        );
    }
}
