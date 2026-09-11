package dev.lourencogabriel.inatead.person.controller;

import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/people")
@PreAuthorize("@domainAuthorization.hasAnyRole(authentication, 'ADMIN')")
@Validated
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService service) {
        this.personService = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonDtos.Response>> create(@Valid @RequestBody PersonDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(personService.create(request), "Resource created", HttpStatus.CREATED));
    }

    @GetMapping("/{id}")
    public ApiResponse<PersonDtos.Response> findById(@PathVariable String id) {
        return new ApiResponse<>(personService.findById(id), "Resource found");
    }

    @GetMapping
    public ApiResponse<PageResponse<PersonDtos.Response>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) PersonTypeCode personType
    ) {
        var pageable = PageRequest.of(page, size);
        return new ApiResponse<>(personType == null ? personService.findAll(pageable)
                : personService.findByType(personType, pageable), "Resources found");
    }

    @PutMapping("/{id}")
    public ApiResponse<PersonDtos.Response> update(@PathVariable String id, @Valid @RequestBody PersonDtos.Request request) {
        return new ApiResponse<>(personService.update(id, request), "Resource updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        personService.delete(id);
        return new ApiResponse<>(null, "Resource deleted");
    }
}
