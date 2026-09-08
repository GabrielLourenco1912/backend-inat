package dev.lourencogabriel.inatead.person.controller;

import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/people")
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
public class PersonController extends AbstractCrudController<PersonDtos.Request, PersonDtos.Response> {

    public PersonController(PersonService service) {
        super(service);
    }
}
