package dev.lourencogabriel.inatead.document.model;

import dev.lourencogabriel.inatead.shared.persistence.CreatedEntity;
import dev.lourencogabriel.inatead.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "files",
        indexes = @Index(name = "idx_files_uploaded_by_user", columnList = "uploaded_by_user_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_files_storage_key", columnNames = "storage_key")
)
@Getter
@Setter
@NoArgsConstructor
public class StoredFile extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedByUser;

    @Column(name = "storage_key", length = 512, nullable = false)
    private String storageKey;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    @Column(name = "mime_type", length = 150, nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", length = 64, nullable = false)
    private String checksumSha256;
}
