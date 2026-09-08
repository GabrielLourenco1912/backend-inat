package dev.lourencogabriel.inatead.document.model;

import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
import dev.lourencogabriel.inatead.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "person_documents",
        indexes = @Index(
                name = "idx_person_documents_person_type",
                columnList = "person_id,document_type_id"
        ),
        uniqueConstraints = @UniqueConstraint(name = "uk_person_documents_file", columnNames = "file_id")
)
@Getter
@Setter
@NoArgsConstructor
public class PersonDocument extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentType documentType;

    @Column(name = "document_number", length = 80)
    private String documentNumber;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20, nullable = false)
    private DocumentVerificationStatus verificationStatus = DocumentVerificationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedByUser;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
