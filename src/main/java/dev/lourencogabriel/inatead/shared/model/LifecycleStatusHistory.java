package dev.lourencogabriel.inatead.shared.model;

import dev.lourencogabriel.inatead.shared.persistence.BaseEntity;
import dev.lourencogabriel.inatead.user.model.User;
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

import java.time.Instant;

@Entity
@Table(
        name = "lifecycle_status_history",
        indexes = @Index(
                name = "idx_lifecycle_status_history_aggregate_time",
                columnList = "aggregate_type,aggregate_id,changed_at"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class LifecycleStatusHistory extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", length = 30, nullable = false, updatable = false)
    private LifecycleAggregateType aggregateType;

    @Column(name = "aggregate_id", length = 26, nullable = false, updatable = false)
    private String aggregateId;

    @Column(name = "previous_status", length = 30, updatable = false)
    private String previousStatus;

    @Column(name = "new_status", length = 30, nullable = false, updatable = false)
    private String newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_reason", length = 40, nullable = false, updatable = false)
    private LifecycleChangeReason changeReason;

    @Column(name = "reason_detail", length = 500, updatable = false)
    private String reasonDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", updatable = false)
    private User changedByUser;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
