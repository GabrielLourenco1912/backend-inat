package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.model.LearnerGuardian;
import dev.lourencogabriel.inatead.learner.repository.LearnerGuardianRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.service.PersonTypeRules;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LearnerRelationshipPolicy {

    private static final Set<ContractStatus> LIVE_CONTRACT_STATUSES = Set.of(
            ContractStatus.ACTIVE,
            ContractStatus.SUSPENDED
    );

    private final LearnerRepository learnerRepository;
    private final LearnerGuardianRepository guardianRepository;
    private final ContractRepository contractRepository;
    private final Clock clock;

    public void validateGuardianEligibility(Person guardian) {
        PersonTypeRules.requireType(guardian, PersonTypeCode.GUARDIAN);
        if (isMinorAt(guardian.getBirthDate(), LocalDate.now(clock))) {
            throw new BusinessRuleException("A guardian must be at least 18 years old");
        }
    }

    public void validateGuardianUpdate(LearnerGuardian link, boolean legalGuardianAfterUpdate) {
        if (link.isLegalGuardian()
                && !legalGuardianAfterUpdate
                && isMinorToday(link.getLearner())
                && !hasOtherLegalGuardian(link)) {
            throw missingLegalGuardian();
        }
    }

    public void validateGuardianDelete(LearnerGuardian link) {
        if (link.isLegalGuardian()
                && isMinorToday(link.getLearner())
                && !hasOtherLegalGuardian(link)) {
            throw missingLegalGuardian();
        }
    }

    public void validateLearnerUpdate(Learner learner) {
        if (learner.getStatus() != RecordStatus.ACTIVE
                && contractRepository.existsByLearner_IdAndStatusIn(
                        learner.getId(),
                        LIVE_CONTRACT_STATUSES
                )) {
            throw new BusinessRuleException(
                    "A learner with an active or suspended contract must remain active"
            );
        }
        if (isMinorToday(learner)
                && !guardianRepository.existsByLearner_IdAndLegalGuardianTrue(learner.getId())) {
            throw missingLegalGuardian();
        }
        validateExistingContractSchools(learner);
    }

    public void validatePersonUpdate(Person person) {
        if (guardianRepository.existsByGuardianPerson_Id(person.getId())) {
            validateGuardianEligibility(person);
        }
        learnerRepository.findByPerson_Id(person.getId())
                .ifPresent(learner -> {
                    if (person.getStatus() != RecordStatus.ACTIVE
                            && hasLiveContract(learner.getId())) {
                        throw new BusinessRuleException(
                                "A person linked to an active or suspended contract must remain active"
                        );
                    }
                    validateLearnerUpdate(learner);
                });
    }

    public void validateOrganizationType(String organizationId, OrganizationType organizationType) {
        if (contractRepository.existsBySchool_Id(organizationId)
                && organizationType != OrganizationType.SCHOOL) {
            throw new BusinessRuleException(
                    "An organization referenced as a contract school must remain a SCHOOL"
            );
        }
        if (contractRepository.existsByEmployer_Id(organizationId)
                && organizationType != OrganizationType.EMPLOYER) {
            throw new BusinessRuleException(
                    "An organization referenced as a contract employer must remain an EMPLOYER"
            );
        }
    }

    public void validateOrganizationStatus(String organizationId, RecordStatus status) {
        if (status == RecordStatus.ACTIVE) {
            return;
        }
        if (contractRepository.existsByEmployer_IdAndStatusIn(
                organizationId,
                LIVE_CONTRACT_STATUSES
        ) || contractRepository.existsBySchool_IdAndStatusIn(
                organizationId,
                LIVE_CONTRACT_STATUSES
        )) {
            throw new BusinessRuleException(
                    "An organization with an active or suspended contract must remain active"
            );
        }
    }

    public void validateOrganizationTaxId(
            String organizationId,
            String currentTaxId,
            String requestedTaxId
    ) {
        if (currentTaxId.equals(requestedTaxId)) {
            return;
        }
        if (contractRepository.existsByEmployer_Id(organizationId)
                || contractRepository.existsBySchool_Id(organizationId)) {
            throw new BusinessRuleException(
                    "The tax ID of an organization referenced by a contract cannot be changed"
            );
        }
    }

    private void validateExistingContractSchools(Learner learner) {
        if (learner.isHasCompletedHighSchool()) {
            return;
        }

        boolean missingRequiredSchool = contractRepository
                .findAllByLearner_IdOrderByStartDateDesc(learner.getId())
                .stream()
                .anyMatch(contract -> requiresSchool(learner, contract));
        if (missingRequiredSchool) {
            throw new BusinessRuleException(
                    "A minor learner who has not completed high school requires a school"
            );
        }
    }

    private boolean hasLiveContract(String learnerId) {
        return contractRepository.existsByLearner_IdAndStatusIn(
                learnerId,
                LIVE_CONTRACT_STATUSES
        );
    }

    private boolean requiresSchool(Learner learner, Contract contract) {
        int ageAtStart = Period.between(
                learner.getPerson().getBirthDate(),
                contract.getStartDate()
        ).getYears();
        return ageAtStart < 18 && contract.getSchool() == null;
    }

    private boolean hasOtherLegalGuardian(LearnerGuardian link) {
        return guardianRepository
                .existsByLearner_IdAndLegalGuardianTrueAndGuardianPerson_IdNot(
                        link.getLearner().getId(),
                        link.getGuardianPerson().getId()
                );
    }

    private boolean isMinorToday(Learner learner) {
        return isMinorAt(learner.getPerson().getBirthDate(), LocalDate.now(clock));
    }

    private boolean isMinorAt(LocalDate birthDate, LocalDate referenceDate) {
        return birthDate.plusYears(18).isAfter(referenceDate);
    }

    private BusinessRuleException missingLegalGuardian() {
        return new BusinessRuleException(
                "A minor learner requires at least one legal guardian"
        );
    }
}
