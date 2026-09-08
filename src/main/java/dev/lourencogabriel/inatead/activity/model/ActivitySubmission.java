package dev.lourencogabriel.inatead.activity.model;

import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "activity_submissions",
        indexes = @Index(
                name = "idx_activity_submissions_learner_status",
                columnList = "learner_id,status"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_activity_submissions_activity_learner",
                columnNames = {"activity_id", "learner_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ActivitySubmission extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by_user_id")
    private User gradedByUser;

    @Column(name = "text_answer", columnDefinition = "text")
    private String textAnswer;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(precision = 7, scale = 2)
    private BigDecimal score;

    @Column(columnDefinition = "text")
    private String feedback;

    @Column(name = "graded_at")
    private Instant gradedAt;
}
