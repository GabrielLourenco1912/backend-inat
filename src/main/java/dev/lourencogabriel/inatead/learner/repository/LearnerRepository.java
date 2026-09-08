package dev.lourencogabriel.inatead.learner.repository;

import dev.lourencogabriel.inatead.learner.model.Learner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearnerRepository extends JpaRepository<Learner, String> {

    boolean existsByPerson_Id(String personId);

    boolean existsByPerson_IdAndIdNot(String personId, String id);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumberAndIdNot(String registrationNumber, String id);

    Optional<Learner> findByPerson_Id(String personId);
}
