package dev.lourencogabriel.inatead.learner.repository;

import dev.lourencogabriel.inatead.learner.model.LearnerGuardian;
import dev.lourencogabriel.inatead.learner.model.LearnerGuardianId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnerGuardianRepository extends JpaRepository<LearnerGuardian, LearnerGuardianId> {

    @EntityGraph(attributePaths = "guardianPerson")
    List<LearnerGuardian> findAllByLearner_Id(String learnerId);

    boolean existsByLearner_IdAndPrimaryContactTrue(String learnerId);

    boolean existsByLearner_IdAndPrimaryContactTrueAndGuardianPerson_IdNot(
            String learnerId,
            String guardianPersonId
    );
}
