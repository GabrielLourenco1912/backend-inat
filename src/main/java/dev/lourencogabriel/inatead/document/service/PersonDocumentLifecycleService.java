package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.document.model.PersonDocument;
import dev.lourencogabriel.inatead.document.model.PersonDocumentStatusChangeReason;
import dev.lourencogabriel.inatead.document.model.PersonDocumentStatusHistory;
import dev.lourencogabriel.inatead.document.repository.PersonDocumentRepository;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PersonDocumentLifecycleService {

    private final PersonDocumentRepository documentRepository;

    @Transactional
    public boolean expireIfDue(String documentId, LocalDate today, Instant changedAt) {
        PersonDocument document = documentRepository.findByIdForUpdate(documentId).orElse(null);
        if (document == null
                || document.getVerificationStatus() != DocumentVerificationStatus.VERIFIED
                || document.getExpiresOn() == null
                || !document.getExpiresOn().isBefore(today)) {
            return false;
        }

        PersonDocumentStatusHistory history = new PersonDocumentStatusHistory();
        history.setPreviousStatus(DocumentVerificationStatus.VERIFIED);
        history.setNewStatus(DocumentVerificationStatus.EXPIRED);
        history.setChangeReason(PersonDocumentStatusChangeReason.AUTOMATIC_EXPIRATION);
        history.setChangedByUser(null);
        history.setChangedAt(changedAt);
        document.addStatusHistory(history);
        document.setVerificationStatus(DocumentVerificationStatus.EXPIRED);
        documentRepository.save(document);
        return true;
    }
}
