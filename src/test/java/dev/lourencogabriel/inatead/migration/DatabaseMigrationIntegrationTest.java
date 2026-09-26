package dev.lourencogabriel.inatead.migration;

import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonTypeRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationTypeRepository;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.user.model.Role;
import dev.lourencogabriel.inatead.user.repository.RoleRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationIntegrationTest {

    @Autowired private Flyway flyway;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PersonTypeRepository personTypeRepository;
    @Autowired private OrganizationTypeRepository organizationTypeRepository;

    @Test
    void shouldApplyCurrentSchemaAndSeedSystemCatalogs() {
        assertEquals("4", flyway.info().current().getVersion().toString());
        assertEquals(Map.of(
                "ADMIN", "Administrator",
                "INSTRUCTOR", "Instructor",
                "LEARNER", "Learner",
                "EMPLOYER_MANAGER", "Employer manager"
        ), roleRepository.findAll().stream().collect(Collectors.toMap(Role::getCode, Role::getName)));
        assertEquals(Set.of(PersonTypeCode.values()), personTypeRepository.findAll().stream()
                .map(personType -> personType.getCode())
                .collect(Collectors.toSet()));
        assertEquals(Set.of(OrganizationType.values()), organizationTypeRepository.findAll().stream()
                .map(type -> type.getCode())
                .collect(Collectors.toSet()));
    }
}
