package dev.lourencogabriel.inatead.person;

import dev.lourencogabriel.inatead.person.controller.PersonController;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PersonTypeControllerTest {

    private PersonService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PersonService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PersonController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void shouldAcceptAndReturnMultipleTypesOnExistingPeopleEndpoint() throws Exception {
        when(service.create(any())).thenAnswer(invocation -> {
            PersonDtos.Request request = invocation.getArgument(0);
            return new PersonDtos.Response("person", null, request.fullName(), request.taxId(), null,
                    request.phoneNumber(), request.birthDate(), null, request.status(), request.personTypes(), null, null);
        });

        mvc.perform(post("/api/people").contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"INSTRUCTOR\",\"GUARDIAN\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.personTypes.length()").value(2));
        verify(service).create(argThat(request -> request.personTypes().equals(
                Set.of(PersonTypeCode.INSTRUCTOR, PersonTypeCode.GUARDIAN))));
    }

    @Test
    void shouldRejectUnsupportedOrNullPersonTypes() throws Exception {
        for (String types : new String[]{"[\"STAFF\"]", "[null]"}) {
            mvc.perform(post("/api/people").contentType(MediaType.APPLICATION_JSON).content(body(types)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void shouldFilterPeopleByTypeWhilePreservingPagination() throws Exception {
        mvc.perform(get("/api/people").param("personType", "GUARDIAN").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());
        verify(service).findByType(PersonTypeCode.GUARDIAN, PageRequest.of(2, 10));
    }

    @Test
    void shouldStillListPeopleWithoutATypeFilter() throws Exception {
        mvc.perform(get("/api/people")).andExpect(status().isOk());
        verify(service).findAll(PageRequest.of(0, 20));
    }

    @Test
    void shouldRejectUnknownTypeFiltersAsBadRequests() throws Exception {
        mvc.perform(get("/api/people").param("personType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private String body(String types) {
        return """
                {
                  "address": {"postalCode":"83200000","street":"Test Street","streetNumber":"10",
                    "district":"Centro","city":"Paranaguá","stateCode":"PR","countryCode":"BR"},
                  "fullName":"Person Types", "taxId":"12345678901", "phoneNumber":"41999999999",
                  "birthDate":"1990-01-01", "status":"ACTIVE", "personTypes": %s
                }
                """.formatted(types);
    }
}
