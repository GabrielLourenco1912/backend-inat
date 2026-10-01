package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.project.ApprenticeshipPolicy;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.lesson.dto.LessonDtos;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonFileRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.service.PersonTypeRules;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
    private final ApprenticeshipPolicy apprenticeshipPolicy;
    private final LessonParticipantService participantService;
    private final LessonFileRepository fileRepository;

    public LessonService(
            LessonRepository lessonRepository,
            CohortRepository cohortRepository,
            PersonRepository personRepository,
            UserRepository userRepository,
            LessonParticipantRepository participantRepository,
            ActivityRepository activityRepository,
            NotificationRepository notificationRepository,
            Clock clock,
            ApprenticeshipPolicy apprenticeshipPolicy,
            LessonParticipantService participantService,
            LessonFileRepository fileRepository
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
        this.apprenticeshipPolicy = apprenticeshipPolicy;
        this.participantService = participantService;
        this.fileRepository = fileRepository;
    }

    @Override
    @Transactional
    public LessonDtos.Response create(LessonDtos.Request request) {
        LessonDtos.Response response = super.create(request);
        if (response.deliveryMode() == DeliveryMode.ONLINE) {
            participantService.generateRegularRoster(response.id());
        }
        return response;
    }

    @Override
    protected Lesson newEntity() {
        return new Lesson();
    }

    @Override
    protected void apply(Lesson entity, LessonDtos.Request request) {
        entity.setCohort(EntityLookup.require(cohortRepository, request.cohortId(), "Cohort"));
        entity.setInstructorPerson(personRepository.findByIdForUpdate(request.instructorPersonId())
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", request.instructorPersonId())));
        entity.setTitle(request.title().trim());
        entity.setDescription(request.description().trim());
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
        if (creating && entity.getStatus() != LessonStatus.SCHEDULED) {
            throw new BusinessRuleException("A lesson must be created with SCHEDULED status");
        }

        if (!entity.getEndsAt().isAfter(entity.getStartsAt())) {
            throw new BusinessRuleException("Lesson end time must be after its start time");
        }

        PersonTypeRules.requireType(entity.getInstructorPerson(), PersonTypeCode.INSTRUCTOR);
        if (!userRepository.existsActivePersonWithRole(
                entity.getInstructorPerson().getId(),
                UserAccountStatus.ACTIVE,
                "INSTRUCTOR"
        )) {
            throw new BusinessRuleException(
                    "instructorPersonId must belong to an active user with the INSTRUCTOR role"
            );
        }

        if ((creating || isOpen(entity.getStatus()))
                && entity.getCohort().getStatus() != CohortStatus.ACTIVE) {
            throw new BusinessRuleException("An open lesson requires an active cohort");
        }

        LocalDate lessonDate = lessonDate(entity);
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
        LessonStatus previousStatus = lesson.getStatus();
        String previousCohortId = lesson.getCohort().getId();
        LocalDate previousLessonDate = lessonDate(lesson);
        DeliveryMode previousDeliveryMode = lesson.getDeliveryMode();

        apply(lesson, request);
        validate(lesson, false);
        validateStatusTransition(previousStatus, lesson);

        boolean cohortChanged = !previousCohortId.equals(lesson.getCohort().getId());
        boolean lessonDateChanged = !previousLessonDate.equals(lessonDate(lesson));
        if (cohortChanged && lesson.getCohort().getStatus() != CohortStatus.ACTIVE) {
            throw new BusinessRuleException("A lesson can only be moved to an active cohort");
        }
        boolean deliveryModeChanged = previousDeliveryMode != lesson.getDeliveryMode();
        if (cohortChanged || lessonDateChanged
                || deliveryModeChanged && lesson.getStatus() != LessonStatus.CANCELLED) {
            validateExistingRosterContext(lesson);
        }

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

    @Override
    protected void validateDelete(Lesson lesson) {
        if (lesson.getStatus() != LessonStatus.SCHEDULED) {
            throw new BusinessRuleException("Only a scheduled lesson can be deleted");
        }
        if (participantRepository.existsByLesson_Id(lesson.getId())) {
            throw new BusinessRuleException("A lesson with participants cannot be deleted");
        }
        if (activityRepository.existsByLesson_Id(lesson.getId())) {
            throw new BusinessRuleException("A lesson with activities cannot be deleted");
        }
        if (fileRepository.existsByLesson_Id(lesson.getId())) {
            throw new BusinessRuleException("Remove lesson materials before deleting the lesson");
        }
    }

    private void validateDeliveryLocation(Lesson entity) {
        boolean hasRoom = entity.getRoom() != null;
        if (entity.getDeliveryMode() == DeliveryMode.ONSITE && !hasRoom) {
            throw new BusinessRuleException("Onsite lessons require a room");
        }
    }

    private void validateStatusTransition(LessonStatus previousStatus, Lesson lesson) {
        LessonStatus requestedStatus = lesson.getStatus();
        boolean validTransition = switch (previousStatus) {
            case SCHEDULED -> requestedStatus == LessonStatus.SCHEDULED
                    || requestedStatus == LessonStatus.IN_PROGRESS
                    || requestedStatus == LessonStatus.CANCELLED;
            case IN_PROGRESS -> requestedStatus == LessonStatus.IN_PROGRESS
                    || requestedStatus == LessonStatus.COMPLETED
                    || requestedStatus == LessonStatus.CANCELLED;
            case COMPLETED -> requestedStatus == LessonStatus.COMPLETED;
            case CANCELLED -> requestedStatus == LessonStatus.CANCELLED;
        };

        if (!validTransition) {
            throw new BusinessRuleException(
                    "Invalid lesson status transition from "
                            + previousStatus + " to " + requestedStatus
            );
        }

        Instant now = clock.instant();
        if (requestedStatus == LessonStatus.IN_PROGRESS
                && (lesson.getStartsAt().isAfter(now) || !lesson.getEndsAt().isAfter(now))) {
            throw new BusinessRuleException(
                    "IN_PROGRESS status requires the current time to be inside the lesson period"
            );
        }
        if (requestedStatus == LessonStatus.COMPLETED && lesson.getEndsAt().isAfter(now)) {
            throw new BusinessRuleException(
                    "COMPLETED status requires the lesson period to have ended"
            );
        }
    }

    private void validateExistingRosterContext(Lesson lesson) {
        LocalDate date = lessonDate(lesson);
        participantRepository.findAllByLesson_Id(lesson.getId()).stream()
                .filter(participant -> participant.getStatus() != ParticipationStatus.CANCELLED)
                .forEach(participant -> {
                    LessonParticipantRules.validateContext(participant, lesson, date);
                    apprenticeshipPolicy.validateParticipation(participant, lesson, date);
                });
    }

    private boolean isOpen(LessonStatus status) {
        return status == LessonStatus.SCHEDULED || status == LessonStatus.IN_PROGRESS;
    }

    private LocalDate lessonDate(Lesson lesson) {
        return BusinessTime.date(lesson.getStartsAt());
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
                entity.getDescription(),
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
