package dev.lourencogabriel.inatead.cohort.controller;

import dev.lourencogabriel.inatead.cohort.dto.CohortEnrollmentDtos;
import dev.lourencogabriel.inatead.cohort.service.CohortEnrollmentService;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cohort-enrollments")
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
public class CohortEnrollmentController extends AbstractCrudController<
        CohortEnrollmentDtos.Request,
        CohortEnrollmentDtos.Response
        > {

    public CohortEnrollmentController(CohortEnrollmentService service) {
        super(service);
    }
}
