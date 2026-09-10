package dev.lourencogabriel.inatead.learner.controller;

import dev.lourencogabriel.inatead.learner.service.LearnerOnboardingService;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearnerOnboardingControllerValidationTest {

    private LearnerOnboardingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LearnerOnboardingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LearnerOnboardingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldValidateNestedOnboardingData() throws Exception {
        mockMvc.perform(post("/api/learner-onboardings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "person": {
                                    "address": {
                                      "postalCode": "invalid",
                                      "street": "Test Street",
                                      "streetNumber": "100",
                                      "district": "Center",
                                      "city": "Curitiba",
                                      "stateCode": "PR",
                                      "countryCode": "BR"
                                    },
                                    "fullName": "Joao da Silva",
                                    "taxId": "invalid",
                                    "contactEmail": "invalid-email",
                                    "phoneNumber": "41999999999",
                                    "birthDate": "2010-01-01"
                                  },
                                  "learner": {
                                    "registrationNumber": "",
                                    "hasCompletedHighSchool": false,
                                    "status": "ACTIVE"
                                  },
                                  "guardians": [
                                    {
                                      "guardianPersonId": "guardian",
                                      "relationshipType": "",
                                      "legalGuardian": true,
                                      "primaryContact": true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"))
                .andExpect(jsonPath(
                        "$.data[*].field",
                        hasItems(
                                "person.taxId",
                                "person.contactEmail",
                                "person.address.postalCode",
                                "learner.registrationNumber",
                                "guardians[0].relationshipType"
                        )
                ));

        verifyNoInteractions(service);
    }

    @Test
    void shouldAcceptValidOnboardingData() throws Exception {
        mockMvc.perform(post("/api/learner-onboardings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "person": {
                                    "address": {
                                      "postalCode": "80000000",
                                      "street": "Test Street",
                                      "streetNumber": "100",
                                      "district": "Center",
                                      "city": "Curitiba",
                                      "stateCode": "PR",
                                      "countryCode": "BR"
                                    },
                                    "fullName": "Joao da Silva",
                                    "taxId": "12345678909",
                                    "contactEmail": "joao@example.com",
                                    "phoneNumber": "41999999999",
                                    "birthDate": "2000-01-01",
                                    "status": "ACTIVE"
                                  },
                                  "learner": {
                                    "registrationNumber": "MAT-001",
                                    "hasCompletedHighSchool": true,
                                    "status": "ACTIVE"
                                  },
                                  "guardians": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Learner onboarding completed"));

        verify(service).onboard(any());
    }
}
