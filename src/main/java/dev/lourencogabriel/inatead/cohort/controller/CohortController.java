package dev.lourencogabriel.inatead.cohort.controller;

import dev.lourencogabriel.inatead.cohort.dto.CohortDtos;
import dev.lourencogabriel.inatead.cohort.service.CohortService;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cohorts")
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
public class CohortController extends AbstractCrudController<CohortDtos.Request, CohortDtos.Response> {

    public CohortController(CohortService service) {
        super(service);
    }
}
