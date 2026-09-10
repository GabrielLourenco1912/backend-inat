package dev.lourencogabriel.inatead.cohort.model;

import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "cohorts",
        indexes = @Index(name = "idx_cohorts_status", columnList = "status"),
        uniqueConstraints = @UniqueConstraint(name = "uk_cohorts_code", columnNames = "code")
)
@Getter
@Setter
@NoArgsConstructor
public class Cohort extends AuditedEntity {

    @Column(length = 40, nullable = false)
    private String code;

    @Column(length = 120, nullable = false)
    private String name;

    @Column(name = "default_weekday", nullable = false)
    private int defaultWeekday;

    @Column(name = "shift_code", length = 20, nullable = false)
    private String shiftCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_learners")
    private Integer maxLearners;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private CohortStatus status = CohortStatus.PLANNED;
}
