package dev.lourencogabriel.inatead.learner.model;

import dev.lourencogabriel.inatead.person.model.Person;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "learner_guardians")
@Getter
@Setter
@NoArgsConstructor
public class LearnerGuardian {

    @EmbeddedId
    private LearnerGuardianId id = new LearnerGuardianId();

    @MapsId("learnerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @MapsId("guardianPersonId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_person_id", nullable = false)
    private Person guardianPerson;

    @Column(name = "relationship_type", length = 30, nullable = false)
    private String relationshipType;

    @Column(name = "is_legal_guardian", nullable = false)
    private boolean legalGuardian;

    @Column(name = "is_primary_contact", nullable = false)
    private boolean primaryContact;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
