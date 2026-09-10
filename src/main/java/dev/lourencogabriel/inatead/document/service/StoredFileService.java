package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.document.dto.StoredFileDtos;
import dev.lourencogabriel.inatead.document.model.StoredFile;
import dev.lourencogabriel.inatead.document.repository.StoredFileRepository;
import dev.lourencogabriel.inatead.document.storage.FileContentValidator;
import dev.lourencogabriel.inatead.document.storage.FileStorageException;
import dev.lourencogabriel.inatead.document.storage.FileSystemStorage;
import dev.lourencogabriel.inatead.document.storage.FileTooLargeException;
import dev.lourencogabriel.inatead.document.storage.FileUploadPurpose;
import dev.lourencogabriel.inatead.document.storage.StoredBinary;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class StoredFileService {

    private final StoredFileRepository fileRepository;
    private final UserRepository userRepository;
    private final FileSystemStorage storage;
    private final FileContentValidator contentValidator;
    private final StoredFileCleanupService cleanupService;

    @Transactional(propagation = Propagation.MANDATORY)
    public StoredFile store(
            MultipartFile upload,
            String actorUserId,
            FileUploadPurpose purpose
    ) {
        validateUpload(upload);
        requireTransactionSynchronization();
        var uploadedBy = EntityLookup.require(userRepository, actorUserId, "User");
        String originalName = normalizeOriginalName(upload.getOriginalFilename());
        String declaredMimeType = normalizeMimeType(upload.getContentType());
        AtomicReference<String> detectedMimeType = new AtomicReference<>();

        StoredBinary binary;
        try {
            InputStream content = upload.getInputStream();
            binary = storage.store(content, temporaryFile -> detectedMimeType.set(
                    contentValidator.validate(
                            temporaryFile,
                            originalName,
                            declaredMimeType,
                            purpose
                    )
            ));
        } catch (IOException ex) {
            throw new FileStorageException("Could not read uploaded file content", ex);
        }
        registerRollbackCleanup(binary.storageKey(), binary.newlyCreated());

        StoredFile file = new StoredFile();
        file.setUploadedByUser(uploadedBy);
        file.setStorageKey(binary.storageKey());
        file.setOriginalName(originalName);
        file.setMimeType(detectedMimeType.get());
        file.setSizeBytes(binary.sizeBytes());
        file.setChecksumSha256(binary.checksumSha256());
        return fileRepository.save(file);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public StoredFileDownload download(StoredFile file) {
        return new StoredFileDownload(
                storage.load(file.getStorageKey(), file.getSizeBytes()),
                file.getOriginalName(),
                file.getMimeType(),
                file.getSizeBytes()
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(StoredFile file) {
        requireTransactionSynchronization();
        String storageKey = file.getStorageKey();
        fileRepository.delete(file);
        registerCommittedDeletion(storageKey);
    }

    public StoredFileDtos.Response toResponse(StoredFile file) {
        return new StoredFileDtos.Response(
                file.getId(),
                file.getUploadedByUser() == null ? null : file.getUploadedByUser().getUlid(),
                file.getOriginalName(),
                file.getMimeType(),
                file.getSizeBytes(),
                file.getChecksumSha256(),
                file.getCreatedAt()
        );
    }

    private void validateUpload(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new BusinessRuleException("File content is required");
        }
        if (upload.getSize() > storage.maximumSizeBytes()) {
            throw new FileTooLargeException(storage.maximumSizeBytes());
        }
        normalizeOriginalName(upload.getOriginalFilename());
        normalizeMimeType(upload.getContentType());
    }

    private String normalizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessRuleException("File name is required");
        }
        String normalized = StringUtils.cleanPath(originalName.trim()).replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isBlank() || normalized.equals(".") || normalized.equals("..")) {
            throw new BusinessRuleException("File name is invalid");
        }
        if (normalized.length() > 255) {
            throw new BusinessRuleException("File name must have at most 255 characters");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessRuleException("File name contains invalid characters");
        }
        return normalized;
    }

    private String normalizeMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            String normalized = MediaType.parseMediaType(contentType).toString();
            if (normalized.length() > 150) {
                throw new BusinessRuleException("File content type must have at most 150 characters");
            }
            return normalized;
        } catch (org.springframework.http.InvalidMediaTypeException ex) {
            throw new BusinessRuleException("File content type is invalid");
        }
    }

    private void registerRollbackCleanup(String storageKey, boolean newlyCreated) {
        if (!newlyCreated) {
            return;
        }
        requireTransactionSynchronization();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    cleanupService.deleteBinaryIfUnreferenced(storageKey, "rolled-back upload");
                }
            }
        });
    }

    private void registerCommittedDeletion(String storageKey) {
        requireTransactionSynchronization();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    cleanupService.deleteBinaryIfUnreferenced(storageKey, "committed deletion");
                }
            }
        });
    }

    private void requireTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Stored files must be managed inside a transaction");
        }
    }

}
