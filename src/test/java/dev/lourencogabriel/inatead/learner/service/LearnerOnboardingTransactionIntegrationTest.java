package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.address.repository.AddressRepository;
import dev.lourencogabriel.inatead.learner.dto.LearnerOnboardingDtos;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.person.service.PersonService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @Autowired
    private PersonService personService;

    @Test
    void shouldCreateProfileForExistingTypedPersonWithoutChangingPerson() {
        var person = personService.create(personRequest("98765432101", LocalDate.of(2000, 1, 1)));
        long personCount = personRepository.count();

        var response = service.fromPerson(person.id(), new LearnerOnboardingDtos.ExistingPersonRequest(
                new LearnerOnboardingDtos.LearnerProfile("EXISTING-001", true, RecordStatus.ACTIVE),
                List.of()
        ));

        assertFalse(response.personCreated());
        assertEquals(person.id(), response.person().id());
        assertEquals(personCount, personRepository.count());
        assertTrue(learnerRepository.existsByPerson_Id(person.id()));
        assertEquals(Set.of(PersonTypeCode.LEARNER), personService.findById(person.id()).personTypes());
    }

    @Test
    void shouldRollBackExistingPersonLearnerWhenGuardianLinkFails() {
        var person = personService.create(personRequest("98765432102", LocalDate.of(2010, 1, 1)));
        String registrationNumber = "EXISTING-ROLLBACK-001";
        var request = new LearnerOnboardingDtos.ExistingPersonRequest(
                new LearnerOnboardingDtos.LearnerProfile(
                        registrationNumber, false, RecordStatus.ACTIVE
                ),
                List.of(new LearnerOnboardingDtos.Guardian(
                        "missing-guardian", "MOTHER", true, true
                ))
        );

        assertThrows(ResourceNotFoundException.class, () -> service.fromPerson(person.id(), request));

        assertTrue(personRepository.existsById(person.id()));
        assertFalse(learnerRepository.existsByRegistrationNumber(registrationNumber));
        assertEquals(Set.of(PersonTypeCode.LEARNER), personService.findById(person.id()).personTypes());
    }

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

    private PersonDtos.Request personRequest(String taxId, LocalDate birthDate) {
        return new PersonDtos.Request(
                address(), "Existing person", taxId, null, "41999999999",
                birthDate, null, RecordStatus.ACTIVE, Set.of(PersonTypeCode.LEARNER)
        );
    }
}
