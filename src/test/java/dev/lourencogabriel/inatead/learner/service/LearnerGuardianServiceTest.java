package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.learner.dto.LearnerGuardianDtos;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.model.LearnerGuardian;
import dev.lourencogabriel.inatead.learner.model.LearnerGuardianId;
import dev.lourencogabriel.inatead.learner.repository.LearnerGuardianRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearnerGuardianServiceTest {

    @Mock
    private LearnerGuardianRepository repository;
    @Mock
    private LearnerRepository learnerRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private LearnerRelationshipPolicy relationshipPolicy;

    private LearnerGuardianService service;

    @BeforeEach
    void setUp() {
        service = new LearnerGuardianService(
                repository,
                learnerRepository,
                personRepository,
                relationshipPolicy
        );
    }

    @Test
    void shouldRejectGuardianWhenPersonIsNotAnAdult() {
        Learner learner = learner();
        Person guardian = person("guardian", LocalDate.of(2010, 1, 1));
        when(learnerRepository.findById("learner")).thenReturn(Optional.of(learner));
        when(personRepository.findById("guardian")).thenReturn(Optional.of(guardian));
        doThrow(new BusinessRuleException("A guardian must be at least 18 years old"))
                .when(relationshipPolicy).validateGuardianEligibility(guardian);

        assertThrows(
                BusinessRuleException.class,
                () -> service.create("learner", request(true))
        );

        verify(repository, never()).save(any());
    }

    @Test
    void shouldPreserveLastLegalGuardianWhenLinkIsUpdated() {
        LearnerGuardian link = guardianLink();
        when(repository.findById(new LearnerGuardianId("learner", "guardian")))
                .thenReturn(Optional.of(link));
        doThrow(new BusinessRuleException("A minor learner requires at least one legal guardian"))
                .when(relationshipPolicy).validateGuardianUpdate(link, false);

        assertThrows(
                BusinessRuleException.class,
                () -> service.update("learner", "guardian", request(false))
        );

        verify(repository, never()).save(any());
    }

    @Test
    void shouldPreserveLastLegalGuardianWhenLinkIsDeleted() {
        LearnerGuardian link = guardianLink();
        when(repository.findById(new LearnerGuardianId("learner", "guardian")))
                .thenReturn(Optional.of(link));
        doThrow(new BusinessRuleException("A minor learner requires at least one legal guardian"))
                .when(relationshipPolicy).validateGuardianDelete(link);

        assertThrows(
                BusinessRuleException.class,
                () -> service.delete("learner", "guardian")
        );

        verify(repository, never()).delete(any());
    }

    private LearnerGuardianDtos.Request request(boolean legalGuardian) {
        return new LearnerGuardianDtos.Request(
                "guardian",
                "MOTHER",
                legalGuardian,
                true
        );
    }

    private LearnerGuardian guardianLink() {
        LearnerGuardian link = new LearnerGuardian();
        link.setId(new LearnerGuardianId("learner", "guardian"));
        link.setLearner(learner());
        link.setGuardianPerson(person("guardian", LocalDate.of(1980, 1, 1)));
        link.setLegalGuardian(true);
        return link;
    }

    private Learner learner() {
        Learner learner = new Learner();
        learner.setId("learner");
        learner.setPerson(person("learner-person", LocalDate.of(2010, 1, 1)));
        return learner;
    }

    private Person person(String id, LocalDate birthDate) {
        Person person = new Person();
        person.setId(id);
        person.setBirthDate(birthDate);
        return person;
    }
}
