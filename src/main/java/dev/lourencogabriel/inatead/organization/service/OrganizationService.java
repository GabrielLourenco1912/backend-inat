package dev.lourencogabriel.inatead.organization.service;

import dev.lourencogabriel.inatead.address.service.AddressService;
import dev.lourencogabriel.inatead.learner.service.LearnerRelationshipPolicy;
import dev.lourencogabriel.inatead.organization.dto.OrganizationDtos;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class OrganizationService
        extends AbstractCrudService<Organization, OrganizationDtos.Request, OrganizationDtos.Response> {

    private final OrganizationRepository organizationRepository;
    private final AddressService addressService;
    private final LearnerRelationshipPolicy learnerRelationshipPolicy;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            AddressService addressService,
            LearnerRelationshipPolicy learnerRelationshipPolicy
    ) {
        super(organizationRepository, "Organization");
        this.organizationRepository = organizationRepository;
        this.addressService = addressService;
        this.learnerRelationshipPolicy = learnerRelationshipPolicy;
    }

    @Override
    protected Organization newEntity() {
        return new Organization();
    }

    @Override
    protected void apply(Organization entity, OrganizationDtos.Request request) {
        if (entity.getId() != null) {
            learnerRelationshipPolicy.validateOrganizationType(
                    entity.getId(),
                    request.organizationType()
            );
            learnerRelationshipPolicy.validateOrganizationStatus(
                    entity.getId(),
                    request.status()
            );
        }
        entity.setParentOrganization(
                request.parentOrganizationId() == null || request.parentOrganizationId().isBlank()
                        ? null
                        : EntityLookup.require(
                                organizationRepository,
                                request.parentOrganizationId(),
                                "Parent organization"
                        )
        );
        entity.setAddress(addressService.apply(entity.getAddress(), request.address()));
        entity.setOrganizationType(request.organizationType());
        entity.setLegalName(request.legalName().trim());
        entity.setTradeName(normalizeNullable(request.tradeName()));
        entity.setTaxId(request.taxId());
        entity.setContactEmail(request.contactEmail().trim().toLowerCase(Locale.ROOT));
        entity.setPhoneNumber(request.phoneNumber().trim());
        entity.setStatus(request.status());
    }

    @Override
    protected void validate(Organization entity, boolean creating) {
        boolean duplicate = creating
                ? organizationRepository.existsByTaxId(entity.getTaxId())
                : organizationRepository.existsByTaxIdAndIdNot(entity.getTaxId(), entity.getId());
        if (duplicate) {
            throw new ResourceConflictException("An organization with this tax ID already exists");
        }

        Organization current = entity.getParentOrganization();
        while (current != null) {
            if (entity.getId() != null && entity.getId().equals(current.getId())) {
                throw new BusinessRuleException("Organization hierarchy cannot contain a cycle");
            }
            current = current.getParentOrganization();
        }
    }

    @Override
    protected OrganizationDtos.Response toResponse(Organization entity) {
        return new OrganizationDtos.Response(
                entity.getId(),
                entity.getParentOrganization() == null
                        ? null
                        : entity.getParentOrganization().getId(),
                addressService.toResponse(entity.getAddress()),
                entity.getOrganizationType(),
                entity.getLegalName(),
                entity.getTradeName(),
                entity.getTaxId(),
                entity.getContactEmail(),
                entity.getPhoneNumber(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
