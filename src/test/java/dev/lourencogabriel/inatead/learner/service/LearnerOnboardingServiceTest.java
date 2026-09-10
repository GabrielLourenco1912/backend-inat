package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.learner.dto.LearnerDtos;
import dev.lourencogabriel.inatead.learner.dto.LearnerGuardianDtos;
import dev.lourencogabriel.inatead.learner.dto.LearnerOnboardingDtos;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearnerOnboardingServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-04T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private PersonRepository personRepository;
    @Mock
    private PersonService personService;
    @Mock
    private LearnerService learnerService;
    @Mock
    private LearnerGuardianService learnerGuardianService;

    private LearnerOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new LearnerOnboardingService(
                personRepository,
                personService,
                learnerService,
                learnerGuardianService,
                CLOCK
        );
    }

    @Test
    void shouldCreatePersonAndLearnerForAdult() {
        PersonDtos.Request personRequest = personRequest("12345678909", LocalDate.of(2000, 1, 1));
        PersonDtos.Response personResponse = personResponse("person", personRequest);
        when(personRepository.findByTaxId(personRequest.taxId())).thenReturn(Optional.empty());
        when(personService.create(personRequest)).thenReturn(personResponse);
        when(learnerService.create(any(LearnerDtos.Request.class)))
                .thenReturn(learnerResponse("learner", "person"));

        LearnerOnboardingDtos.Response response = service.onboard(request(personRequest, List.of()));

        assertTrue(response.personCreated());
        assertEquals("person", response.person().id());
        assertEquals("learner", response.learner().id());
        assertTrue(response.guardians().isEmpty());

        ArgumentCaptor<LearnerDtos.Request> learnerRequest =
                ArgumentCaptor.forClass(LearnerDtos.Request.class);
        verify(learnerService).create(learnerRequest.capture());
        assertEquals("person", learnerRequest.getValue().personId());
        assertEquals("MAT-001", learnerRequest.getValue().registrationNumber());
        verifyNoInteractions(learnerGuardianService);
    }

    @Test
    void shouldReusePersonWithSameTaxIdAndBirthDate() {
        PersonDtos.Request personRequest = personRequest("12345678909", LocalDate.of(2000, 1, 1));
        Person existing = new Person();
        existing.setId("existing-person");
        existing.setTaxId(personRequest.taxId());
        existing.setBirthDate(personRequest.birthDate());
        when(personRepository.findByTaxId(personRequest.taxId())).thenReturn(Optional.of(existing));
        when(personService.findById("existing-person"))
                .thenReturn(personResponse("existing-person", personRequest));
        when(learnerService.create(any(LearnerDtos.Request.class)))
                .thenReturn(learnerResponse("learner", "existing-person"));

        LearnerOnboardingDtos.Response response = service.onboard(request(personRequest, List.of()));

        assertFalse(response.personCreated());
        assertEquals("existing-person", response.person().id());
        verify(personService, never()).create(any());
    }

    @Test
    void shouldRejectExistingTaxIdWithDifferentBirthDate() {
        PersonDtos.Request personRequest = personRequest("12345678909", LocalDate.of(2000, 1, 1));
        Person existing = new Person();
        existing.setId("existing-person");
        existing.setTaxId(personRequest.taxId());
        existing.setBirthDate(LocalDate.of(1999, 1, 1));
        when(personRepository.findByTaxId(personRequest.taxId())).thenReturn(Optional.of(existing));

        assertThrows(
                ResourceConflictException.class,
                () -> service.onboard(request(personRequest, List.of()))
        );

        verifyNoInteractions(learnerService, learnerGuardianService);
        verify(personService, never()).create(any());
    }

    @Test
    void shouldRequireLegalGuardianForMinor() {
        PersonDtos.Request personRequest = personRequest("12345678909", LocalDate.of(2010, 1, 1));
        List<LearnerOnboardingDtos.Guardian> guardians = List.of(new LearnerOnboardingDtos.Guardian(
                "guardian",
                "MOTHER",
                false,
                true
        ));

        assertThrows(
                BusinessRuleException.class,
                () -> service.onboard(request(personRequest, guardians))
        );

        verifyNoInteractions(personRepository, personService, learnerService, learnerGuardianService);
    }

    @Test
    void shouldCreateLegalGuardianLinkForMinor() {
        PersonDtos.Request personRequest = personRequest("12345678909", LocalDate.of(2010, 1, 1));
        LearnerOnboardingDtos.Guardian guardian = new LearnerOnboardingDtos.Guardian(
                "guardian",
                "mother",
                true,
                true
        );
        when(personRepository.findByTaxId(personRequest.taxId())).thenReturn(Optional.empty());
        when(personService.create(personRequest)).thenReturn(personResponse("person", personRequest));
        when(learnerService.create(any(LearnerDtos.Request.class)))
                .thenReturn(learnerResponse("learner", "person"));
        when(learnerGuardianService.create(any(), any(LearnerGuardianDtos.Request.class)))
                .thenReturn(new LearnerGuardianDtos.Response(
                        "learner",
                        "guardian",
                        "Maria",
                        "MOTHER",
                        true,
                        true,
                        null,
                        null
                ));

        LearnerOnboardingDtos.Response response = service.onboard(request(
                personRequest,
                List.of(guardian)
        ));

        assertEquals(1, response.guardians().size());
        ArgumentCaptor<LearnerGuardianDtos.Request> guardianRequest =
                ArgumentCaptor.forClass(LearnerGuardianDtos.Request.class);
        verify(learnerGuardianService).create(eq("learner"), guardianRequest.capture());
        assertEquals("guardian", guardianRequest.getValue().guardianPersonId());
        assertTrue(guardianRequest.getValue().legalGuardian());
    }

    @Test
    void shouldRejectDuplicateGuardianInSameRequest() {
        PersonDtos.Request personRequest = personRequest("12345678909", LocalDate.of(2000, 1, 1));
        LearnerOnboardingDtos.Guardian guardian = new LearnerOnboardingDtos.Guardian(
                "guardian",
                "MOTHER",
                true,
                true
        );

        assertThrows(
                BusinessRuleException.class,
                () -> service.onboard(request(personRequest, List.of(guardian, guardian)))
        );

        verifyNoInteractions(personRepository, personService, learnerService, learnerGuardianService);
    }

    private LearnerOnboardingDtos.Request request(
            PersonDtos.Request person,
            List<LearnerOnboardingDtos.Guardian> guardians
    ) {
        return new LearnerOnboardingDtos.Request(
                person,
                new LearnerOnboardingDtos.LearnerProfile(
                        "MAT-001",
                        false,
                        RecordStatus.ACTIVE
                ),
                guardians
        );
    }

    private PersonDtos.Request personRequest(String taxId, LocalDate birthDate) {
        return new PersonDtos.Request(
                addressRequest(),
                "Joao da Silva",
                taxId,
                "joao@example.com",
                "41999999999",
                birthDate,
                null,
                RecordStatus.ACTIVE
        );
    }

    private PersonDtos.Response personResponse(String id, PersonDtos.Request request) {
        return new PersonDtos.Response(
                id,
                addressResponse(request.address()),
                request.fullName(),
                request.taxId(),
                request.contactEmail(),
                request.phoneNumber(),
                request.birthDate(),
                request.gender(),
                request.status(),
                null,
                null
        );
    }

    private AddressDtos.Request addressRequest() {
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

    private AddressDtos.Response addressResponse(AddressDtos.Request request) {
        return new AddressDtos.Response(
                "address",
                request.postalCode(),
                request.street(),
                request.streetNumber(),
                request.addressLine2(),
                request.district(),
                request.city(),
                request.stateCode(),
                request.countryCode(),
                null,
                null
        );
    }

    private LearnerDtos.Response learnerResponse(String id, String personId) {
        return new LearnerDtos.Response(
                id,
                personId,
                "MAT-001",
                false,
                RecordStatus.ACTIVE,
                null,
                null
        );
    }
}
