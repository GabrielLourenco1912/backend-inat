package dev.lourencogabriel.inatead.person.repository;

import dev.lourencogabriel.inatead.person.model.PersonType;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonTypeRepository extends JpaRepository<PersonType, PersonTypeCode> {
}
