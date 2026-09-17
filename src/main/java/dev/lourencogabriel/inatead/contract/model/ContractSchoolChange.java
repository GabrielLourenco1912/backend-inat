package dev.lourencogabriel.inatead.contract.model;

import dev.lourencogabriel.inatead.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "contract_school_changes",
        indexes = @Index(
                name = "idx_contract_school_changes_contract_time",
                columnList = "contract_id,changed_at"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ContractSchoolChange extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, updatable = false)
    private Contract contract;

    @Column(name = "previous_school_id", length = 26, updatable = false)
    private String previousSchoolId;

    @Column(name = "new_school_id", length = 26, updatable = false)
    private String newSchoolId;

    @Column(name = "changed_by_user_id", length = 26, updatable = false)
    private String changedByUserId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
