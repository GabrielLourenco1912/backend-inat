package dev.lourencogabriel.inatead.lesson.model;

import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationType;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "lesson_participants",
        indexes = {
                @Index(name = "idx_lesson_participants_learner", columnList = "learner_id,lesson_id"),
                @Index(
                        name = "idx_lesson_participants_source_enrollment",
                        columnList = "source_enrollment_id"
                )
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_lesson_participants_lesson_learner",
                columnNames = {"lesson_id", "learner_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class LessonParticipant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_enrollment_id")
    private CohortEnrollment sourceEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private User assignedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_type", length = 20, nullable = false)
    private ParticipationType participationType = ParticipationType.REGULAR;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ParticipationStatus status = ParticipationStatus.EXPECTED;

    @Column(name = "assignment_reason", length = 500)
    private String assignmentReason;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
