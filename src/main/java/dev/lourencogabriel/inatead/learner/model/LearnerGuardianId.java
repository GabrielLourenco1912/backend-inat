package dev.lourencogabriel.inatead.learner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class LearnerGuardianId implements Serializable {

    @Column(name = "learner_id", length = 26)
    private String learnerId;

    @Column(name = "guardian_person_id", length = 26)
    private String guardianPersonId;
}
