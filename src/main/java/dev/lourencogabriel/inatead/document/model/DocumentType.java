package dev.lourencogabriel.inatead.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "document_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_types_code", columnNames = "code")
)
@Getter
@Setter
@NoArgsConstructor
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(length = 50, nullable = false)
    private String code;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private DocumentTypeScope scope = DocumentTypeScope.BOTH;
}
