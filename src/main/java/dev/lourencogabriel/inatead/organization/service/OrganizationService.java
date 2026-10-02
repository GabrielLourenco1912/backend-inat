package dev.lourencogabriel.inatead.organization.service;

import dev.lourencogabriel.inatead.address.service.AddressService;
import dev.lourencogabriel.inatead.learner.service.LearnerRelationshipPolicy;
import dev.lourencogabriel.inatead.organization.dto.OrganizationDtos;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.organization.model.OrganizationTypeEntry;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationTypeRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class OrganizationService
        extends AbstractCrudService<Organization, OrganizationDtos.Request, OrganizationDtos.Response> {

    private final OrganizationRepository organizationRepository;
    private final OrganizationTypeRepository organizationTypeRepository;
    private final AddressService addressService;
    private final LearnerRelationshipPolicy learnerRelationshipPolicy;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationTypeRepository organizationTypeRepository,
            AddressService addressService,
            LearnerRelationshipPolicy learnerRelationshipPolicy
    ) {
        super(organizationRepository, "Organization");
        this.organizationRepository = organizationRepository;
        this.organizationTypeRepository = organizationTypeRepository;
        this.addressService = addressService;
        this.learnerRelationshipPolicy = learnerRelationshipPolicy;
    }

    @Override
    protected Organization newEntity() {
        return new Organization();
    }

    @Override
    protected void apply(Organization entity, OrganizationDtos.Request request) {
        if (request.organizationTypes() == null || request.organizationTypes().isEmpty()) {
            throw new BusinessRuleException("An organization must have at least one type");
        }
        String taxId = request.taxId().toUpperCase(Locale.ROOT);
        if (entity.getId() != null) {
            learnerRelationshipPolicy.validateOrganizationTaxId(
                    entity.getId(),
                    entity.getTaxId(),
                    taxId
            );
            learnerRelationshipPolicy.validateOrganizationType(
                    entity.getId(),
                    request.organizationTypes()
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
        var types = new HashSet<>(organizationTypeRepository.findAllById(request.organizationTypes()));
        if (types.size() != request.organizationTypes().size()) {
            throw new BusinessRuleException("One or more organization types are not available");
        }
        entity.getOrganizationTypes().clear();
        entity.getOrganizationTypes().addAll(types);
        entity.setLegalName(request.legalName().trim());
        entity.setTradeName(normalizeNullable(request.tradeName()));
        entity.setTaxId(taxId);
        entity.setContactEmail(request.contactEmail().trim().toLowerCase(Locale.ROOT));
        entity.setPhoneNumber(request.phoneNumber().trim());
        entity.setAttendanceClosingDay(request.attendanceClosingDay());
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
                entity.getParentOrganization() == null
                        ? null
                        : entity.getParentOrganization().getDisplayName(),
                addressService.toResponse(entity.getAddress()),
                entity.getOrganizationTypes().stream()
                        .map(OrganizationTypeEntry::getCode)
                        .collect(Collectors.toUnmodifiableSet()),
                entity.getLegalName(),
                entity.getTradeName(),
                entity.getTaxId(),
                entity.getContactEmail(),
                entity.getPhoneNumber(),
                entity.getAttendanceClosingDay(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
