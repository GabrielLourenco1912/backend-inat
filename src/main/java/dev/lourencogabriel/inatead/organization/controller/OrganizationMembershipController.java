package dev.lourencogabriel.inatead.organization.controller;

import dev.lourencogabriel.inatead.organization.dto.OrganizationMembershipDtos;
import dev.lourencogabriel.inatead.organization.service.OrganizationMembershipService;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization-memberships")
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
public class OrganizationMembershipController extends AbstractCrudController<
        OrganizationMembershipDtos.Request,
        OrganizationMembershipDtos.Response
        > {

    public OrganizationMembershipController(OrganizationMembershipService service) {
        super(service);
    }
}
