package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.lesson.dto.LessonDtos;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
public class LessonService extends AbstractCrudService<Lesson, LessonDtos.Request, LessonDtos.Response> {

    private static final Set<ActivityStatus> OPEN_ACTIVITY_STATUSES = Set.of(
            ActivityStatus.DRAFT,
            ActivityStatus.PUBLISHED
    );
    private static final String ACTIVITY_DUE_SOON = "ACTIVITY_DUE_SOON";
    private static final String ACTIVITY_CONTEXT = "ACTIVITY";

    private final LessonRepository lessonRepository;
    private final CohortRepository cohortRepository;
    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final LessonParticipantRepository participantRepository;
    private final ActivityRepository activityRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public LessonService(
            LessonRepository lessonRepository,
            CohortRepository cohortRepository,
            PersonRepository personRepository,
            UserRepository userRepository,
            LessonParticipantRepository participantRepository,
            ActivityRepository activityRepository,
            NotificationRepository notificationRepository,
            Clock clock
    ) {
        super(lessonRepository, "Lesson");
        this.lessonRepository = lessonRepository;
        this.cohortRepository = cohortRepository;
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
        this.activityRepository = activityRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Override
    protected Lesson newEntity() {
        return new Lesson();
    }

    @Override
    protected void apply(Lesson entity, LessonDtos.Request request) {
        entity.setCohort(EntityLookup.require(cohortRepository, request.cohortId(), "Cohort"));
        entity.setInstructorPerson(EntityLookup.require(
                personRepository,
                request.instructorPersonId(),
                "Instructor"
        ));
        entity.setTitle(request.title().trim());
        entity.setStartsAt(request.startsAt());
        entity.setEndsAt(request.endsAt());
        entity.setDeliveryMode(request.deliveryMode());
        entity.setRoom(normalize(request.room()));
        entity.setMeetingUrl(normalize(request.meetingUrl()));
        entity.setExternalLessonUrl(normalize(request.externalLessonUrl()));
        entity.setStatus(request.status());
    }

    @Override
    protected void validate(Lesson entity, boolean creating) {
        if (!entity.getEndsAt().isAfter(entity.getStartsAt())) {
            throw new BusinessRuleException("Lesson end time must be after its start time");
        }

        if (!userRepository.existsActivePersonWithRole(
                entity.getInstructorPerson().getId(),
                UserAccountStatus.ACTIVE,
                "INSTRUCTOR"
        )) {
            throw new BusinessRuleException(
                    "instructorPersonId must belong to an active user with the INSTRUCTOR role"
            );
        }

        LocalDate lessonDate = entity.getStartsAt().atZone(ZoneOffset.UTC).toLocalDate();
        if (lessonDate.isBefore(entity.getCohort().getStartDate())
                || entity.getCohort().getEndDate() != null
                && lessonDate.isAfter(entity.getCohort().getEndDate())) {
            throw new BusinessRuleException("Lesson date must be inside the cohort period");
        }

        validateDeliveryLocation(entity);

        boolean duplicateStart = creating
                ? lessonRepository.existsByCohort_IdAndStartsAt(
                        entity.getCohort().getId(),
                        entity.getStartsAt()
                )
                : lessonRepository.existsByCohort_IdAndStartsAtAndIdNot(
                        entity.getCohort().getId(),
                        entity.getStartsAt(),
                        entity.getId()
                );
        if (duplicateStart) {
            throw new ResourceConflictException("This cohort already has a lesson at the same start time");
        }

        if (entity.getStatus() != LessonStatus.CANCELLED) {
            String currentId = creating ? null : entity.getId();
            if (lessonRepository.existsCohortScheduleConflict(
                    entity.getCohort().getId(),
                    currentId,
                    entity.getStartsAt(),
                    entity.getEndsAt(),
                    LessonStatus.CANCELLED
            )) {
                throw new BusinessRuleException("The cohort already has an overlapping lesson");
            }
            if (lessonRepository.existsInstructorScheduleConflict(
                    entity.getInstructorPerson().getId(),
                    currentId,
                    entity.getStartsAt(),
                    entity.getEndsAt(),
                    LessonStatus.CANCELLED
            )) {
                throw new BusinessRuleException("The instructor already has an overlapping lesson");
            }
            if (!creating && participantRepository.existsScheduleConflictForLessonParticipants(
                    entity.getId(),
                    entity.getStartsAt(),
                    entity.getEndsAt(),
                    ParticipationStatus.CANCELLED,
                    LessonStatus.CANCELLED
            )) {
                throw new BusinessRuleException(
                        "At least one learner already has an overlapping lesson"
                );
            }
        }
    }

    @Override
    @Transactional
    public LessonDtos.Response update(String id, LessonDtos.Request request) {
        Lesson lesson = requireEntity(id);
        apply(lesson, request);
        validate(lesson, false);
        Lesson saved = lessonRepository.save(lesson);

        if (saved.getStatus() == LessonStatus.CANCELLED) {
            Instant now = clock.instant();
            activityRepository.cancelOpenActivitiesByLessonId(
                    saved.getId(),
                    OPEN_ACTIVITY_STATUSES,
                    ActivityStatus.CANCELLED,
                    now
            );
            notificationRepository.expireActiveActivityNotificationsForLesson(
                    saved.getId(),
                    ACTIVITY_DUE_SOON,
                    ACTIVITY_CONTEXT,
                    now
            );
        }

        return toResponse(saved);
    }

    private void validateDeliveryLocation(Lesson entity) {
        boolean hasRoom = entity.getRoom() != null;
        boolean hasMeetingUrl = entity.getMeetingUrl() != null;
        if (entity.getDeliveryMode() == DeliveryMode.ONSITE && !hasRoom) {
            throw new BusinessRuleException("Onsite lessons require a room");
        }
        if (entity.getDeliveryMode() == DeliveryMode.ONLINE && !hasMeetingUrl) {
            throw new BusinessRuleException("Online lessons require a meeting URL");
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<LessonDtos.Response> findMine(String userId) {
        var user = userRepository.findWithRolesByUlid(userId)
                .orElseThrow(() -> new dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException(
                        "User",
                        userId
                ));
        if (user.getPerson() == null) {
            return List.of();
        }
        String personId = user.getPerson().getId();
        LinkedHashMap<String, Lesson> lessons = new LinkedHashMap<>();
        lessonRepository.findAllByInstructorPerson_IdOrderByStartsAtDesc(personId)
                .forEach(lesson -> lessons.put(lesson.getId(), lesson));
        lessonRepository.findAllForLearnerPerson(personId)
                .forEach(lesson -> lessons.put(lesson.getId(), lesson));
        return lessons.values().stream()
                .sorted(java.util.Comparator.comparing(Lesson::getStartsAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Override
    protected LessonDtos.Response toResponse(Lesson entity) {
        return new LessonDtos.Response(
                entity.getId(),
                entity.getCohort().getId(),
                entity.getInstructorPerson().getId(),
                entity.getTitle(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getDeliveryMode(),
                entity.getRoom(),
                entity.getMeetingUrl(),
                entity.getExternalLessonUrl(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
