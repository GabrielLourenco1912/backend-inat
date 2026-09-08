package dev.lourencogabriel.inatead.contract.service;

import dev.lourencogabriel.inatead.contract.dto.ContractDtos;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private LearnerRepository learnerRepository;
    @Mock
    private OrganizationRepository organizationRepository;

    private ContractService service;
    private Learner learner;
    private Organization employer;

    @BeforeEach
    void setUp() {
        service = new ContractService(
                contractRepository,
                learnerRepository,
                organizationRepository
        );
        learner = learner(LocalDate.of(2000, 1, 1), false);
        employer = organization("employer", OrganizationType.EMPLOYER);
        when(learnerRepository.findById("learner")).thenReturn(Optional.of(learner));
        when(organizationRepository.findById("employer")).thenReturn(Optional.of(employer));
    }

    @Test
    void shouldAllowNullableSchoolForAdultLearner() {
        when(contractRepository.existsOverlappingContract(
                anyString(),
                anyCollection(),
                any(),
                any(),
                any()
        )).thenReturn(false);
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContractDtos.Response response = assertDoesNotThrow(() -> service.create(request(null)));

        assertNull(response.schoolId());
    }

    @Test
    void shouldRequireSchoolForMinorWhoHasNotCompletedHighSchool() {
        learner.getPerson().setBirthDate(LocalDate.of(2010, 1, 1));
        learner.setHasCompletedHighSchool(false);

        assertThrows(BusinessRuleException.class, () -> service.create(request(null)));

        verify(contractRepository, never()).save(any());
    }

    @Test
    void shouldAllowNullableSchoolForMinorWhoCompletedHighSchool() {
        learner.getPerson().setBirthDate(LocalDate.of(2010, 1, 1));
        learner.setHasCompletedHighSchool(true);
        when(contractRepository.existsOverlappingContract(
                anyString(),
                anyCollection(),
                any(),
                any(),
                any()
        )).thenReturn(false);
        when(contractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContractDtos.Response response = assertDoesNotThrow(() -> service.create(request(null)));

        assertNull(response.schoolId());
    }

    private ContractDtos.Request request(String schoolId) {
        return new ContractDtos.Request(
                "learner",
                "employer",
                schoolId,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("1200.00"),
                1800,
                ContractStatus.ACTIVE
        );
    }

    private Learner learner(LocalDate birthDate, boolean completedHighSchool) {
        Person person = new Person();
        person.setId("person");
        person.setBirthDate(birthDate);
        Learner value = new Learner();
        value.setId("learner");
        value.setPerson(person);
        value.setHasCompletedHighSchool(completedHighSchool);
        return value;
    }

    private Organization organization(String id, OrganizationType type) {
        Organization value = new Organization();
        value.setId(id);
        value.setOrganizationType(type);
        return value;
    }
}
