package dev.lourencogabriel.inatead.person.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "person_types")
@Getter
@NoArgsConstructor
public class PersonType {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40, nullable = false, updatable = false)
    private PersonTypeCode code;

    public PersonType(PersonTypeCode code) {
        this.code = code;
    }
}
