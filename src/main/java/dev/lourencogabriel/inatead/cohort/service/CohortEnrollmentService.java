package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.cohort.dto.CohortEnrollmentDtos;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LifecycleAggregateType;
import dev.lourencogabriel.inatead.shared.model.LifecycleChangeReason;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.service.LifecycleStatusHistoryService;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;

@Service
public class CohortEnrollmentService extends AbstractCrudService<
        CohortEnrollment,
        CohortEnrollmentDtos.Request,
        CohortEnrollmentDtos.Response
        > {

    private static final LocalDate OPEN_ENDED_RANGE_END = LocalDate.of(9999, 12, 31);
    private static final EnumSet<EnrollmentStatus> OCCUPYING_STATUSES =
            EnumSet.of(EnrollmentStatus.PENDING, EnrollmentStatus.ACTIVE);

    private final CohortEnrollmentRepository enrollmentRepository;
    private final ContractRepository contractRepository;
    private final CohortRepository cohortRepository;
    private final LessonParticipantRepository participantRepository;
    private final CohortEnrollmentLifecycleService lifecycleService;
    private final LifecycleStatusHistoryService historyService;
    private final Clock clock;

    public CohortEnrollmentService(
            CohortEnrollmentRepository enrollmentRepository,
            ContractRepository contractRepository,
            CohortRepository cohortRepository,
            LessonParticipantRepository participantRepository,
            CohortEnrollmentLifecycleService lifecycleService,
            LifecycleStatusHistoryService historyService,
            Clock clock
    ) {
        super(enrollmentRepository, "Cohort enrollment");
        this.enrollmentRepository = enrollmentRepository;
        this.contractRepository = contractRepository;
        this.cohortRepository = cohortRepository;
        this.participantRepository = participantRepository;
        this.lifecycleService = lifecycleService;
        this.historyService = historyService;
        this.clock = clock;
    }

    @Override
    protected CohortEnrollment newEntity() {
        return new CohortEnrollment();
    }

    @Override
    protected void apply(CohortEnrollment entity, CohortEnrollmentDtos.Request request) {
        entity.setContract(requireContract(request.contractId()));
        entity.setCohort(requireCohort(request.cohortId()));
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(request.status());
    }

    @Override
    @Transactional
    public CohortEnrollmentDtos.Response create(CohortEnrollmentDtos.Request request) {
        return create(request, null);
    }

    @Transactional
    public CohortEnrollmentDtos.Response create(
            CohortEnrollmentDtos.Request request,
            String actorUserId
    ) {
        if (request.status() != EnrollmentStatus.PENDING) {
            throw new BusinessRuleException("An enrollment must be created with PENDING status");
        }

        CohortEnrollment enrollment = newEntity();
        apply(enrollment, request);
        validate(enrollment, true);
        CohortEnrollment saved = enrollmentRepository.save(enrollment);
        historyService.append(
                LifecycleAggregateType.COHORT_ENROLLMENT,
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
    public CohortEnrollmentDtos.Response update(
            String id,
            CohortEnrollmentDtos.Request request
    ) {
        return update(id, request, null);
    }

    @Transactional
    public CohortEnrollmentDtos.Response update(
            String id,
            CohortEnrollmentDtos.Request request,
            String actorUserId
    ) {
        requireContract(request.contractId());
        requireCohort(request.cohortId());
        CohortEnrollment enrollment = enrollmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cohort enrollment", id));
        EnrollmentStatus previousStatus = enrollment.getStatus();
        validateImmutableIdentity(enrollment, request);
        validateTerminalEnrollmentIsUnchanged(enrollment, request);
        validateReferencedPeriodChange(enrollment, request);

        enrollment.setStartDate(request.startDate());
        enrollment.setEndDate(request.endDate());
        enrollment.setStatus(request.status());
        validateStatusTransition(previousStatus, enrollment, request.statusReason());
        validate(enrollment, false);

        if (request.status() == EnrollmentStatus.CANCELLED
                && previousStatus != EnrollmentStatus.CANCELLED) {
            enrollment.setStatus(previousStatus);
            lifecycleService.cancelDirectly(
                    enrollment,
                    LocalDate.now(clock),
                    clock.instant(),
                    actorUserId,
                    request.statusReason()
            );
            return toResponse(enrollment);
        }

        CohortEnrollment saved = enrollmentRepository.save(enrollment);
        if (previousStatus != saved.getStatus()) {
            historyService.append(
                    LifecycleAggregateType.COHORT_ENROLLMENT,
                    saved.getId(),
                    previousStatus,
                    saved.getStatus(),
                    LifecycleChangeReason.ADMIN_TRANSITION,
                    request.statusReason(),
                    actorUserId,
                    clock.instant()
            );
        }
        return toResponse(saved);
    }

    @Override
    protected void validate(CohortEnrollment entity, boolean creating) {
        Contract contract = entity.getContract();
        Cohort cohort = entity.getCohort();
        LocalDate today = LocalDate.now(clock);

        if (entity.getEndDate() != null && entity.getEndDate().isBefore(entity.getStartDate())) {
            throw new BusinessRuleException("Enrollment end date cannot precede start date");
        }
        if (entity.getStartDate().isBefore(contract.getStartDate())) {
            throw new BusinessRuleException("Enrollment cannot begin before the contract");
        }
        if (contract.getEndDate() != null
                && (entity.getEndDate() == null || entity.getEndDate().isAfter(contract.getEndDate()))) {
            throw new BusinessRuleException("Enrollment cannot outlive the contract");
        }
        if (entity.getStartDate().isBefore(cohort.getStartDate())) {
            throw new BusinessRuleException("Enrollment cannot begin before the cohort");
        }
        if (cohort.getEndDate() != null
                && (entity.getEndDate() == null || entity.getEndDate().isAfter(cohort.getEndDate()))) {
            throw new BusinessRuleException("Enrollment cannot outlive the cohort");
        }

        if (entity.getStatus() == EnrollmentStatus.PENDING
                && (isTerminal(contract.getStatus()) || isTerminal(cohort.getStatus()))) {
            throw new BusinessRuleException(
                    "A pending enrollment requires a non-terminal contract and cohort"
            );
        }
        if (entity.getStatus() == EnrollmentStatus.ACTIVE) {
            validateActiveParents(entity, today);
        }
        if (entity.getStatus() == EnrollmentStatus.COMPLETED
                && (entity.getEndDate() == null || !entity.getEndDate().isBefore(today))) {
            throw new BusinessRuleException(
                    "COMPLETED status requires an enrollment end date before today"
            );
        }

        if (OCCUPYING_STATUSES.contains(entity.getStatus())) {
            if (enrollmentRepository.existsOverlappingEnrollment(
                    contract.getId(),
                    OCCUPYING_STATUSES,
                    creating ? null : entity.getId(),
                    entity.getStartDate(),
                    entity.getEndDate() == null ? OPEN_ENDED_RANGE_END : entity.getEndDate()
            )) {
                throw new BusinessRuleException(
                        "The contract already has a pending or active cohort enrollment in this period"
                );
            }
            validateCapacity(entity, creating);
        }
    }

    @Override
    @Transactional
    public void delete(String id) {
        CohortEnrollment enrollment = enrollmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cohort enrollment", id));
        validateDelete(enrollment);
        enrollmentRepository.delete(enrollment);
    }

    @Override
    protected void validateDelete(CohortEnrollment enrollment) {
        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            throw new BusinessRuleException("Only a pending enrollment can be deleted");
        }
        if (participantRepository.existsBySourceEnrollment_Id(enrollment.getId())) {
            throw new BusinessRuleException("An enrollment referenced by lessons cannot be deleted");
        }
    }

    @Override
    protected CohortEnrollmentDtos.Response toResponse(CohortEnrollment entity) {
        return new CohortEnrollmentDtos.Response(
                entity.getId(),
                entity.getContract().getId(),
                entity.getCohort().getId(),
                entity.getContract().getLearner().getId(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                historyService.find(LifecycleAggregateType.COHORT_ENROLLMENT, entity.getId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Contract requireContract(String id) {
        return contractRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract", id));
    }

    private Cohort requireCohort(String id) {
        return cohortRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cohort", id));
    }

    private void validateImmutableIdentity(
            CohortEnrollment enrollment,
            CohortEnrollmentDtos.Request request
    ) {
        if (!enrollment.getContract().getId().equals(request.contractId())) {
            throw new BusinessRuleException("contractId cannot be changed");
        }
        if (!enrollment.getCohort().getId().equals(request.cohortId())) {
            throw new BusinessRuleException("cohortId cannot be changed");
        }
    }

    private void validateReferencedPeriodChange(
            CohortEnrollment enrollment,
            CohortEnrollmentDtos.Request request
    ) {
        boolean periodChanged = !enrollment.getStartDate().equals(request.startDate())
                || !java.util.Objects.equals(enrollment.getEndDate(), request.endDate());
        if (periodChanged && participantRepository.existsBySourceEnrollment_Id(enrollment.getId())) {
            throw new BusinessRuleException(
                    "Enrollment dates cannot be changed after it has been used by a lesson"
            );
        }
    }

    private void validateTerminalEnrollmentIsUnchanged(
            CohortEnrollment enrollment,
            CohortEnrollmentDtos.Request request
    ) {
        if (enrollment.getStatus() != EnrollmentStatus.COMPLETED
                && enrollment.getStatus() != EnrollmentStatus.CANCELLED) {
            return;
        }
        if (enrollment.getStatus() != request.status()
                || !enrollment.getStartDate().equals(request.startDate())
                || !java.util.Objects.equals(enrollment.getEndDate(), request.endDate())) {
            throw new BusinessRuleException("Completed or cancelled enrollments cannot be changed");
        }
    }

    private void validateStatusTransition(
            EnrollmentStatus previous,
            CohortEnrollment enrollment,
            String reason
    ) {
        EnrollmentStatus requested = enrollment.getStatus();
        boolean valid = switch (previous) {
            case PENDING -> requested == EnrollmentStatus.PENDING
                    || requested == EnrollmentStatus.ACTIVE
                    || requested == EnrollmentStatus.CANCELLED;
            case ACTIVE -> requested == EnrollmentStatus.ACTIVE
                    || requested == EnrollmentStatus.COMPLETED
                    || requested == EnrollmentStatus.CANCELLED;
            case COMPLETED -> requested == EnrollmentStatus.COMPLETED;
            case CANCELLED -> requested == EnrollmentStatus.CANCELLED;
        };
        if (!valid) {
            throw new BusinessRuleException(
                    "Invalid enrollment status transition from " + previous + " to " + requested
            );
        }
        if (requested == EnrollmentStatus.CANCELLED
                && previous != EnrollmentStatus.CANCELLED
                && (reason == null || reason.isBlank())) {
            throw new BusinessRuleException("Cancelling an enrollment requires a reason");
        }
    }

    private void validateActiveParents(CohortEnrollment enrollment, LocalDate today) {
        Contract contract = enrollment.getContract();
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessRuleException("An active enrollment requires an active contract");
        }
        if (enrollment.getCohort().getStatus() != CohortStatus.ACTIVE) {
            throw new BusinessRuleException("An active enrollment requires an active cohort");
        }
        if (contract.getLearner().getStatus() != RecordStatus.ACTIVE
                || contract.getLearner().getPerson().getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("An active enrollment requires an active learner and person");
        }
        if (today.isBefore(enrollment.getStartDate())
                || enrollment.getEndDate() != null && today.isAfter(enrollment.getEndDate())) {
            throw new BusinessRuleException(
                    "ACTIVE status requires today to be inside the enrollment period"
            );
        }
    }

    private void validateCapacity(CohortEnrollment enrollment, boolean creating) {
        Integer capacity = enrollment.getCohort().getMaxLearners();
        if (capacity == null) {
            return;
        }
        long occupied = creating
                ? enrollmentRepository.countByCohort_IdAndStatusIn(
                        enrollment.getCohort().getId(),
                        OCCUPYING_STATUSES
                )
                : enrollmentRepository.countByCohort_IdAndStatusInAndIdNot(
                        enrollment.getCohort().getId(),
                        OCCUPYING_STATUSES,
                        enrollment.getId()
                );
        if (occupied >= capacity) {
            throw new BusinessRuleException("The cohort has reached its learner capacity");
        }
    }

    private boolean isTerminal(ContractStatus status) {
        return status == ContractStatus.ENDED || status == ContractStatus.CANCELLED;
    }

    private boolean isTerminal(CohortStatus status) {
        return status == CohortStatus.COMPLETED || status == CohortStatus.CANCELLED;
    }
}
