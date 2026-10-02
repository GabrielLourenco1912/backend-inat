package dev.lourencogabriel.inatead.address;

import dev.lourencogabriel.inatead.address.dto.AddressDtos;
import dev.lourencogabriel.inatead.address.repository.AddressRepository;
import dev.lourencogabriel.inatead.organization.dto.OrganizationDtos;
import dev.lourencogabriel.inatead.organization.service.OrganizationService;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class AddressCompositionIntegrationTest {

    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonService personService;
    @Autowired
    private OrganizationService organizationService;

    @Test
    void shouldCreateUpdateAndDeleteAddressWithPerson() {
        long initialAddressCount = addressRepository.count();
        PersonDtos.Response created = personService.create(new PersonDtos.Request(
                address("First Street"),
                "Address Owner",
                "74185296300",
                null,
                "41999999999",
                LocalDate.of(2000, 1, 1),
                null,
                RecordStatus.ACTIVE,
                Set.of()
        ));
        String addressId = created.address().id();

        assertEquals(initialAddressCount + 1, addressRepository.count());

        PersonDtos.Response updated = personService.update(created.id(), new PersonDtos.Request(
                address("Updated Street"),
                "Address Owner",
                "74185296300",
                null,
                "41999999999",
                LocalDate.of(2000, 1, 1),
                null,
                RecordStatus.ACTIVE,
                Set.of()
        ));

        assertEquals(addressId, updated.address().id());
        assertEquals("Updated Street", updated.address().street());
        assertEquals(initialAddressCount + 1, addressRepository.count());

        personService.delete(created.id());

        assertFalse(addressRepository.existsById(addressId));
        assertEquals(initialAddressCount, addressRepository.count());
    }

    @Test
    void shouldCreateUpdateAndDeleteAddressWithOrganization() {
        long initialAddressCount = addressRepository.count();
        OrganizationDtos.Response created = organizationService.create(new OrganizationDtos.Request(
                null,
                address("Company Street"),
                Set.of(OrganizationType.EMPLOYER, OrganizationType.SCHOOL),
                "Address Company Ltda",
                "Address Company",
                "12345678000190",
                "contact@example.com",
                "41999999999",
                20,
                RecordStatus.ACTIVE
        ));
        String addressId = created.address().id();

        assertEquals(Set.of(OrganizationType.EMPLOYER, OrganizationType.SCHOOL), created.organizationTypes());
        assertEquals(created.organizationTypes(), organizationService.findById(created.id()).organizationTypes());

        assertEquals(initialAddressCount + 1, addressRepository.count());

        OrganizationDtos.Response updated = organizationService.update(
                created.id(),
                new OrganizationDtos.Request(
                        null,
                        address("New Company Street"),
                        Set.of(OrganizationType.SCHOOL),
                        "Address Company Ltda",
                        "Address Company",
                        "12345678000190",
                        "contact@example.com",
                        "41999999999",
                        20,
                        RecordStatus.ACTIVE
                )
        );

        assertEquals(addressId, updated.address().id());
        assertEquals(Set.of(OrganizationType.SCHOOL), updated.organizationTypes());
        assertEquals("New Company Street", updated.address().street());
        assertEquals(initialAddressCount + 1, addressRepository.count());

        organizationService.delete(created.id());

        assertFalse(addressRepository.existsById(addressId));
        assertEquals(initialAddressCount, addressRepository.count());
    }

    @Test
    void shouldPersistUppercaseAlphanumericCnpjAndRejectCaseInsensitiveDuplicates() {
        var request = new OrganizationDtos.Request(
                null, address("Alphanumeric Street"), Set.of(OrganizationType.EMPLOYER),
                "Alphanumeric Company", null, "12abc34501de35", "alpha@example.com", "41999999999",
                null, RecordStatus.ACTIVE
        );
        var created = organizationService.create(request);
        try {
            assertEquals("12ABC34501DE35", created.taxId());
            assertEquals("12ABC34501DE35", organizationService.findById(created.id()).taxId());
            var updated = organizationService.update(created.id(), request);
            assertEquals(created.address().id(), updated.address().id());
            assertEquals("12ABC34501DE35", updated.taxId());
            assertThrows(ResourceConflictException.class, () -> organizationService.create(new OrganizationDtos.Request(
                    null, address("Duplicate Street"), Set.of(OrganizationType.SCHOOL),
                    "Duplicate Company", null, "12AbC34501dE35", "duplicate@example.com", "41999999999",
                    null, RecordStatus.ACTIVE
            )));
        } finally {
            organizationService.delete(created.id());
        }
    }

    @Test
    void shouldRejectOrganizationWithoutTypesInService() {
        assertThrows(BusinessRuleException.class,
                () -> organizationService.create(new OrganizationDtos.Request(
                        null,
                        address("Untyped Street"),
                        Set.of(),
                        "Untyped Company Ltda",
                        null,
                        "22345678000190",
                        "contact-untyped@example.com",
                        "41999999999",
                        null,
                        RecordStatus.ACTIVE
                )));
    }

    private AddressDtos.Request address(String street) {
        return new AddressDtos.Request(
                "80000000",
                street,
                "100",
                null,
                "Center",
                "Curitiba",
                "PR",
                "BR"
        );
    }
}
