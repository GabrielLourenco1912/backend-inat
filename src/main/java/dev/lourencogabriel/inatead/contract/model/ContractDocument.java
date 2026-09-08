package dev.lourencogabriel.inatead.contract.model;

import dev.lourencogabriel.inatead.document.model.DocumentType;
import dev.lourencogabriel.inatead.document.model.StoredFile;
import dev.lourencogabriel.inatead.shared.persistence.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "contract_documents",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contract_documents_file", columnNames = "file_id"),
                @UniqueConstraint(
                        name = "uk_contract_documents_version",
                        columnNames = {"contract_id", "document_type_id", "version_number"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ContractDocument extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentType documentType;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;
}
