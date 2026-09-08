package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.user.model.Role;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.model.UserRoleId;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.user.repository.UserRoleRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

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
}
