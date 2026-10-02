package dev.lourencogabriel.inatead.organization;

import dev.lourencogabriel.inatead.organization.controller.OrganizationController;
import dev.lourencogabriel.inatead.organization.dto.OrganizationDtos;
import dev.lourencogabriel.inatead.organization.service.OrganizationService;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationControllerValidationTest {

    private OrganizationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(OrganizationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrganizationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678000195", "12ABC34501DE35", "12abc34501de35"})
    void shouldAcceptNumericAndAlphanumericCnpjOnCreate(String taxId) throws Exception {
        mockMvc.perform(post("/api/organizations").contentType(MediaType.APPLICATION_JSON).content(request(taxId)))
                .andExpect(status().isCreated());

        var captured = ArgumentCaptor.forClass(OrganizationDtos.Request.class);
        verify(service).create(captured.capture());
        assertEquals(taxId, captured.getValue().taxId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678000195", "12ABC34501DE35", "12abc34501de35"})
    void shouldAcceptNumericAndAlphanumericCnpjOnUpdate(String taxId) throws Exception {
        mockMvc.perform(put("/api/organizations/organization-id")
                        .contentType(MediaType.APPLICATION_JSON).content(request(taxId)))
                .andExpect(status().isOk());

        var captured = ArgumentCaptor.forClass(OrganizationDtos.Request.class);
        verify(service).update(eq("organization-id"), captured.capture());
        assertEquals(taxId, captured.getValue().taxId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12ABC34501DEAB", "12ABC34501DE3", "12ABC34501DE355", "12.ABC.345/01DE-35", "12ÁBC34501DE35", "12ABC34501D 35"})
    void shouldRejectCnpjWithoutFourteenAllowedCharactersAndNumericCheckDigits(String taxId) throws Exception {
        for (var endpoint : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                post("/api/organizations"), put("/api/organizations/organization-id")
        }) {
            mockMvc.perform(endpoint.contentType(MediaType.APPLICATION_JSON).content(request(taxId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data[0].field").value("taxId"));
        }
        verifyNoInteractions(service);
    }

    private String request(String taxId) {
        return """
                {
                  "address": {
                    "postalCode": "83200000", "street": "RUA TESTE", "streetNumber": "1",
                    "district": "CENTRO", "city": "PARANAGUÁ", "stateCode": "PR", "countryCode": "BR"
                  },
                  "organizationTypes": ["EMPLOYER"], "legalName": "EMPRESA TESTE",
                  "taxId": "%s", "contactEmail": "contact@example.com", "phoneNumber": "41999999999",
                  "status": "ACTIVE"
                }
                """.formatted(taxId);
    }
}
