package dev.lourencogabriel.inatead.organization.model;

import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "organization_memberships",
        indexes = @Index(
                name = "idx_organization_memberships_org_status",
                columnList = "organization_id,status"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_organization_memberships_period",
                columnNames = {"person_id", "organization_id", "membership_role", "start_date"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class OrganizationMembership extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "membership_role", length = 40, nullable = false)
    private String membershipRole;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;
}
