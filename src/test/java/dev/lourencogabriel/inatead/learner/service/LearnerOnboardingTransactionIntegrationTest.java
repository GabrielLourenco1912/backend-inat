package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.address.repository.AddressRepository;
import dev.lourencogabriel.inatead.learner.dto.LearnerOnboardingDtos;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class LearnerOnboardingTransactionIntegrationTest {

    @Autowired
    private LearnerOnboardingService service;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private LearnerRepository learnerRepository;

    @Test
    void shouldRollBackPersonAndLearnerWhenGuardianLinkFails() {
        long addressCountBefore = addressRepository.count();
        String taxId = "98765432100";
        String registrationNumber = "ROLLBACK-001";
        LearnerOnboardingDtos.Request request = new LearnerOnboardingDtos.Request(
                new PersonDtos.Request(
                        address(),
                        "Minor Learner",
                        taxId,
                        null,
                        "41999999999",
                        LocalDate.of(2010, 1, 1),
                        null,
                        RecordStatus.ACTIVE,
                        Set.of(PersonTypeCode.LEARNER)
                ),
                new LearnerOnboardingDtos.LearnerProfile(
                        registrationNumber,
                        false,
                        RecordStatus.ACTIVE
                ),
                List.of(new LearnerOnboardingDtos.Guardian(
                        "missing-guardian",
                        "MOTHER",
                        true,
                        true
                ))
        );

        assertThrows(ResourceNotFoundException.class, () -> service.onboard(request));

        assertFalse(personRepository.existsByTaxId(taxId));
        assertFalse(learnerRepository.existsByRegistrationNumber(registrationNumber));
        assertEquals(addressCountBefore, addressRepository.count());
    }

    private AddressDtos.Request address() {
        return new AddressDtos.Request(
                "80000000",
                "Test Street",
                "100",
                null,
                "Center",
                "Curitiba",
                "PR",
                "BR"
        );
    }
}
