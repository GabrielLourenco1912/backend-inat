package dev.lourencogabriel.inatead.learner.controller;

import dev.lourencogabriel.inatead.learner.service.LearnerOnboardingService;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearnerFromPersonControllerValidationTest {

    private LearnerOnboardingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LearnerOnboardingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LearnerFromPersonController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldValidateProfileAndGuardians() throws Exception {
        mockMvc.perform(post("/api/people/person-id/learner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "learner": {"registrationNumber": "", "hasCompletedHighSchool": false, "status": "ACTIVE"},
                                  "guardians": [{"guardianPersonId": "guardian", "relationshipType": "", "legalGuardian": false, "primaryContact": false}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[*].field", hasItems(
                        "learner.registrationNumber", "guardians[0].relationshipType"
                )));

        verifyNoInteractions(service);
    }

    @Test
    void shouldPassPersonIdAndProfileToService() throws Exception {
        mockMvc.perform(post("/api/people/person-id/learner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "learner": {
                                    "registrationNumber": "MAT-001",
                                    "hasCompletedHighSchool": true,
                                    "status": "ACTIVE"
                                  },
                                  "guardians": []
                                }
                                """))
                .andExpect(status().isCreated());

        verify(service).fromPerson(org.mockito.ArgumentMatchers.eq("person-id"),
                org.mockito.ArgumentMatchers.argThat(request ->
                        request.learner().registrationNumber().equals("MAT-001")
                                && request.guardians().isEmpty()));
    }
}
