package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.document.repository.PersonDocumentRepository;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "api.document.lifecycle.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PersonDocumentLifecycleScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            PersonDocumentLifecycleScheduler.class
    );

    private final PersonDocumentRepository documentRepository;
    private final PersonDocumentLifecycleService lifecycleService;
    private final Clock clock;
    private final int batchSize;

    public PersonDocumentLifecycleScheduler(
            PersonDocumentRepository documentRepository,
            PersonDocumentLifecycleService lifecycleService,
            Clock clock,
            @Value("${api.document.lifecycle.batch-size:200}") int batchSize
    ) {
        this.documentRepository = documentRepository;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
    }

    @Scheduled(fixedDelayString = "${api.document.lifecycle.interval:60000}")
    public void expireDocuments() {
        LocalDate today = BusinessTime.today(clock);
        Instant changedAt = Instant.now(clock);
        List<String> candidates = documentRepository.findExpirationCandidateIds(
                DocumentVerificationStatus.VERIFIED,
                today,
                PageRequest.of(0, batchSize)
        );

        int expired = 0;
        for (String documentId : candidates) {
            try {
                if (lifecycleService.expireIfDue(documentId, today, changedAt)) {
                    expired++;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Could not expire person document {}", documentId, exception);
            }
        }
        if (expired > 0) {
            LOGGER.info("Person document lifecycle synchronized: expired={}", expired);
        }
    }
}
