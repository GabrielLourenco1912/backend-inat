package dev.lourencogabriel.inatead.contact.controller;

import dev.lourencogabriel.inatead.contact.service.ContactMessageService;
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

class ContactMessageControllerValidationTest {

    private ContactMessageService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ContactMessageService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContactMessageController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRejectInvalidPublicMessage() throws Exception {
        mockMvc.perform(post("/api/contact-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "email": "invalid",
                                  "contactType": "YOUTH_INTERESTED",
                                  "message": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Error"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectUnknownContactType() throws Exception {
        mockMvc.perform(post("/api/contact-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Maria Silva",
                                  "email": "maria@example.com",
                                  "contactType": "UNKNOWN",
                                  "message": "Quero saber mais."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is invalid"));

        verifyNoInteractions(service);
    }
}
