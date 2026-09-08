package dev.lourencogabriel.inatead.cohort.model;

import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
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
        name = "cohort_enrollments",
        indexes = @Index(
                name = "idx_cohort_enrollments_roster",
                columnList = "cohort_id,status"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cohort_enrollments_period",
                columnNames = {"contract_id", "cohort_id", "start_date"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class CohortEnrollment extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private EnrollmentStatus status = EnrollmentStatus.PENDING;
}
