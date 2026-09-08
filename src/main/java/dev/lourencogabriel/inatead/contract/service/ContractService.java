package dev.lourencogabriel.inatead.contract.service;

import dev.lourencogabriel.inatead.contract.dto.ContractDtos;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.EnumSet;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService extends AbstractCrudService<Contract, ContractDtos.Request, ContractDtos.Response> {

    private static final LocalDate OPEN_ENDED_RANGE_END = LocalDate.of(9999, 12, 31);
    private static final EnumSet<ContractStatus> OCCUPYING_STATUSES =
            EnumSet.of(ContractStatus.ACTIVE, ContractStatus.SUSPENDED);

    private final ContractRepository contractRepository;
    private final LearnerRepository learnerRepository;
    private final OrganizationRepository organizationRepository;

    public ContractService(
            ContractRepository contractRepository,
            LearnerRepository learnerRepository,
            OrganizationRepository organizationRepository
    ) {
        super(contractRepository, "Contract");
        this.contractRepository = contractRepository;
        this.learnerRepository = learnerRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    protected Contract newEntity() {
        return new Contract();
    }

    @Override
    protected void apply(Contract entity, ContractDtos.Request request) {
        entity.setLearner(EntityLookup.require(learnerRepository, request.learnerId(), "Learner"));
        entity.setEmployer(EntityLookup.require(
                organizationRepository,
                request.employerId(),
                "Employer"
        ));
        entity.setSchool(
                request.schoolId() == null || request.schoolId().isBlank()
                        ? null
                        : EntityLookup.require(organizationRepository, request.schoolId(), "School")
        );
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setMonthlySalary(request.monthlySalary());
        entity.setWeeklyWorkloadMinutes(request.weeklyWorkloadMinutes());
        entity.setStatus(request.status());
    }

    @Override
    protected void validate(Contract entity, boolean creating) {
        if (entity.getEmployer().getOrganizationType() != OrganizationType.EMPLOYER) {
            throw new BusinessRuleException("employerId must reference an EMPLOYER organization");
        }
        if (entity.getSchool() != null
                && entity.getSchool().getOrganizationType() != OrganizationType.SCHOOL) {
            throw new BusinessRuleException("schoolId must reference a SCHOOL organization");
        }
        if (entity.getSchool() != null
                && entity.getSchool().getId().equals(entity.getEmployer().getId())) {
            throw new BusinessRuleException("Employer and school must be different organizations");
        }
        if (entity.getEndDate() != null && entity.getEndDate().isBefore(entity.getStartDate())) {
            throw new BusinessRuleException("Contract end date cannot precede start date");
        }
        if (entity.getStartDate().isBefore(entity.getLearner().getPerson().getBirthDate())) {
            throw new BusinessRuleException("Contract start date cannot precede the learner birth date");
        }
        if (entity.getStatus() == ContractStatus.ENDED && entity.getEndDate() == null) {
            throw new BusinessRuleException("Ended contracts require an end date");
        }

        validateSchoolRequirement(entity.getLearner(), entity.getStartDate(), entity.getSchool());

        if (OCCUPYING_STATUSES.contains(entity.getStatus())
                && contractRepository.existsOverlappingContract(
                        entity.getLearner().getId(),
                        OCCUPYING_STATUSES,
                        creating ? null : entity.getId(),
                        entity.getStartDate(),
                        entity.getEndDate() == null ? OPEN_ENDED_RANGE_END : entity.getEndDate()
                )) {
            throw new BusinessRuleException(
                    "The learner already has an active or suspended contract in this period"
            );
        }
    }

    private void validateSchoolRequirement(
            Learner learner,
            LocalDate contractStart,
            Organization school
    ) {
        int ageAtStart = Period.between(
                learner.getPerson().getBirthDate(),
                contractStart
        ).getYears();
        if (ageAtStart < 18 && !learner.isHasCompletedHighSchool() && school == null) {
            throw new BusinessRuleException(
                    "A minor learner who has not completed high school requires a school"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<ContractDtos.Response> findByLearner(String learnerId) {
        EntityLookup.require(learnerRepository, learnerId, "Learner");
        return contractRepository.findAllByLearner_IdOrderByStartDateDesc(learnerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContractDtos.Response> findByOrganization(String organizationId) {
        EntityLookup.require(organizationRepository, organizationId, "Organization");
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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
