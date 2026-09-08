package dev.lourencogabriel.inatead.notification.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.notification.service.NotificationRecipientService;
import dev.lourencogabriel.inatead.notification.service.NotificationService;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerValidationTest {

    private NotificationService notificationService;
    private NotificationRecipientService recipientService;
    private MockMvc notificationMockMvc;
    private MockMvc recipientMockMvc;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        recipientService = mock(NotificationRecipientService.class);
        DomainAuthorization authorization = mock(DomainAuthorization.class);
        notificationMockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationService, authorization))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        recipientMockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationRecipientController(
                        recipientService,
                        authorization
                ))
                .build();
    }

    @Test
    void shouldRequireAudienceWhenCreatingNotification() throws Exception {
        notificationMockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBodyWithout("audience")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("audience"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldRequireAtLeastOneChannelWhenCreatingNotification() throws Exception {
        notificationMockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBodyWithout("channels")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("channels"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldNotExposeStandaloneRecipientCreation() throws Exception {
        recipientMockMvc.perform(post("/api/notification-recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(recipientService);
    }

    @Test
    void shouldNotExposeStandaloneRecipientDeletion() throws Exception {
        recipientMockMvc.perform(delete("/api/notification-recipients/recipient-id"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(recipientService);
    }

    private String validBodyWithout(String property) {
        String audience = property.equals("audience")
                ? ""
                : "\"audience\": {\"type\": \"ALL\"},";
        String channels = property.equals("channels")
                ? ""
                : "\"channels\": [\"IN_APP\"],";
        return """
                {
                  %s
                  %s
                  "notificationType": "SYSTEM_MESSAGE",
                  "title": "Important message",
                  "message": "Please read this message.",
                  "priority": "NORMAL"
                }
                """.formatted(audience, channels);
    }
}
