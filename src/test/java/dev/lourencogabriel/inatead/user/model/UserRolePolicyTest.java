package dev.lourencogabriel.inatead.user.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRolePolicyTest {

    @Test
    void shouldNotCreateAnImplicitRoleForAnAccountWithoutAssignments() {
        User user = new User("No role", "no-role@example.com", "hash");

        assertTrue(user.getAuthorities().isEmpty());
    }

    @Test
    void shouldExposeOnlySupportedSystemRolesAsAuthorities() {
        User user = new User("User", "user@example.com", "hash");
        user.setUserRoles(new HashSet<>(Set.of(
                assignment("ADMIN"),
                assignment("STAFF")
        )));

        assertEquals(
                Set.of("ROLE_ADMIN"),
                user.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    private UserRole assignment(String code) {
        Role role = new Role();
        role.setCode(code);
        UserRole assignment = new UserRole();
        assignment.setRole(role);
        return assignment;
    }
}
