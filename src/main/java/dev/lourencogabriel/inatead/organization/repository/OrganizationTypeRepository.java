package dev.lourencogabriel.inatead.organization.repository;

import dev.lourencogabriel.inatead.organization.model.OrganizationTypeEntry;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationTypeRepository extends JpaRepository<OrganizationTypeEntry, OrganizationType> {
}
