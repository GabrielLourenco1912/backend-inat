package dev.lourencogabriel.inatead.person.service;

import dev.lourencogabriel.inatead.address.service.AddressService;
import dev.lourencogabriel.inatead.learner.service.LearnerRelationshipPolicy;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonType;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class PersonService extends AbstractCrudService<Person, PersonDtos.Request, PersonDtos.Response> {

    private final PersonRepository personRepository;
    private final AddressService addressService;
    private final LearnerRelationshipPolicy learnerRelationshipPolicy;
    private final PersonTypeService personTypeService;

    public PersonService(
            PersonRepository personRepository,
            AddressService addressService,
            LearnerRelationshipPolicy learnerRelationshipPolicy,
            PersonTypeService personTypeService
    ) {
        super(personRepository, "Person");
        this.personRepository = personRepository;
        this.addressService = addressService;
        this.learnerRelationshipPolicy = learnerRelationshipPolicy;
        this.personTypeService = personTypeService;
    }

    @Override
    protected Person newEntity() {
        return new Person();
    }

    @Override
    protected void apply(Person entity, PersonDtos.Request request) {
        entity.setAddress(addressService.apply(entity.getAddress(), request.address()));
        entity.setFullName(request.fullName().trim());
        entity.setTaxId(request.taxId());
        entity.setContactEmail(normalizeEmail(request.contactEmail()));
        entity.setPhoneNumber(request.phoneNumber().trim());
        entity.setBirthDate(request.birthDate());
        entity.setGender(normalizeNullable(request.gender()));
        entity.setStatus(request.status());
        if (request.personTypes() != null) {
            var types = personTypeService.resolve(request.personTypes());
            entity.getPersonTypes().clear();
            entity.getPersonTypes().addAll(types);
        }
    }

    @Override
    @Transactional
    public PersonDtos.Response update(String id, PersonDtos.Request request) {
        Person person = personRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person", id));
        apply(person, request);
        validate(person, false);
        return toResponse(personRepository.save(person));
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonDtos.Response> findByType(PersonTypeCode personType, Pageable pageable) {
        return PageResponse.from(personRepository.findByPersonType(personType, pageable).map(this::toResponse));
    }

    @Override
    protected void validate(Person entity, boolean creating) {
        boolean duplicateTaxId = creating
                ? personRepository.existsByTaxId(entity.getTaxId())
                : personRepository.existsByTaxIdAndIdNot(entity.getTaxId(), entity.getId());
        if (duplicateTaxId) {
            throw new ResourceConflictException("A person with this tax ID already exists");
        }

        if (!creating) {
            personTypeService.validateExistingAssignments(entity);
            learnerRelationshipPolicy.validatePersonUpdate(entity);
        }

        if (entity.getContactEmail() == null) {
            return;
        }
        boolean duplicateEmail = creating
                ? personRepository.existsByContactEmailIgnoreCase(entity.getContactEmail())
                : personRepository.existsByContactEmailIgnoreCaseAndIdNot(
                        entity.getContactEmail(),
                        entity.getId()
                );
        if (duplicateEmail) {
            throw new ResourceConflictException("A person with this contact email already exists");
        }
    }

    @Override
    protected PersonDtos.Response toResponse(Person entity) {
        return new PersonDtos.Response(
                entity.getId(),
                addressService.toResponse(entity.getAddress()),
                entity.getFullName(),
                entity.getTaxId(),
                entity.getContactEmail(),
                entity.getPhoneNumber(),
                entity.getBirthDate(),
                entity.getGender(),
                entity.getStatus(),
                entity.getPersonTypes().stream().map(PersonType::getCode).collect(Collectors.toSet()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
