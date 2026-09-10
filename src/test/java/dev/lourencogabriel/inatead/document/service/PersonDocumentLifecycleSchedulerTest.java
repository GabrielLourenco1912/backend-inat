package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.document.repository.PersonDocumentRepository;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonDocumentLifecycleSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Mock
    private PersonDocumentRepository repository;
    @Mock
    private PersonDocumentLifecycleService lifecycleService;

    @Test
    void shouldProcessExpirationCandidatesUsingConfiguredBatch() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        LocalDate today = LocalDate.of(2026, 9, 9);
        when(repository.findExpirationCandidateIds(
                DocumentVerificationStatus.VERIFIED,
                today,
                PageRequest.of(0, 25)
        )).thenReturn(List.of("document-1", "document-2"));
        when(lifecycleService.expireIfDue("document-1", today, NOW)).thenReturn(true);

        PersonDocumentLifecycleScheduler scheduler = new PersonDocumentLifecycleScheduler(
                repository,
                lifecycleService,
                clock,
                25
        );
        scheduler.expireDocuments();

        verify(lifecycleService).expireIfDue("document-1", today, NOW);
        verify(lifecycleService).expireIfDue("document-2", today, NOW);
    }
}
