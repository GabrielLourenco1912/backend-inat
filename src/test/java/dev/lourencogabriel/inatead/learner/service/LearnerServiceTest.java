package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.learner.dto.LearnerDtos;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonType;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearnerServiceTest {

    @Mock
    private LearnerRepository learnerRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private LearnerRelationshipPolicy relationshipPolicy;

    private LearnerService service;

    @BeforeEach
    void setUp() {
        service = new LearnerService(
                learnerRepository,
                personRepository,
                relationshipPolicy
        );
    }

    @Test
    void shouldValidateRelationshipsWhenLearnerIsUpdated() {
        Learner learner = learner("person");
        when(learnerRepository.findById("learner")).thenReturn(Optional.of(learner));
        when(personRepository.findByIdForUpdate("person")).thenReturn(Optional.of(learner.getPerson()));
        when(learnerRepository.save(learner)).thenReturn(learner);

        service.update("learner", request("person"));

        verify(relationshipPolicy).validateLearnerUpdate(learner);
    }

    @Test
    void shouldNotAllowChangingThePersonOfAnExistingLearner() {
        Learner learner = learner("person");
        when(learnerRepository.findById("learner")).thenReturn(Optional.of(learner));

        assertThrows(
                BusinessRuleException.class,
                () -> service.update("learner", request("another-person"))
        );

        verify(personRepository, never()).findByIdForUpdate(any());
        verify(learnerRepository, never()).save(any());
    }

    private LearnerDtos.Request request(String personId) {
        return new LearnerDtos.Request(
                personId,
                "MAT-001",
                false,
                RecordStatus.ACTIVE
        );
    }

    private Learner learner(String personId) {
        Person person = new Person();
        person.setId(personId);
        person.getPersonTypes().add(new PersonType(PersonTypeCode.LEARNER));
        person.setBirthDate(LocalDate.of(2000, 1, 1));
        Learner learner = new Learner();
        learner.setId("learner");
        learner.setPerson(person);
        learner.setRegistrationNumber("MAT-001");
        learner.setStatus(RecordStatus.ACTIVE);
        return learner;
    }
}
