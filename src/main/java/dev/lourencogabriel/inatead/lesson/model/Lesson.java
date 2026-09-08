package dev.lourencogabriel.inatead.lesson.model;

import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
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

import java.time.Instant;

@Entity
@Table(
        name = "lessons",
        indexes = @Index(
                name = "idx_lessons_instructor_start",
                columnList = "instructor_person_id,starts_at"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_lessons_cohort_start",
                columnNames = {"cohort_id", "starts_at"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Lesson extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_person_id", nullable = false)
    private Person instructorPerson;

    @Column(length = 160, nullable = false)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", length = 20, nullable = false)
    private DeliveryMode deliveryMode;

    @Column(length = 80)
    private String room;

    @Column(name = "meeting_url", length = 512)
    private String meetingUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private LessonStatus status = LessonStatus.SCHEDULED;
}
