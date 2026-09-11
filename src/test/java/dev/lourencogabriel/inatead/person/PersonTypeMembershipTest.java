package dev.lourencogabriel.inatead.person;

import dev.lourencogabriel.inatead.organization.dto.OrganizationMembershipDtos;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.model.OrganizationMembership;
import dev.lourencogabriel.inatead.organization.repository.OrganizationMembershipRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.organization.service.OrganizationMembershipService;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonType;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonTypeMembershipTest {

    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PersonRepository personRepository;
    @InjectMocks private OrganizationMembershipService service;
    private Person person;

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setId("person");
        Organization organization = new Organization();
        organization.setId("organization");
        when(personRepository.findByIdForUpdate("person")).thenReturn(Optional.of(person));
        when(organizationRepository.findById("organization")).thenReturn(Optional.of(organization));
    }

    @Test
    void shouldRejectAssigningManagerWithoutCorrespondingType() {
        person.getPersonTypes().add(new PersonType(PersonTypeCode.INSTRUCTOR));
        assertThrows(BusinessRuleException.class, () -> service.create(request("EMPLOYER_MANAGER")));
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void shouldAcceptManagerWithCorrespondingType() {
        person.getPersonTypes().add(new PersonType(PersonTypeCode.EMPLOYER_MANAGER));
        when(membershipRepository.save(any(OrganizationMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals("EMPLOYER_MANAGER", service.create(request(" employer_manager ")).membershipRole());
    }

    @Test
    void shouldPreserveFreeBusinessFunctionsWithoutGrantingAccessRoles() {
        when(membershipRepository.save(any(OrganizationMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals("HR_CONTACT", service.create(request("HR_CONTACT")).membershipRole());
    }

    private OrganizationMembershipDtos.Request request(String role) {
        return new OrganizationMembershipDtos.Request("person", "organization", role, null,
                LocalDate.of(2026, 1, 1), null, RecordStatus.ACTIVE);
    }
}
