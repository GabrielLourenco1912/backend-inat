package dev.lourencogabriel.inatead.person.repository;

import dev.lourencogabriel.inatead.person.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, String> {

    Optional<Person> findByTaxId(String taxId);

    Optional<Person> findByContactEmailIgnoreCase(String contactEmail);

    boolean existsByTaxId(String taxId);

    boolean existsByTaxIdAndIdNot(String taxId, String id);

    boolean existsByContactEmailIgnoreCase(String contactEmail);

    boolean existsByContactEmailIgnoreCaseAndIdNot(String contactEmail, String id);
}
