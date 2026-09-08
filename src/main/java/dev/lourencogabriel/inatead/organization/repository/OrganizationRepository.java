package dev.lourencogabriel.inatead.organization.repository;

import dev.lourencogabriel.inatead.organization.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, String> {

    boolean existsByTaxId(String taxId);

    boolean existsByTaxIdAndIdNot(String taxId, String id);
}
