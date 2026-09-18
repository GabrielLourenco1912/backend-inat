package dev.lourencogabriel.inatead.contact.service;

import dev.lourencogabriel.inatead.contact.dto.ContactMessageDtos;
import dev.lourencogabriel.inatead.contact.model.ContactMessage;
import dev.lourencogabriel.inatead.contact.model.ContactMessageStatus;
import dev.lourencogabriel.inatead.contact.model.ContactMessageType;
import dev.lourencogabriel.inatead.contact.repository.ContactMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactMessageServiceTest {

    @Mock
    private ContactMessageRepository repository;

    private ContactMessageService service;

    @BeforeEach
    void setUp() {
        service = new ContactMessageService(repository);
    }

    @Test
    void shouldNormalizePublicMessageBeforePersisting() {
        when(repository.save(any(ContactMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactMessageDtos.Response response = service.create(new ContactMessageDtos.CreateRequest(
                "  Maria Silva  ",
                "  MARIA@EXAMPLE.COM  ",
                "   ",
                ContactMessageType.YOUTH_INTERESTED,
                "  Quero saber mais sobre os cursos.  "
        ));

        ArgumentCaptor<ContactMessage> captor = ArgumentCaptor.forClass(ContactMessage.class);
        verify(repository).save(captor.capture());
        ContactMessage saved = captor.getValue();
        assertEquals("Maria Silva", saved.getName());
        assertEquals("maria@example.com", saved.getEmail());
        assertNull(saved.getPhone());
        assertEquals("Quero saber mais sobre os cursos.", saved.getMessage());
        assertEquals(ContactMessageStatus.NEW, response.status());
    }

    @Test
    void shouldUpdateMessageStatus() {
        ContactMessage message = new ContactMessage();
        message.setId("message-id");
        message.setName("Maria Silva");
        message.setEmail("maria@example.com");
        message.setContactType(ContactMessageType.YOUTH_INTERESTED);
        message.setMessage("Quero saber mais.");
        when(repository.findById("message-id")).thenReturn(Optional.of(message));
        when(repository.save(message)).thenReturn(message);

        ContactMessageDtos.Response response = service.updateStatus(
                "message-id",
                new ContactMessageDtos.StatusRequest(ContactMessageStatus.READ)
        );

        assertEquals(ContactMessageStatus.READ, response.status());
        verify(repository).save(message);
    }
}
