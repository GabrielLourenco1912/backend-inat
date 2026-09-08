package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.cohort.dto.CohortEnrollmentDtos;
import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

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

    public CohortEnrollmentService(
            CohortEnrollmentRepository enrollmentRepository,
            ContractRepository contractRepository,
            CohortRepository cohortRepository
    ) {
        super(enrollmentRepository, "Cohort enrollment");
        this.enrollmentRepository = enrollmentRepository;
        this.contractRepository = contractRepository;
        this.cohortRepository = cohortRepository;
    }

    @Override
    protected CohortEnrollment newEntity() {
        return new CohortEnrollment();
    }

    @Override
    protected void apply(CohortEnrollment entity, CohortEnrollmentDtos.Request request) {
        entity.setContract(EntityLookup.require(contractRepository, request.contractId(), "Contract"));
        entity.setCohort(EntityLookup.require(cohortRepository, request.cohortId(), "Cohort"));
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(request.status());
    }

    @Override
    protected void validate(CohortEnrollment entity, boolean creating) {
        Contract contract = entity.getContract();
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
        if (entity.getStartDate().isBefore(entity.getCohort().getStartDate())) {
            throw new BusinessRuleException("Enrollment cannot begin before the cohort");
        }
        if (entity.getCohort().getEndDate() != null
                && (entity.getEndDate() == null
                || entity.getEndDate().isAfter(entity.getCohort().getEndDate()))) {
            throw new BusinessRuleException("Enrollment cannot outlive the cohort");
        }
        if (entity.getStatus() == EnrollmentStatus.ACTIVE
                && contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessRuleException("An active enrollment requires an active contract");
        }
        if (entity.getStatus() == EnrollmentStatus.ACTIVE
                && entity.getCohort().getStatus() != CohortStatus.ACTIVE) {
            throw new BusinessRuleException("An active enrollment requires an active cohort");
        }
        if (OCCUPYING_STATUSES.contains(entity.getStatus())
                && enrollmentRepository.existsOverlappingEnrollment(
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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
