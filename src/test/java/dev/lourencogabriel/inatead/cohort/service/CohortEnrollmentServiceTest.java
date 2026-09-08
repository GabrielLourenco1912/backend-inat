package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.cohort.dto.CohortEnrollmentDtos;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CohortEnrollmentServiceTest {

    @Mock
    private CohortEnrollmentRepository enrollmentRepository;
    @Mock
    private ContractRepository contractRepository;
    @Mock
    private CohortRepository cohortRepository;

    private CohortEnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new CohortEnrollmentService(
                enrollmentRepository,
                contractRepository,
                cohortRepository
        );
    }

    @Test
    void shouldRejectEnrollmentOutsideCohortPeriod() {
        Contract contract = new Contract();
        contract.setId("contract");
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setEndDate(LocalDate.of(2026, 12, 31));
        contract.setStatus(ContractStatus.ACTIVE);
        Cohort cohort = new Cohort();
        cohort.setId("cohort");
        cohort.setStartDate(LocalDate.of(2026, 3, 1));
        cohort.setEndDate(LocalDate.of(2026, 11, 30));
        cohort.setStatus(CohortStatus.ACTIVE);
        when(contractRepository.findById("contract")).thenReturn(Optional.of(contract));
        when(cohortRepository.findById("cohort")).thenReturn(Optional.of(cohort));

        CohortEnrollmentDtos.Request request = new CohortEnrollmentDtos.Request(
                "contract",
                "cohort",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 10, 31),
                EnrollmentStatus.ACTIVE
        );

        assertThrows(BusinessRuleException.class, () -> service.create(request));
    }
}
