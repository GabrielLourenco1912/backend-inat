package dev.lourencogabriel.inatead.document.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "api.storage.validation")
@Getter
@Setter
public class FileValidationProperties {

    private Set<String> documentExtensions = new LinkedHashSet<>(Set.of(
            "pdf", "png", "jpg", "jpeg", "webp"
    ));

    private Set<String> activityExtensions = new LinkedHashSet<>(Set.of(
            "pdf", "png", "jpg", "jpeg", "webp", "gif",
            "txt", "csv", "rtf", "docx", "xlsx", "pptx",
            "odt", "ods", "odp", "mp3", "mp4", "webm", "wav"
    ));

    private Set<String> submissionExtensions = new LinkedHashSet<>(Set.of(
            "pdf", "png", "jpg", "jpeg", "webp", "gif",
            "txt", "csv", "rtf", "docx", "xlsx", "pptx",
            "odt", "ods", "odp", "mp3", "mp4", "webm", "wav"
    ));

    public Set<String> allowedExtensions(FileUploadPurpose purpose) {
        return switch (purpose) {
            case PERSON_DOCUMENT, CONTRACT_DOCUMENT -> documentExtensions;
            case ACTIVITY_MATERIAL -> activityExtensions;
            case SUBMISSION_ATTACHMENT -> submissionExtensions;
        };
    }
}
