package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.auth.service.AccountSecurityService;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.dto.UserDtos;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.user.repository.UserRoleRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String PERSON_ID = "01JTESTPERSON000000000000000";
    private static final String USER_ID = "01JTESTUSER0000000000000000";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private AccountSecurityService accountSecurityService;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateDirectInvitedUserWithRegulationPending() {
        UserDtos.CreateRequest request = new UserDtos.CreateRequest(
                PERSON_ID,
                "Direct User",
                "Direct@Example.com",
                "plain-password",
                UserAccountStatus.INVITED
        );
        Person person = new Person();
        person.setId(PERSON_ID);

        when(this.userRepository.existsByEmail("direct@example.com")).thenReturn(false);
        when(this.userRepository.existsByPerson_Id(PERSON_ID)).thenReturn(false);
        when(this.personRepository.findById(PERSON_ID)).thenReturn(Optional.of(person));
        when(this.passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(this.userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setUlid(USER_ID);
            return savedUser;
        });

        this.userService.create(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(this.userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertAll(
                () -> assertFalse(createdUser.isRegulationAccepted()),
                () -> assertNull(createdUser.getRegulationAcceptedAt()),
                () -> assertSame(person, createdUser.getPerson())
        );
        verify(this.accountSecurityService).applyAdministrativeUpdate(
                createdUser,
                UserAccountStatus.INVITED,
                false
        );
    }

    @Test
    void shouldRejectDirectActiveUserCreationWithoutVerifiedEmail() {
        UserDtos.CreateRequest request = new UserDtos.CreateRequest(
                PERSON_ID,
                "Direct User",
                "direct@example.com",
                "plain-password",
                UserAccountStatus.ACTIVE
        );
        Person person = new Person();
        person.setId(PERSON_ID);

        when(this.userRepository.existsByEmail("direct@example.com")).thenReturn(false);
        when(this.userRepository.existsByPerson_Id(PERSON_ID)).thenReturn(false);
        when(this.personRepository.findById(PERSON_ID)).thenReturn(Optional.of(person));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> this.userService.create(request)
        );

        assertEquals(
                "An account without a verified email must remain INVITED until verification is complete",
                exception.getMessage()
        );
        verify(this.passwordEncoder, never()).encode(any());
        verify(this.userRepository, never()).save(any());
    }

    @Test
    void shouldRejectDirectUserCreationForInactivePerson() {
        UserDtos.CreateRequest request = new UserDtos.CreateRequest(
                PERSON_ID,
                "Direct User",
                "direct@example.com",
                "plain-password",
                UserAccountStatus.INVITED
        );
        Person person = new Person();
        person.setId(PERSON_ID);
        person.setStatus(RecordStatus.INACTIVE);
        when(this.userRepository.existsByEmail("direct@example.com")).thenReturn(false);
        when(this.userRepository.existsByPerson_Id(PERSON_ID)).thenReturn(false);
        when(this.personRepository.findById(PERSON_ID)).thenReturn(Optional.of(person));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> this.userService.create(request)
        );

        assertEquals("A user account requires an active person", exception.getMessage());
        verify(this.userRepository, never()).save(any());
        verify(this.passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldKeepLinkedPersonWhenUpdatingUser() {
        Person linkedPerson = new Person();
        linkedPerson.setId(PERSON_ID);
        User user = new User("Current Name", "current@example.com", "encoded-password");
        user.setUlid(USER_ID);
        user.setPerson(linkedPerson);
        user.setStatus(UserAccountStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.parse("2026-09-10T12:00:00Z"));
        UserDtos.UpdateRequest request = new UserDtos.UpdateRequest(
                "Updated Name",
                "current@example.com",
                null,
                UserAccountStatus.ACTIVE
        );

        when(this.userRepository.findWithRolesByUlidForUpdate(USER_ID))
                .thenReturn(Optional.of(user));
        when(this.userRepository.save(user)).thenReturn(user);

        this.userService.update(USER_ID, request);

        assertSame(linkedPerson, user.getPerson());
        verify(this.personRepository, never()).findById(any());
        verify(this.accountSecurityService).applyAdministrativeUpdate(
                user,
                UserAccountStatus.ACTIVE,
                false
        );
    }

    @Test
    void shouldRejectAdministrativeActivationWithoutVerifiedEmail() {
        User user = user(UserAccountStatus.INVITED, null);
        UserDtos.UpdateRequest request = new UserDtos.UpdateRequest(
                "Invited User",
                "current@example.com",
                null,
                UserAccountStatus.ACTIVE
        );
        when(this.userRepository.findWithRolesByUlidForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> this.userService.update(USER_ID, request)
        );

        assertEquals(
                "An account without a verified email must remain INVITED until verification is complete",
                exception.getMessage()
        );
        assertEquals(UserAccountStatus.INVITED, user.getStatus());
        verify(this.userRepository, never()).save(any());
    }

    @Test
    void shouldRequireInvitedStatusWhenChangingVerifiedEmail() {
        Instant verifiedAt = Instant.parse("2026-09-10T12:00:00Z");
        User user = user(UserAccountStatus.ACTIVE, verifiedAt);
        UserDtos.UpdateRequest request = new UserDtos.UpdateRequest(
                "Verified User",
                "new@example.com",
                null,
                UserAccountStatus.ACTIVE
        );
        when(this.userRepository.findWithRolesByUlidForUpdate(USER_ID))
                .thenReturn(Optional.of(user));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> this.userService.update(USER_ID, request)
        );

        assertEquals(
                "An account without a verified email must remain INVITED until verification is complete",
                exception.getMessage()
        );
        assertAll(
                () -> assertEquals("current@example.com", user.getEmail()),
                () -> assertEquals(verifiedAt, user.getEmailVerifiedAt()),
                () -> assertEquals(UserAccountStatus.ACTIVE, user.getStatus())
        );
        verify(this.userRepository, never()).save(any());
    }

    private User user(UserAccountStatus status, Instant emailVerifiedAt) {
        Person linkedPerson = new Person();
        linkedPerson.setId(PERSON_ID);
        User user = new User("Current Name", "current@example.com", "encoded-password");
        user.setUlid(USER_ID);
        user.setPerson(linkedPerson);
        user.setStatus(status);
        user.setEmailVerifiedAt(emailVerifiedAt);
        return user;
    }
}
