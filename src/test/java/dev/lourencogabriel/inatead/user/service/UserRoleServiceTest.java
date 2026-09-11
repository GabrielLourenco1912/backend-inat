package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonType;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.user.model.Role;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.model.UserRole;
import dev.lourencogabriel.inatead.user.model.UserRoleId;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.user.repository.UserRoleRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private LearnerRepository learnerRepository;

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private UserRoleService service;

    @Test
    void shouldRejectGrantingALegacyRoleStillPresentInTheDatabase() {
        String userId = "01JTESTUSER0000000000000000";
        short roleId = 9;
        UserRoleId assignmentId = new UserRoleId(userId, roleId);
        User user = new User("User", "user@example.com", "hash");
        Role legacyRole = new Role();
        legacyRole.setId(roleId);
        legacyRole.setCode("STAFF");

        when(userRoleRepository.existsById(assignmentId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(legacyRole));

        assertThrows(
                BusinessRuleException.class,
                () -> service.grant(userId, roleId, null)
        );

        verify(userRoleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldGrantLearnerRoleWhenUserPersonHasAnActiveLearnerProfile() {
        String userId = "01JTESTUSER0000000000000000";
        short roleId = 3;
        UserRoleId assignmentId = new UserRoleId(userId, roleId);
        Person person = person();
        User user = user(userId, person);
        Role role = role(roleId, "LEARNER");
        Learner learner = new Learner();
        learner.setPerson(person);
        learner.setStatus(RecordStatus.ACTIVE);

        when(userRoleRepository.existsById(assignmentId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(learnerRepository.findByPerson_Id(person.getId()))
                .thenReturn(Optional.of(learner));
        when(userRoleRepository.save(any(UserRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.grant(userId, roleId, null);

        assertEquals(userId, response.userId());
        assertEquals(roleId, response.roleId());
        assertEquals("LEARNER", response.roleCode());
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void shouldRejectLearnerRoleWhenUserPersonHasNoLearnerProfile() {
        String userId = "01JTESTUSER0000000000000000";
        short roleId = 3;
        Person person = person();
        User user = user(userId, person);
        Role role = role(roleId, "LEARNER");

        when(userRoleRepository.existsById(new UserRoleId(userId, roleId))).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(learnerRepository.findByPerson_Id(person.getId())).thenReturn(Optional.empty());

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.grant(userId, roleId, null)
        );

        assertEquals(
                "The LEARNER role requires an active learner profile linked to the user's person",
                exception.getMessage()
        );
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void shouldRejectLearnerRoleWhenLinkedLearnerProfileIsInactive() {
        String userId = "01JTESTUSER0000000000000000";
        short roleId = 3;
        Person person = person();
        User user = user(userId, person);
        Role role = role(roleId, "LEARNER");
        Learner learner = new Learner();
        learner.setPerson(person);
        learner.setStatus(RecordStatus.INACTIVE);

        when(userRoleRepository.existsById(new UserRoleId(userId, roleId))).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(learnerRepository.findByPerson_Id(person.getId()))
                .thenReturn(Optional.of(learner));

        assertThrows(
                BusinessRuleException.class,
                () -> service.grant(userId, roleId, null)
        );

        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void shouldRejectLearnerRoleWhenUserHasNoLinkedPerson() {
        String userId = "01JTESTUSER0000000000000000";
        short roleId = 3;
        User user = user(userId, null);
        Role role = role(roleId, "LEARNER");

        when(userRoleRepository.existsById(new UserRoleId(userId, roleId))).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        assertThrows(
                BusinessRuleException.class,
                () -> service.grant(userId, roleId, null)
        );

        verifyNoInteractions(learnerRepository);
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void shouldNotRequireLearnerProfileForOtherRoles() {
        String userId = "01JTESTUSER0000000000000000";
        short roleId = 2;
        User user = user(userId, person());
        Role role = role(roleId, "INSTRUCTOR");

        when(userRoleRepository.existsById(new UserRoleId(userId, roleId))).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(userRoleRepository.save(any(UserRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.grant(userId, roleId, null);

        assertEquals("INSTRUCTOR", response.roleCode());
        verifyNoInteractions(learnerRepository);
    }

    @ParameterizedTest
    @EnumSource(SystemRoleCode.class)
    void shouldRejectEveryRoleWithoutItsCorrespondingPersonType(SystemRoleCode code) {
        Person person = person();
        person.getPersonTypes().clear();
        // A different type must not qualify the person for this role.
        person.getPersonTypes().add(new PersonType(PersonTypeCode.GUARDIAN));
        String userId = "typed-user";
        short roleId = 1;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, person)));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role(roleId, code.name())));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.grant(userId, roleId, null));

        assertEquals("The person must have the " + code + " person type", error.getMessage());
        verify(userRoleRepository, never()).save(any());
        verifyNoInteractions(learnerRepository);
    }

    @ParameterizedTest
    @EnumSource(value = SystemRoleCode.class, names = {"ADMIN", "INSTRUCTOR", "EMPLOYER_MANAGER"})
    void shouldGrantNonLearnerRolesWithMatchingPersonType(SystemRoleCode code) {
        Person person = person();
        person.getPersonTypes().clear();
        person.getPersonTypes().add(new PersonType(PersonTypeCode.valueOf(code.name())));
        String userId = "typed-user";
        short roleId = 1;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, person)));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role(roleId, code.name())));
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(code.name(), service.grant(userId, roleId, null).roleCode());
        verifyNoInteractions(learnerRepository);
    }

    private Person person() {
        Person person = new Person();
        person.setId("01JTESTPERSON000000000000000");
        person.getPersonTypes().add(new PersonType(PersonTypeCode.LEARNER));
        person.getPersonTypes().add(new PersonType(PersonTypeCode.INSTRUCTOR));
        when(personRepository.findByIdForUpdate(person.getId())).thenReturn(Optional.of(person));
        return person;
    }

    private User user(String id, Person person) {
        User user = new User("User", "user@example.com", "hash");
        user.setUlid(id);
        user.setPerson(person);
        return user;
    }

    private Role role(short id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        return role;
    }
}
