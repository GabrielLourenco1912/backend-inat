package dev.lourencogabriel.inatead.address.model;

import dev.lourencogabriel.inatead.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address extends AuditedEntity {

    @Column(name = "postal_code", length = 8, nullable = false)
    private String postalCode;

    @Column(length = 150, nullable = false)
    private String street;

    @Column(name = "street_number", length = 20, nullable = false)
    private String streetNumber;

    @Column(name = "address_line_2", length = 100)
    private String addressLine2;

    @Column(length = 100, nullable = false)
    private String district;

    @Column(length = 100, nullable = false)
    private String city;

    @Column(name = "state_code", length = 2, nullable = false)
    private String stateCode;

    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode = "BR";
}
