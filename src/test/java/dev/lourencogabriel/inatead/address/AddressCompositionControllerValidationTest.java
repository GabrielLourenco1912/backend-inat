package dev.lourencogabriel.inatead.address;

import dev.lourencogabriel.inatead.organization.controller.OrganizationController;
import dev.lourencogabriel.inatead.organization.service.OrganizationService;
import dev.lourencogabriel.inatead.person.controller.PersonController;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AddressCompositionControllerValidationTest {

    private PersonService personService;
    private OrganizationService organizationService;
    private MockMvc personMockMvc;
    private MockMvc organizationMockMvc;

    @BeforeEach
    void setUp() {
        personService = mock(PersonService.class);
        organizationService = mock(OrganizationService.class);
        personMockMvc = MockMvcBuilders
                .standaloneSetup(new PersonController(personService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        organizationMockMvc = MockMvcBuilders
                .standaloneSetup(new OrganizationController(organizationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRequireAddressWhenCreatingPerson() throws Exception {
        personMockMvc.perform(post("/api/people")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Joao da Silva",
                                  "taxId": "12345678909",
                                  "contactEmail": "joao@example.com",
                                  "phoneNumber": "41999999999",
                                  "birthDate": "2000-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("address"));

        verifyNoInteractions(personService);
    }

    @Test
    void shouldRequireAddressWhenCreatingOrganization() throws Exception {
        organizationMockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationType": "EMPLOYER",
                                  "legalName": "Test Company Ltda",
                                  "taxId": "12345678000190",
                                  "contactEmail": "contact@example.com",
                                  "phoneNumber": "41999999999",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("address"));

        verifyNoInteractions(organizationService);
    }
}
