package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.dto.UserDtos;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.user.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateDirectUserWithRegulationPending() {
        UserDtos.CreateRequest request = new UserDtos.CreateRequest(
                PERSON_ID,
                "Direct User",
                "Direct@Example.com",
                "plain-password",
                UserAccountStatus.ACTIVE
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
    }
}
