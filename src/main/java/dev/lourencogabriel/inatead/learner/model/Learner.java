package dev.lourencogabriel.inatead.learner.model;

import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(
        name = "learners",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_learners_person_id", columnNames = "person_id"),
                @UniqueConstraint(name = "uk_learners_registration_number", columnNames = "registration_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Learner extends AuditedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Generated(event = EventType.INSERT)
    @Column(name = "registration_number", nullable = false, insertable = false, updatable = false)
    private Long registrationNumber;

    @Column(name = "has_completed_high_school", nullable = false)
    private boolean hasCompletedHighSchool;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;
}
