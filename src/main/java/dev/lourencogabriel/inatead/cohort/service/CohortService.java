package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.cohort.dto.CohortDtos;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.notification.repository.NotificationRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.LifecycleAggregateType;
import dev.lourencogabriel.inatead.shared.model.LifecycleChangeReason;
import dev.lourencogabriel.inatead.shared.service.LifecycleStatusHistoryService;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class CohortService extends AbstractCrudService<Cohort, CohortDtos.Request, CohortDtos.Response> {

    private static final Set<EnrollmentStatus> OCCUPYING_ENROLLMENT_STATUSES = Set.of(
            EnrollmentStatus.PENDING,
            EnrollmentStatus.ACTIVE
    );
    private static final Set<LessonStatus> OPEN_LESSON_STATUSES = Set.of(
            LessonStatus.SCHEDULED,
            LessonStatus.IN_PROGRESS
    );
    private static final Set<ActivityStatus> OPEN_ACTIVITY_STATUSES = Set.of(
            ActivityStatus.DRAFT,
            ActivityStatus.PUBLISHED
    );
    private static final String ACTIVITY_DUE_SOON = "ACTIVITY_DUE_SOON";
    private static final String ACTIVITY_CONTEXT = "ACTIVITY";

    private final CohortRepository cohortRepository;
    private final CohortEnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final ActivityRepository activityRepository;
    private final NotificationRepository notificationRepository;
    private final CohortEnrollmentLifecycleService enrollmentLifecycleService;
    private final LifecycleStatusHistoryService historyService;
    private final Clock clock;

    public CohortService(
            CohortRepository cohortRepository,
            CohortEnrollmentRepository enrollmentRepository,
            LessonRepository lessonRepository,
            ActivityRepository activityRepository,
            NotificationRepository notificationRepository,
            CohortEnrollmentLifecycleService enrollmentLifecycleService,
            LifecycleStatusHistoryService historyService,
            Clock clock
    ) {
        super(cohortRepository, "Cohort");
        this.cohortRepository = cohortRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.lessonRepository = lessonRepository;
        this.activityRepository = activityRepository;
        this.notificationRepository = notificationRepository;
        this.enrollmentLifecycleService = enrollmentLifecycleService;
        this.historyService = historyService;
        this.clock = clock;
    }

    @Override
    protected Cohort newEntity() {
        return new Cohort();
    }

    @Override
    protected void apply(Cohort entity, CohortDtos.Request request) {
        entity.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        entity.setName(request.name().trim());
        entity.setDefaultWeekday(request.defaultWeekday());
        entity.setShiftCode(request.shiftCode().trim().toUpperCase(Locale.ROOT));
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setMaxLearners(request.maxLearners());
        entity.setStatus(request.status());
    }

    @Override
    @Transactional
    public CohortDtos.Response create(CohortDtos.Request request) {
        return create(request, null);
    }

    @Transactional
    public CohortDtos.Response create(CohortDtos.Request request, String actorUserId) {
        if (request.status() != CohortStatus.PLANNED) {
            throw new BusinessRuleException("A cohort must be created with PLANNED status");
        }
        Cohort cohort = newEntity();
        apply(cohort, request);
        validate(cohort, true);
        Cohort saved = cohortRepository.save(cohort);
        historyService.append(
                LifecycleAggregateType.COHORT,
                saved.getId(),
                null,
                saved.getStatus(),
                LifecycleChangeReason.CREATED,
                request.statusReason(),
                actorUserId,
                clock.instant()
        );
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CohortDtos.Response update(String id, CohortDtos.Request request) {
        return update(id, request, null);
    }

    @Transactional
    public CohortDtos.Response update(
            String id,
            CohortDtos.Request request,
            String actorUserId
    ) {
        Cohort cohort = cohortRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cohort", id));
        CohortStatus previousStatus = cohort.getStatus();
        LocalDate previousStart = cohort.getStartDate();
        LocalDate previousEnd = cohort.getEndDate();

        if (previousStatus != CohortStatus.PLANNED
                && !cohort.getCode().equals(request.code().trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessRuleException("Cohort code can only be changed while the cohort is planned");
        }
        validateTerminalCohortIsUnchanged(cohort, request);
        apply(cohort, request);
        validateStatusTransition(previousStatus, cohort.getStatus(), request.statusReason());
        validate(cohort, false);
        validateChildrenStillFit(cohort, previousStart, previousEnd);
        validateCapacity(cohort);

        Cohort saved = cohortRepository.save(cohort);
        Instant changedAt = clock.instant();
        if (previousStatus != saved.getStatus()) {
            historyService.append(
                    LifecycleAggregateType.COHORT,
                    saved.getId(),
                    previousStatus,
                    saved.getStatus(),
                    LifecycleChangeReason.ADMIN_TRANSITION,
                    request.statusReason(),
                    actorUserId,
                    changedAt
            );
        }

        if (saved.getStatus() == CohortStatus.CANCELLED
                && previousStatus != CohortStatus.CANCELLED) {
            cancelChildren(saved, actorUserId, request.statusReason(), changedAt);
        } else if (saved.getStatus() == CohortStatus.COMPLETED
                && previousStatus != CohortStatus.COMPLETED) {
            enrollmentLifecycleService.completeForCohort(
                    saved.getId(),
                    saved.getEndDate(),
                    changedAt,
                    actorUserId
            );
        }
        return toResponse(saved);
    }

    @Override
    protected void validate(Cohort cohort, boolean creating) {
        LocalDate today = LocalDate.now(clock);
        if (cohort.getEndDate() != null && cohort.getEndDate().isBefore(cohort.getStartDate())) {
            throw new BusinessRuleException("Cohort end date cannot precede start date");
        }
        boolean duplicate = creating
                ? cohortRepository.existsByCode(cohort.getCode())
                : cohortRepository.existsByCodeAndIdNot(cohort.getCode(), cohort.getId());
        if (duplicate) {
            throw new ResourceConflictException("A cohort with this code already exists");
        }
        if (cohort.getStatus() == CohortStatus.ACTIVE
                && (today.isBefore(cohort.getStartDate())
                || cohort.getEndDate() != null && today.isAfter(cohort.getEndDate()))) {
            throw new BusinessRuleException("ACTIVE status requires today to be inside the cohort period");
        }
        if (cohort.getStatus() == CohortStatus.COMPLETED
                && (cohort.getEndDate() == null || !cohort.getEndDate().isBefore(today))) {
            throw new BusinessRuleException("COMPLETED status requires a cohort end date before today");
        }
    }

    @Override
    @Transactional
    public void delete(String id) {
        Cohort cohort = cohortRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cohort", id));
        validateDelete(cohort);
        cohortRepository.delete(cohort);
    }

    @Override
    protected void validateDelete(Cohort cohort) {
        if (cohort.getStatus() != CohortStatus.PLANNED) {
            throw new BusinessRuleException("Only a planned cohort can be deleted");
        }
        if (lessonRepository.existsByCohort_Id(cohort.getId())) {
            throw new BusinessRuleException("A cohort with lessons cannot be deleted");
        }
        if (enrollmentRepository.existsByCohort_Id(cohort.getId())) {
            throw new BusinessRuleException("A cohort with enrollments cannot be deleted");
        }
    }

    @Override
    protected CohortDtos.Response toResponse(Cohort entity) {
        return new CohortDtos.Response(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDefaultWeekday(),
                entity.getShiftCode(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getMaxLearners(),
                entity.getStatus(),
                historyService.find(LifecycleAggregateType.COHORT, entity.getId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validateStatusTransition(
            CohortStatus previous,
            CohortStatus requested,
            String reason
    ) {
        boolean valid = switch (previous) {
            case PLANNED -> requested == CohortStatus.PLANNED
                    || requested == CohortStatus.ACTIVE
                    || requested == CohortStatus.CANCELLED;
            case ACTIVE -> requested == CohortStatus.ACTIVE
                    || requested == CohortStatus.COMPLETED
                    || requested == CohortStatus.CANCELLED;
            case COMPLETED -> requested == CohortStatus.COMPLETED;
            case CANCELLED -> requested == CohortStatus.CANCELLED;
        };
        if (!valid) {
            throw new BusinessRuleException(
                    "Invalid cohort status transition from " + previous + " to " + requested
            );
        }
        if (requested == CohortStatus.CANCELLED
                && previous != CohortStatus.CANCELLED
                && (reason == null || reason.isBlank())) {
            throw new BusinessRuleException("Cancelling a cohort requires a reason");
        }
    }

    private void validateTerminalCohortIsUnchanged(Cohort cohort, CohortDtos.Request request) {
        if (cohort.getStatus() != CohortStatus.COMPLETED
                && cohort.getStatus() != CohortStatus.CANCELLED) {
            return;
        }
        String requestedCode = request.code().trim().toUpperCase(Locale.ROOT);
        String requestedName = request.name().trim();
        String requestedShift = request.shiftCode().trim().toUpperCase(Locale.ROOT);
        if (cohort.getStatus() != request.status()
                || !cohort.getCode().equals(requestedCode)
                || !cohort.getName().equals(requestedName)
                || cohort.getDefaultWeekday() != request.defaultWeekday()
                || !cohort.getShiftCode().equals(requestedShift)
                || !cohort.getStartDate().equals(request.startDate())
                || !Objects.equals(cohort.getEndDate(), request.endDate())
                || !Objects.equals(cohort.getMaxLearners(), request.maxLearners())) {
            throw new BusinessRuleException("Completed or cancelled cohorts cannot be changed");
        }
    }

    private void validateChildrenStillFit(
            Cohort cohort,
            LocalDate previousStart,
            LocalDate previousEnd
    ) {
        if (cohort.getStatus() == CohortStatus.COMPLETED
                && lessonRepository.existsByCohort_IdAndStatusIn(
                        cohort.getId(),
                        OPEN_LESSON_STATUSES
                )) {
            throw new BusinessRuleException("A cohort with open lessons cannot be completed");
        }

        if (Objects.equals(previousStart, cohort.getStartDate())
                && Objects.equals(previousEnd, cohort.getEndDate())) {
            return;
        }

        ZoneId zone = clock.getZone();
        for (Lesson lesson : lessonRepository.findAllByCohort_Id(cohort.getId())) {
            LocalDate start = lesson.getStartsAt().atZone(zone).toLocalDate();
            LocalDate end = lesson.getEndsAt().atZone(zone).toLocalDate();
            if (start.isBefore(cohort.getStartDate())
                    || cohort.getEndDate() != null && end.isAfter(cohort.getEndDate())) {
                throw new BusinessRuleException("The new cohort period would exclude an existing lesson");
            }
        }
        enrollmentRepository.findAllByCohort_Id(cohort.getId()).forEach(enrollment -> {
            if (enrollment.getStartDate().isBefore(cohort.getStartDate())
                    || cohort.getEndDate() != null
                    && (enrollment.getEndDate() == null
                    || enrollment.getEndDate().isAfter(cohort.getEndDate()))) {
                throw new BusinessRuleException(
                        "The new cohort period would exclude an existing enrollment"
                );
            }
        });
    }

    private void validateCapacity(Cohort cohort) {
        if (cohort.getMaxLearners() == null) {
            return;
        }
        long occupied = enrollmentRepository.countByCohort_IdAndStatusIn(
                cohort.getId(),
                OCCUPYING_ENROLLMENT_STATUSES
        );
        if (occupied > cohort.getMaxLearners()) {
            throw new BusinessRuleException(
                    "Cohort capacity cannot be lower than its pending and active enrollments"
            );
        }
    }

    private void cancelChildren(
            Cohort cohort,
            String actorUserId,
            String detail,
            Instant changedAt
    ) {
        LocalDate today = LocalDate.now(clock);
        enrollmentLifecycleService.cancelForCohort(
                cohort.getId(),
                today,
                changedAt,
                actorUserId,
                "Cohort cancelled: " + detail.trim()
        );
        for (Lesson lesson : lessonRepository.findAllByCohort_Id(cohort.getId())) {
            if (OPEN_LESSON_STATUSES.contains(lesson.getStatus())) {
                lesson.setStatus(LessonStatus.CANCELLED);
                lessonRepository.save(lesson);
            }
            activityRepository.cancelOpenActivitiesByLessonId(
                    lesson.getId(),
                    OPEN_ACTIVITY_STATUSES,
                    ActivityStatus.CANCELLED,
                    changedAt
            );
            notificationRepository.expireActiveActivityNotificationsForLesson(
                    lesson.getId(),
                    ACTIVITY_DUE_SOON,
                    ACTIVITY_CONTEXT,
                    changedAt
            );
        }
    }
}
