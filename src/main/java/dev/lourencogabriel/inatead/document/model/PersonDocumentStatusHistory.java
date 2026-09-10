package dev.lourencogabriel.inatead.document.model;

import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import dev.lourencogabriel.inatead.shared.persistence.BaseEntity;
import dev.lourencogabriel.inatead.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "person_document_status_history",
        indexes = @Index(
                name = "idx_person_document_status_history_document_time",
                columnList = "person_document_id,changed_at"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class PersonDocumentStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_document_id", nullable = false)
    private PersonDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private DocumentVerificationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20, nullable = false)
    private DocumentVerificationStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_reason", length = 40, nullable = false)
    private PersonDocumentStatusChangeReason changeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedByUser;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
