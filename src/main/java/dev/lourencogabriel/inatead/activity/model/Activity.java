package dev.lourencogabriel.inatead.activity.model;

import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "activities",
        indexes = {
                @Index(name = "idx_activities_lesson_status", columnList = "lesson_id,status"),
                @Index(name = "idx_activities_due_status", columnList = "due_at,status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Activity extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(length = 160, nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String description;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "due_reminder_processed_at")
    private Instant dueReminderProcessedAt;

    @Column(name = "max_score", precision = 7, scale = 2)
    private BigDecimal maxScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ActivityStatus status = ActivityStatus.DRAFT;
}
