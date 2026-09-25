package dev.lourencogabriel.inatead.person.repository;

import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select person from Person person where person.id = :id")
    Optional<Person> findByIdForUpdate(@Param("id") String id);

    @Query("select person from Person person join person.personTypes type where type.code = :personType")
    Page<Person> findByPersonType(@Param("personType") PersonTypeCode personType, Pageable pageable);

    Optional<Person> findByTaxId(String taxId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Person> findByContactEmailIgnoreCase(String contactEmail);

    boolean existsByTaxId(String taxId);

    boolean existsByTaxIdAndIdNot(String taxId, String id);

    boolean existsByContactEmailIgnoreCase(String contactEmail);

    boolean existsByContactEmailIgnoreCaseAndIdNot(String contactEmail, String id);
}
