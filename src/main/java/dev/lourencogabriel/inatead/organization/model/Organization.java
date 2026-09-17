package dev.lourencogabriel.inatead.organization.model;

import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
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

@Entity
@Table(
        name = "organizations",
        indexes = {
                @Index(name = "idx_organizations_parent", columnList = "parent_organization_id"),
                @Index(name = "idx_organizations_address", columnList = "address_id")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_organizations_tax_id", columnNames = "tax_id")
)
@Getter
@Setter
@NoArgsConstructor
public class Organization extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_organization_id")
    private Organization parentOrganization;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JoinColumn(name = "address_id", nullable = false, unique = true)
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", length = 30, nullable = false)
    private OrganizationType organizationType;

    @Column(name = "legal_name", length = 180, nullable = false)
    private String legalName;

    @Column(name = "trade_name", length = 180)
    private String tradeName;

    @Column(name = "tax_id", length = 14, nullable = false)
    private String taxId;

    @Column(name = "contact_email", length = 254, nullable = false)
    private String contactEmail;

    @Column(name = "phone_number", length = 20, nullable = false)
    private String phoneNumber;

    @Column(name = "attendance_closing_day")
    private Integer attendanceClosingDay;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;
}
