package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.document.model.PersonDocument;
import dev.lourencogabriel.inatead.document.model.PersonDocumentStatusChangeReason;
import dev.lourencogabriel.inatead.document.repository.PersonDocumentRepository;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonDocumentLifecycleServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Mock
    private PersonDocumentRepository repository;

    private PersonDocumentLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new PersonDocumentLifecycleService(repository);
    }

    @Test
    void shouldExpireDueDocumentAndRecordAutomaticHistory() {
        PersonDocument document = document(DocumentVerificationStatus.VERIFIED, TODAY.minusDays(1));
        when(repository.findByIdForUpdate("document-1")).thenReturn(Optional.of(document));

        assertTrue(service.expireIfDue("document-1", TODAY, NOW));

        assertEquals(DocumentVerificationStatus.EXPIRED, document.getVerificationStatus());
        assertEquals(1, document.getStatusHistory().size());
        var history = document.getStatusHistory().getFirst();
        assertEquals(DocumentVerificationStatus.VERIFIED, history.getPreviousStatus());
        assertEquals(DocumentVerificationStatus.EXPIRED, history.getNewStatus());
        assertEquals(
                PersonDocumentStatusChangeReason.AUTOMATIC_EXPIRATION,
                history.getChangeReason()
        );
        assertNull(history.getChangedByUser());
        assertEquals(NOW, history.getChangedAt());
        verify(repository).save(document);
    }

    @Test
    void shouldNotExpireDocumentOnItsLastValidDay() {
        PersonDocument document = document(DocumentVerificationStatus.VERIFIED, TODAY);
        when(repository.findByIdForUpdate("document-1")).thenReturn(Optional.of(document));

        assertFalse(service.expireIfDue("document-1", TODAY, NOW));

        assertEquals(DocumentVerificationStatus.VERIFIED, document.getVerificationStatus());
        verify(repository, never()).save(document);
    }

    private PersonDocument document(DocumentVerificationStatus status, LocalDate expiresOn) {
        PersonDocument document = new PersonDocument();
        document.setId("document-1");
        document.setVerificationStatus(status);
        document.setExpiresOn(expiresOn);
        return document;
    }
}
