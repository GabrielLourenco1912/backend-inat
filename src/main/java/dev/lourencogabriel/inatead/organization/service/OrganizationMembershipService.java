package dev.lourencogabriel.inatead.organization.service;

import dev.lourencogabriel.inatead.organization.dto.OrganizationMembershipDtos;
import dev.lourencogabriel.inatead.organization.model.OrganizationMembership;
import dev.lourencogabriel.inatead.organization.repository.OrganizationMembershipRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationRepository;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class OrganizationMembershipService extends AbstractCrudService<
        OrganizationMembership,
        OrganizationMembershipDtos.Request,
        OrganizationMembershipDtos.Response
        > {

    private final OrganizationMembershipRepository membershipRepository;
    private final PersonRepository personRepository;
    private final OrganizationRepository organizationRepository;

    public OrganizationMembershipService(
            OrganizationMembershipRepository membershipRepository,
            PersonRepository personRepository,
            OrganizationRepository organizationRepository
    ) {
        super(membershipRepository, "Organization membership");
        this.membershipRepository = membershipRepository;
        this.personRepository = personRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    protected OrganizationMembership newEntity() {
        return new OrganizationMembership();
    }

    @Override
    protected void apply(
            OrganizationMembership entity,
            OrganizationMembershipDtos.Request request
    ) {
        entity.setPerson(EntityLookup.require(personRepository, request.personId(), "Person"));
        entity.setOrganization(EntityLookup.require(
                organizationRepository,
                request.organizationId(),
                "Organization"
        ));
        entity.setMembershipRole(request.membershipRole().trim().toUpperCase(Locale.ROOT));
        entity.setJobTitle(normalizeNullable(request.jobTitle()));
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(request.status());
    }

    @Override
    protected void validate(OrganizationMembership entity, boolean creating) {
        if (entity.getEndDate() != null && entity.getEndDate().isBefore(entity.getStartDate())) {
            throw new BusinessRuleException("Membership end date cannot precede start date");
        }
        boolean duplicate = creating
                ? membershipRepository.existsByPerson_IdAndOrganization_IdAndMembershipRoleAndStartDate(
                        entity.getPerson().getId(),
                        entity.getOrganization().getId(),
                        entity.getMembershipRole(),
                        entity.getStartDate()
                )
                : membershipRepository.existsByPerson_IdAndOrganization_IdAndMembershipRoleAndStartDateAndIdNot(
                        entity.getPerson().getId(),
                        entity.getOrganization().getId(),
                        entity.getMembershipRole(),
                        entity.getStartDate(),
                        entity.getId()
                );
        if (duplicate) {
            throw new ResourceConflictException("This organization membership period already exists");
        }
    }

    @Override
    protected OrganizationMembershipDtos.Response toResponse(OrganizationMembership entity) {
        return new OrganizationMembershipDtos.Response(
                entity.getId(),
                entity.getPerson().getId(),
                entity.getOrganization().getId(),
                entity.getMembershipRole(),
                entity.getJobTitle(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
