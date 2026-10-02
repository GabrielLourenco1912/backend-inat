package dev.lourencogabriel.inatead.organization;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.address.service.AddressService;
import dev.lourencogabriel.inatead.contract.repository.ContractRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerGuardianRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.learner.service.LearnerRelationshipPolicy;
import dev.lourencogabriel.inatead.organization.dto.OrganizationDtos;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.model.OrganizationTypeEntry;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationTypeRepository;
import dev.lourencogabriel.inatead.organization.service.OrganizationService;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationServiceTest {

    @Test
    void shouldNormalizeCnpjBeforeCheckingContractImmutability() {
        var organizations = mock(OrganizationRepository.class);
        var types = mock(OrganizationTypeRepository.class);
        var contracts = mock(ContractRepository.class);
        var policy = new LearnerRelationshipPolicy(
                mock(LearnerRepository.class), mock(LearnerGuardianRepository.class), contracts, Clock.systemUTC()
        );
        var service = new OrganizationService(organizations, types, mock(AddressService.class), policy);
        var organization = new Organization();
        organization.setId("organization-id");
        organization.setTaxId("12ABC34501DE35");
        when(organizations.findById("organization-id")).thenReturn(Optional.of(organization));
        when(organizations.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(types.findAllById(Set.of(OrganizationType.EMPLOYER)))
                .thenReturn(List.of(new OrganizationTypeEntry(OrganizationType.EMPLOYER)));
        when(contracts.existsByEmployer_Id("organization-id")).thenReturn(true);

        assertEquals("12ABC34501DE35", service.update("organization-id", request("12abc34501de35")).taxId());
        assertThrows(BusinessRuleException.class,
                () -> service.update("organization-id", request("12ABC34502DE35")));
        assertEquals("12ABC34501DE35", organization.getTaxId());
    }

    private OrganizationDtos.Request request(String taxId) {
        return new OrganizationDtos.Request(
                null, new AddressDtos.Request("83200000", "RUA TESTE", "1", null, "CENTRO", "PARANAGUÁ", "PR", "BR"),
                Set.of(OrganizationType.EMPLOYER), "EMPRESA TESTE", null, taxId, "contact@example.com", "41999999999",
                null, RecordStatus.ACTIVE
        );
    }
}
