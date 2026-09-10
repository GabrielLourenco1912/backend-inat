package dev.lourencogabriel.inatead.learner.repository;

import dev.lourencogabriel.inatead.learner.model.Learner;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LearnerRepository extends JpaRepository<Learner, String> {

    boolean existsByPerson_Id(String personId);

    boolean existsByPerson_IdAndIdNot(String personId, String id);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByRegistrationNumberAndIdNot(String registrationNumber, String id);

    Optional<Learner> findByPerson_Id(String personId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select learner from Learner learner where learner.id = :id")
    Optional<Learner> findByIdForUpdate(@Param("id") String id);
}
