package dev.lourencogabriel.inatead.organization.model;

import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "organization_types")
@Getter
@NoArgsConstructor
public class OrganizationTypeEntry {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false, updatable = false)
    private OrganizationType code;

    public OrganizationTypeEntry(OrganizationType code) {
        this.code = code;
    }
}
