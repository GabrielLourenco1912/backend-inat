package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.user.model.Role;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoleCatalogInitializer implements ApplicationRunner {

    private static final List<RoleDefinition> STANDARD_ROLES = List.of(
            new RoleDefinition(
                    SystemRoleCode.ADMIN.name(),
                    "Administrator",
                    "Full platform administration"
            ),
            new RoleDefinition(
                    SystemRoleCode.INSTRUCTOR.name(),
                    "Instructor",
                    "Assigned lesson management"
            ),
            new RoleDefinition(
                    SystemRoleCode.LEARNER.name(),
                    "Learner",
                    "Own learning experience"
            ),
            new RoleDefinition(
                    SystemRoleCode.EMPLOYER_MANAGER.name(),
                    "Employer manager",
                    "Scoped access to an employer organization"
            )
    );

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        STANDARD_ROLES.stream()
                .filter(definition -> !roleRepository.existsByCode(definition.code()))
                .map(this::toRole)
                .forEach(roleRepository::save);
    }

    private Role toRole(RoleDefinition definition) {
        Role role = new Role();
        role.setCode(definition.code());
        role.setName(definition.name());
        role.setDescription(definition.description());
        return role;
    }

    private record RoleDefinition(String code, String name, String description) {
    }
}
