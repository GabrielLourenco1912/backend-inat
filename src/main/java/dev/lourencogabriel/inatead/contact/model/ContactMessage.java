package dev.lourencogabriel.inatead.contact.model;

import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "contact_messages",
        indexes = {
                @Index(name = "idx_contact_messages_status_created", columnList = "status, created_at"),
                @Index(name = "idx_contact_messages_type_created", columnList = "contact_type, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ContactMessage extends AuditedEntity {

    @Column(length = 150, nullable = false)
    private String name;

    @Column(length = 254, nullable = false)
    private String email;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", length = 32, nullable = false)
    private ContactMessageType contactType;

    @Column(columnDefinition = "text", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ContactMessageStatus status = ContactMessageStatus.NEW;
}
