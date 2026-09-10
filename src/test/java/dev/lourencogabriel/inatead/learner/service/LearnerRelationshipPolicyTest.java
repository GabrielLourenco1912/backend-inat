package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.model.LearnerGuardian;
import dev.lourencogabriel.inatead.learner.repository.LearnerGuardianRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearnerRelationshipPolicyTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-09T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private LearnerRepository learnerRepository;
    @Mock
    private LearnerGuardianRepository guardianRepository;
    @Mock
    private ContractRepository contractRepository;

    private LearnerRelationshipPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LearnerRelationshipPolicy(
                learnerRepository,
                guardianRepository,
                contractRepository,
                CLOCK
        );
    }

    @Test
    void shouldRejectMinorGuardian() {
        Person guardian = person("guardian", LocalDate.of(2008, 9, 10));

        assertThrows(
                BusinessRuleException.class,
                () -> policy.validateGuardianEligibility(guardian)
        );
    }

    @Test
    void shouldAcceptGuardianOnEighteenthBirthday() {
        Person guardian = person("guardian", LocalDate.of(2008, 9, 9));

        assertDoesNotThrow(() -> policy.validateGuardianEligibility(guardian));
    }

    @Test
    void shouldRejectMinorLearnerUpdateWithoutLegalGuardian() {
        Learner learner = learner(LocalDate.of(2010, 1, 1), false);
        when(guardianRepository.existsByLearner_IdAndLegalGuardianTrue("learner"))
                .thenReturn(false);

        assertThrows(
                BusinessRuleException.class,
                () -> policy.validateLearnerUpdate(learner)
        );
    }

    @Test
    void shouldRejectRemovingLegalStatusFromLastLegalGuardianOfMinor() {
        LearnerGuardian link = legalGuardianLink(learner(LocalDate.of(2010, 1, 1), false));
        when(guardianRepository
                .existsByLearner_IdAndLegalGuardianTrueAndGuardianPerson_IdNot(
                        "learner",
                        "guardian"
                )).thenReturn(false);

        assertThrows(
                BusinessRuleException.class,
                () -> policy.validateGuardianUpdate(link, false)
        );
    }

    @Test
    void shouldRejectDeletingLastLegalGuardianOfMinor() {
        LearnerGuardian link = legalGuardianLink(learner(LocalDate.of(2010, 1, 1), false));
        when(guardianRepository
                .existsByLearner_IdAndLegalGuardianTrueAndGuardianPerson_IdNot(
                        "learner",
                        "guardian"
                )).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> policy.validateGuardianDelete(link));
    }

    @Test
    void shouldAllowDeletingLegalGuardianWhenMinorHasAnotherOne() {
        LearnerGuardian link = legalGuardianLink(learner(LocalDate.of(2010, 1, 1), false));
        when(guardianRepository
                .existsByLearner_IdAndLegalGuardianTrueAndGuardianPerson_IdNot(
                        "learner",
                        "guardian"
                )).thenReturn(true);

        assertDoesNotThrow(() -> policy.validateGuardianDelete(link));
    }

    @Test
    void shouldRejectLearnerUpdateThatLeavesExistingMinorContractWithoutSchool() {
        Learner learner = learner(LocalDate.of(2010, 1, 1), false);
        Contract contract = new Contract();
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setSchool(null);
        when(guardianRepository.existsByLearner_IdAndLegalGuardianTrue("learner"))
                .thenReturn(true);
        when(contractRepository.findAllByLearner_IdOrderByStartDateDesc("learner"))
                .thenReturn(List.of(contract));

        assertThrows(
                BusinessRuleException.class,
                () -> policy.validateLearnerUpdate(learner)
        );
    }

    @Test
    void shouldRejectChangingContractSchoolOrganizationToAnotherType() {
        when(contractRepository.existsBySchool_Id("school")).thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> policy.validateOrganizationType("school", OrganizationType.EMPLOYER)
        );
    }

    @Test
    void shouldRejectDeactivatingLearnerWithLiveContract() {
        Learner learner = learner(LocalDate.of(2000, 1, 1), true);
        learner.setStatus(RecordStatus.INACTIVE);
        when(contractRepository.existsByLearner_IdAndStatusIn(
                org.mockito.ArgumentMatchers.eq("learner"),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> policy.validateLearnerUpdate(learner));
    }

    @Test
    void shouldRejectDeactivatingOrganizationWithLiveContract() {
        when(contractRepository.existsByEmployer_IdAndStatusIn(
                org.mockito.ArgumentMatchers.eq("employer"),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> policy.validateOrganizationStatus("employer", RecordStatus.INACTIVE)
        );
    }

    @Test
    void shouldRejectDeactivatingPersonWithLiveContract() {
        Learner learner = learner(LocalDate.of(2000, 1, 1), true);
        learner.getPerson().setStatus(RecordStatus.INACTIVE);
        when(learnerRepository.findByPerson_Id("learner-person"))
                .thenReturn(Optional.of(learner));
        when(contractRepository.existsByLearner_IdAndStatusIn(
                org.mockito.ArgumentMatchers.eq("learner"),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> policy.validatePersonUpdate(learner.getPerson())
        );
    }

    @Test
    void shouldRevalidateGuardianAgeWhenPersonIsUpdated() {
        Person guardian = person("guardian", LocalDate.of(2010, 1, 1));
        when(guardianRepository.existsByGuardianPerson_Id("guardian")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> policy.validatePersonUpdate(guardian));
    }

    @Test
    void shouldRevalidateLearnerRelationshipsWhenPersonIsUpdated() {
        Learner learner = learner(LocalDate.of(2010, 1, 1), false);
        when(learnerRepository.findByPerson_Id("learner-person"))
                .thenReturn(Optional.of(learner));
        when(guardianRepository.existsByLearner_IdAndLegalGuardianTrue("learner"))
                .thenReturn(false);

        assertThrows(
                BusinessRuleException.class,
                () -> policy.validatePersonUpdate(learner.getPerson())
        );
    }

    private Learner learner(LocalDate birthDate, boolean completedHighSchool) {
        Learner learner = new Learner();
        learner.setId("learner");
        learner.setPerson(person("learner-person", birthDate));
        learner.setHasCompletedHighSchool(completedHighSchool);
        return learner;
    }

    private LearnerGuardian legalGuardianLink(Learner learner) {
        LearnerGuardian link = new LearnerGuardian();
        link.setLearner(learner);
        link.setGuardianPerson(person("guardian", LocalDate.of(1980, 1, 1)));
        link.setLegalGuardian(true);
        return link;
    }

    private Person person(String id, LocalDate birthDate) {
        Person person = new Person();
        person.setId(id);
        person.setBirthDate(birthDate);
        return person;
    }
}
