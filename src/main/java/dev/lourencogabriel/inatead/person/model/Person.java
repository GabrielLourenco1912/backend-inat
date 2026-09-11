package dev.lourencogabriel.inatead.person.model;

import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "people",
        indexes = @Index(name = "idx_people_address_id", columnList = "address_id"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_people_tax_id", columnNames = "tax_id"),
                @UniqueConstraint(
                        name = "uk_people_contact_email",
                        columnNames = "contact_email"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Person extends AuditedEntity {

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JoinColumn(name = "address_id", nullable = false, unique = true)
    private Address address;

    @Column(name = "full_name", length = 150, nullable = false)
    private String fullName;

    @Column(name = "tax_id", length = 11, nullable = false)
    private String taxId;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "phone_number", length = 20, nullable = false)
    private String phoneNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(length = 30)
    private String gender;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "person_type_assignments",
            joinColumns = @JoinColumn(name = "person_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "person_type_code", nullable = false),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_person_type_assignments",
                    columnNames = {"person_id", "person_type_code"}
            ),
            indexes = @Index(name = "idx_person_type_assignments_type", columnList = "person_type_code")
    )
    @org.hibernate.annotations.BatchSize(size = 100)
    private Set<PersonType> personTypes = new HashSet<>();

    public boolean hasType(PersonTypeCode code) {
        return personTypes.stream().anyMatch(type -> type.getCode() == code);
    }
}
