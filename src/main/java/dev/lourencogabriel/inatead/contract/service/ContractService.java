package dev.lourencogabriel.inatead.contract.service;

import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.project.ApprenticeshipPolicy;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.service.CohortEnrollmentLifecycleService;
import dev.lourencogabriel.inatead.contract.dto.ContractDtos;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.model.ContractSchoolChange;
import dev.lourencogabriel.inatead.contract.repository.ContractDocumentRepository;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.contract.repository.ContractSchoolChangeRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.LifecycleAggregateType;
import dev.lourencogabriel.inatead.shared.model.LifecycleChangeReason;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.service.LifecycleStatusHistoryService;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
public class ContractService extends AbstractCrudService<Contract, ContractDtos.Request, ContractDtos.Response> {

    private static final LocalDate OPEN_ENDED_RANGE_END = LocalDate.of(9999, 12, 31);
    private static final EnumSet<ContractStatus> OCCUPYING_STATUSES =
            EnumSet.of(ContractStatus.ACTIVE, ContractStatus.SUSPENDED);

    private final ContractRepository contractRepository;
    private final LearnerRepository learnerRepository;
    private final OrganizationRepository organizationRepository;
    private final CohortEnrollmentRepository enrollmentRepository;
    private final ContractDocumentRepository documentRepository;
    private final ContractSchoolChangeRepository schoolChangeRepository;
    private final CohortEnrollmentLifecycleService enrollmentLifecycleService;
    private final LifecycleStatusHistoryService historyService;
    private final Clock clock;
    private final ApprenticeshipPolicy apprenticeshipPolicy;

    public ContractService(
            ContractRepository contractRepository,
            LearnerRepository learnerRepository,
            OrganizationRepository organizationRepository,
            CohortEnrollmentRepository enrollmentRepository,
            ContractDocumentRepository documentRepository,
            ContractSchoolChangeRepository schoolChangeRepository,
            CohortEnrollmentLifecycleService enrollmentLifecycleService,
            LifecycleStatusHistoryService historyService,
            Clock clock,
            ApprenticeshipPolicy apprenticeshipPolicy
    ) {
        super(contractRepository, "Contract");
        this.contractRepository = contractRepository;
        this.learnerRepository = learnerRepository;
        this.organizationRepository = organizationRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.documentRepository = documentRepository;
        this.schoolChangeRepository = schoolChangeRepository;
        this.enrollmentLifecycleService = enrollmentLifecycleService;
        this.historyService = historyService;
        this.clock = clock;
        this.apprenticeshipPolicy = apprenticeshipPolicy;
    }

    @Override
    protected Contract newEntity() {
        return new Contract();
    }

    @Override
    protected void apply(Contract entity, ContractDtos.Request request) {
        entity.setLearner(requireLearnerForUpdate(request.learnerId()));
        entity.setEmployer(requireOrganization(request.employerId(), "Employer"));
        entity.setSchool(
                request.schoolId() == null || request.schoolId().isBlank()
                        ? null
                        : requireOrganization(request.schoolId(), "School")
        );
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setMonthlySalary(request.monthlySalary());
        entity.setWeeklyWorkloadMinutes(request.weeklyWorkloadMinutes());
        entity.setStatus(request.status());
    }

    @Override
    @Transactional
    public ContractDtos.Response create(ContractDtos.Request request) {
        return create(request, null);
    }

    @Transactional
    public ContractDtos.Response create(ContractDtos.Request request, String actorUserId) {
        if (request.status() != ContractStatus.DRAFT) {
            throw new BusinessRuleException("A contract must be created with DRAFT status");
        }
        Contract contract = newEntity();
        apply(contract, request);
        validate(contract, true);
        Contract saved = contractRepository.save(contract);
        historyService.append(
                LifecycleAggregateType.CONTRACT,
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
    public ContractDtos.Response update(String id, ContractDtos.Request request) {
        return update(id, request, null);
    }

    @Transactional
    public ContractDtos.Response update(
            String id,
            ContractDtos.Request request,
            String actorUserId
    ) {
        Learner requestedLearner = requireLearnerForUpdate(request.learnerId());
        Organization requestedEmployer = requireOrganization(request.employerId(), "Employer");
        Organization requestedSchool = request.schoolId() == null || request.schoolId().isBlank()
                ? null
                : requireOrganization(request.schoolId(), "School");
        Contract contract = contractRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract", id));
        ContractStatus previousStatus = contract.getStatus();
        LocalDate previousEndDate = contract.getEndDate();
        String previousSchoolId = organizationId(contract.getSchool());

        validateImmutableIdentity(contract, request);
        validateTerminalContractIsUnchanged(contract, request);
        if (previousStatus != ContractStatus.DRAFT
                && !contract.getStartDate().equals(request.startDate())) {
            throw new BusinessRuleException("Contract start date cannot be changed");
        }
        validateStatusTransition(previousStatus, request.status(), request.statusReason());
        validateEndDateChange(contract, previousEndDate, request);
        apprenticeshipPolicy.validateWorkloadChange(contract, request.weeklyWorkloadMinutes());

        if (previousStatus == ContractStatus.DRAFT) {
            contract.setLearner(requestedLearner);
            contract.setEmployer(requestedEmployer);
            contract.setStartDate(request.startDate());
        }
        contract.setSchool(requestedSchool);
        contract.setEndDate(effectiveEndDate(contract, previousStatus, request));
        contract.setMonthlySalary(request.monthlySalary());
        contract.setWeeklyWorkloadMinutes(request.weeklyWorkloadMinutes());
        contract.setStatus(request.status());
        validate(contract, false);
        validateEnrollmentsStillFit(contract, previousStatus);

        Contract saved = contractRepository.save(contract);
        Instant changedAt = clock.instant();
        String newSchoolId = organizationId(saved.getSchool());
        if (!Objects.equals(previousSchoolId, newSchoolId)) {
            appendSchoolChange(saved, previousSchoolId, newSchoolId, actorUserId, changedAt);
        }
        if (previousStatus != saved.getStatus()) {
            historyService.append(
                    LifecycleAggregateType.CONTRACT,
                    saved.getId(),
                    previousStatus,
                    saved.getStatus(),
                    LifecycleChangeReason.ADMIN_TRANSITION,
                    request.statusReason(),
                    actorUserId,
                    changedAt
            );
        }

        if (saved.getStatus() == ContractStatus.CANCELLED
                && previousStatus != ContractStatus.CANCELLED) {
            enrollmentLifecycleService.cancelForContract(
                    saved.getId(),
                    BusinessTime.today(clock),
                    changedAt,
                    actorUserId,
                    "Contract cancelled: " + request.statusReason().trim()
            );
        } else if (saved.getStatus() == ContractStatus.ENDED
                && previousStatus != ContractStatus.ENDED) {
            enrollmentLifecycleService.completeForContract(
                    saved.getId(),
                    saved.getEndDate(),
                    changedAt,
                    actorUserId
            );
        }
        return toResponse(saved);
    }

    @Override
    protected void validate(Contract contract, boolean creating) {
        if (!contract.getEmployer().hasType(OrganizationType.EMPLOYER)) {
            throw new BusinessRuleException("employerId must reference an EMPLOYER organization");
        }
        if (contract.getSchool() != null
                && !contract.getSchool().hasType(OrganizationType.SCHOOL)) {
            throw new BusinessRuleException("schoolId must reference a SCHOOL organization");
        }
        if (contract.getSchool() != null
                && contract.getSchool().getId().equals(contract.getEmployer().getId())) {
            throw new BusinessRuleException("Employer and school must be different organizations");
        }
        if (contract.getEndDate() != null
                && contract.getEndDate().isBefore(contract.getStartDate())) {
            throw new BusinessRuleException("Contract end date cannot precede start date");
        }
        if (contract.getStartDate().isBefore(contract.getLearner().getPerson().getBirthDate())) {
            throw new BusinessRuleException("Contract start date cannot precede the learner birth date");
        }

        LocalDate today = BusinessTime.today(clock);
        if (creating && contract.getEndDate() != null && !contract.getEndDate().isAfter(today)) {
            throw new BusinessRuleException("A new contract end date must be in the future");
        }
        if (contract.getStatus() == ContractStatus.ENDED
                && (contract.getEndDate() == null || !contract.getEndDate().isBefore(today))) {
            throw new BusinessRuleException("ENDED status requires a contract end date before today");
        }
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            validateActiveContext(contract, today);
        }
        if (contract.getStatus() == ContractStatus.SUSPENDED) {
            validateRelatedRecordsAreActive(contract);
            if (today.isBefore(contract.getStartDate())
                    || contract.getEndDate() != null && today.isAfter(contract.getEndDate())) {
                throw new BusinessRuleException(
                        "SUSPENDED status requires today to be inside the contract period"
                );
            }
        }

        validateSchoolRequirement(contract.getLearner(), contract.getStartDate(), contract.getSchool());

        if (OCCUPYING_STATUSES.contains(contract.getStatus())
                && contractRepository.existsOverlappingContract(
                        contract.getLearner().getId(),
                        OCCUPYING_STATUSES,
                        creating ? null : contract.getId(),
                        contract.getStartDate(),
                        contract.getEndDate() == null ? OPEN_ENDED_RANGE_END : contract.getEndDate()
                )) {
            throw new BusinessRuleException(
                    "The learner already has an active or suspended contract in this period"
            );
        }
    }

    @Override
    @Transactional
    public void delete(String id) {
        Contract contract = contractRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract", id));
        validateDelete(contract);
        schoolChangeRepository.deleteAllByContract_Id(contract.getId());
        contractRepository.delete(contract);
    }

    @Override
    protected void validateDelete(Contract contract) {
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft contract can be deleted");
        }
        if (enrollmentRepository.existsByContract_Id(contract.getId())) {
            throw new BusinessRuleException("A contract with cohort enrollments cannot be deleted");
        }
        if (documentRepository.existsByContract_Id(contract.getId())) {
            throw new BusinessRuleException("A contract with documents cannot be deleted");
        }
    }

    @Transactional(readOnly = true)
    public List<ContractDtos.Response> findByLearner(String learnerId) {
        if (!learnerRepository.existsById(learnerId)) {
            throw new ResourceNotFoundException("Learner", learnerId);
        }
        return contractRepository.findAllByLearner_IdOrderByStartDateDesc(learnerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContractDtos.Response> findByOrganization(String organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", organizationId);
        }
        return contractRepository.findAllByOrganization(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    protected ContractDtos.Response toResponse(Contract entity) {
        return new ContractDtos.Response(
                entity.getId(),
                entity.getLearner().getId(),
                entity.getEmployer().getId(),
                entity.getSchool() == null ? null : entity.getSchool().getId(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getMonthlySalary(),
                entity.getWeeklyWorkloadMinutes(),
                entity.getStatus(),
                historyService.find(LifecycleAggregateType.CONTRACT, entity.getId()),
                schoolChangeRepository
                        .findAllByContract_IdOrderByChangedAtAscIdAsc(entity.getId())
                        .stream()
                        .map(change -> new ContractDtos.SchoolChangeResponse(
                                change.getId(),
                                change.getPreviousSchoolId(),
                                change.getNewSchoolId(),
                                change.getChangedByUserId(),
                                change.getChangedAt()
                        ))
                        .toList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validateImmutableIdentity(Contract contract, ContractDtos.Request request) {
        if (contract.getStatus() == ContractStatus.DRAFT) {
            return;
        }
        if (!contract.getLearner().getId().equals(request.learnerId())) {
            throw new BusinessRuleException("learnerId cannot be changed");
        }
        if (!contract.getEmployer().getId().equals(request.employerId())) {
            throw new BusinessRuleException("employerId cannot be changed");
        }
    }

    private void validateTerminalContractIsUnchanged(
            Contract contract,
            ContractDtos.Request request
    ) {
        if (contract.getStatus() != ContractStatus.ENDED
                && contract.getStatus() != ContractStatus.CANCELLED) {
            return;
        }
        if (contract.getStatus() != request.status()
                || !Objects.equals(organizationId(contract.getSchool()), normalizeId(request.schoolId()))
                || !Objects.equals(contract.getEndDate(), request.endDate())
                || contract.getMonthlySalary().compareTo(request.monthlySalary()) != 0
                || contract.getWeeklyWorkloadMinutes() != request.weeklyWorkloadMinutes()) {
            throw new BusinessRuleException("Ended or cancelled contracts cannot be changed");
        }
    }

    private void appendSchoolChange(
            Contract contract,
            String previousSchoolId,
            String newSchoolId,
            String actorUserId,
            Instant changedAt
    ) {
        ContractSchoolChange change = new ContractSchoolChange();
        change.setContract(contract);
        change.setPreviousSchoolId(previousSchoolId);
        change.setNewSchoolId(newSchoolId);
        change.setChangedByUserId(actorUserId);
        change.setChangedAt(changedAt);
        schoolChangeRepository.save(change);
    }

    private String organizationId(Organization organization) {
        return organization == null ? null : organization.getId();
    }

    private String normalizeId(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void validateEndDateChange(
            Contract contract,
            LocalDate previousEndDate,
            ContractDtos.Request request
    ) {
        LocalDate requestedEndDate = request.endDate();
        if (Objects.equals(previousEndDate, requestedEndDate)) {
            return;
        }

        LocalDate today = BusinessTime.today(clock);
        boolean cancellingToday = request.status() == ContractStatus.CANCELLED
                && !contract.getStartDate().isAfter(today)
                && today.equals(requestedEndDate);
        if (cancellingToday) {
            return;
        }
        if (requestedEndDate != null && !requestedEndDate.isAfter(today)) {
            throw new BusinessRuleException(
                    "Contract end date can only be changed to a future date; use cancellation to end it today"
            );
        }
        if (previousEndDate != null
                && requestedEndDate != null
                && !requestedEndDate.isAfter(previousEndDate)) {
            throw new BusinessRuleException("Contract end date can only be extended");
        }
    }

    private LocalDate effectiveEndDate(
            Contract contract,
            ContractStatus previousStatus,
            ContractDtos.Request request
    ) {
        LocalDate today = BusinessTime.today(clock);
        if (request.status() == ContractStatus.CANCELLED
                && previousStatus != ContractStatus.CANCELLED
                && !contract.getStartDate().isAfter(today)) {
            return today;
        }
        return request.endDate();
    }

    private void validateStatusTransition(
            ContractStatus previous,
            ContractStatus requested,
            String reason
    ) {
        boolean valid = switch (previous) {
            case DRAFT -> requested == ContractStatus.DRAFT
                    || requested == ContractStatus.ACTIVE
                    || requested == ContractStatus.CANCELLED;
            case ACTIVE -> requested == ContractStatus.ACTIVE
                    || requested == ContractStatus.SUSPENDED
                    || requested == ContractStatus.ENDED
                    || requested == ContractStatus.CANCELLED;
            case SUSPENDED -> requested == ContractStatus.SUSPENDED
                    || requested == ContractStatus.ACTIVE
                    || requested == ContractStatus.ENDED
                    || requested == ContractStatus.CANCELLED;
            case ENDED -> requested == ContractStatus.ENDED;
            case CANCELLED -> requested == ContractStatus.CANCELLED;
        };
        if (!valid) {
            throw new BusinessRuleException(
                    "Invalid contract status transition from " + previous + " to " + requested
            );
        }
        boolean reasonRequired = previous != requested
                && (requested == ContractStatus.SUSPENDED
                || requested == ContractStatus.CANCELLED);
        if (reasonRequired && (reason == null || reason.isBlank())) {
            throw new BusinessRuleException(
                    requested == ContractStatus.CANCELLED
                            ? "Cancelling a contract requires a reason"
                            : "Suspending a contract requires a reason"
            );
        }
    }

    private void validateEnrollmentsStillFit(Contract contract, ContractStatus previousStatus) {
        if (contract.getEndDate() == null
                || contract.getStatus() == ContractStatus.CANCELLED
                && previousStatus != ContractStatus.CANCELLED) {
            return;
        }
        boolean excludesEnrollment = enrollmentRepository.findAllByContract_Id(contract.getId())
                .stream()
                .anyMatch(enrollment -> enrollment.getEndDate() == null
                        || enrollment.getEndDate().isAfter(contract.getEndDate()));
        if (excludesEnrollment) {
            throw new BusinessRuleException(
                    "The new contract end date would exclude an existing cohort enrollment"
            );
        }
    }

    private void validateActiveContext(Contract contract, LocalDate today) {
        validateRelatedRecordsAreActive(contract);
        if (today.isBefore(contract.getStartDate())
                || contract.getEndDate() != null && today.isAfter(contract.getEndDate())) {
            throw new BusinessRuleException("ACTIVE status requires today to be inside the contract period");
        }
    }

    private void validateRelatedRecordsAreActive(Contract contract) {
        if (contract.getLearner().getStatus() != RecordStatus.ACTIVE
                || contract.getLearner().getPerson().getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("An active contract requires an active learner and person");
        }
        if (contract.getEmployer().getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("An active contract requires an active employer");
        }
        if (contract.getSchool() != null && contract.getSchool().getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("An active contract requires an active school");
        }
    }

    private void validateSchoolRequirement(
            Learner learner,
            LocalDate contractStart,
            Organization school
    ) {
        int ageAtStart = Period.between(learner.getPerson().getBirthDate(), contractStart).getYears();
        if (ageAtStart < 18 && !learner.isHasCompletedHighSchool() && school == null) {
            throw new BusinessRuleException(
                    "A minor learner who has not completed high school requires a school"
            );
        }
    }

    private Learner requireLearnerForUpdate(String id) {
        return learnerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Learner", id));
    }

    private Organization requireOrganization(String id, String name) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(name, id));
    }
}
