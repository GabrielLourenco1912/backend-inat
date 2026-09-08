package dev.lourencogabriel.inatead.contract.model;

import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "contracts",
        indexes = {
                @Index(name = "idx_contracts_learner_period", columnList = "learner_id,start_date"),
                @Index(name = "idx_contracts_employer_status", columnList = "employer_id,status"),
                @Index(name = "idx_contracts_school_status", columnList = "school_id,status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Contract extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id", nullable = false)
    private Organization employer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private Organization school;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "monthly_salary", precision = 12, scale = 2, nullable = false)
    private BigDecimal monthlySalary;

    @Column(name = "weekly_workload_minutes", nullable = false)
    private int weeklyWorkloadMinutes;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ContractStatus status = ContractStatus.DRAFT;
}
