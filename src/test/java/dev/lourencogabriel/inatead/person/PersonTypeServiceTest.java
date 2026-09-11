package dev.lourencogabriel.inatead.person;

import dev.lourencogabriel.inatead.learner.repository.LearnerGuardianRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationMembershipRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonType;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonTypeRepository;
import dev.lourencogabriel.inatead.person.service.PersonTypeService;
import dev.lourencogabriel.inatead.user.repository.UserRoleRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonTypeServiceTest {

    @Mock private PersonTypeRepository typeRepository;
    @Mock private LearnerRepository learnerRepository;
    @Mock private LearnerGuardianRepository guardianRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @InjectMocks private PersonTypeService service;

    @ParameterizedTest
    @EnumSource(PersonTypeCode.class)
    void shouldPreventRemovingTypesRequiredByExistingAssignments(PersonTypeCode type) {
        Person person = new Person();
        person.setId("person");
        switch (type) {
            case LEARNER -> when(learnerRepository.existsByPerson_Id("person")).thenReturn(true);
            case GUARDIAN -> when(guardianRepository.existsByGuardianPerson_Id("person")).thenReturn(true);
            case INSTRUCTOR -> when(lessonRepository.existsByInstructorPerson_Id("person")).thenReturn(true);
            case EMPLOYER_MANAGER -> when(membershipRepository.findRoleCodesByPersonId("person"))
                    .thenReturn(List.of("EMPLOYER_MANAGER"));
            case ADMIN -> when(userRoleRepository.findRoleCodesByPersonId("person")).thenReturn(List.of("ADMIN"));
        }
        assertThrows(BusinessRuleException.class, () -> service.validateExistingAssignments(person));
    }

    @Test
    void shouldAllowOtherTypesWhilePreservingTheAssignedType() {
        Person person = new Person();
        person.setId("person");
        person.getPersonTypes().add(new PersonType(PersonTypeCode.EMPLOYER_MANAGER));
        person.getPersonTypes().add(new PersonType(PersonTypeCode.GUARDIAN));
        when(membershipRepository.findRoleCodesByPersonId("person")).thenReturn(List.of("EMPLOYER_MANAGER"));
        when(userRoleRepository.findRoleCodesByPersonId("person")).thenReturn(List.of("EMPLOYER_MANAGER"));

        assertDoesNotThrow(() -> service.validateExistingAssignments(person));
    }
}
