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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "organization_type_assignments",
            joinColumns = @JoinColumn(name = "organization_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "organization_type_code", nullable = false),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_organization_type_assignments",
                    columnNames = {"organization_id", "organization_type_code"}
            ),
            indexes = @Index(name = "idx_organization_type_assignments_type", columnList = "organization_type_code")
    )
    @org.hibernate.annotations.BatchSize(size = 100)
    private Set<OrganizationTypeEntry> organizationTypes = new HashSet<>();

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

    public boolean hasType(OrganizationType type) {
        return organizationTypes.stream().anyMatch(entry -> entry.getCode() == type);
    }

    public String getDisplayName() {
        return tradeName == null || tradeName.isBlank() ? legalName : tradeName;
    }
}
