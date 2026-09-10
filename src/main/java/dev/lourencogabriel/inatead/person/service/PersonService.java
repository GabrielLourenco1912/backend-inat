package dev.lourencogabriel.inatead.person.service;

import dev.lourencogabriel.inatead.address.service.AddressService;
import dev.lourencogabriel.inatead.learner.service.LearnerRelationshipPolicy;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class PersonService extends AbstractCrudService<Person, PersonDtos.Request, PersonDtos.Response> {

    private final PersonRepository personRepository;
    private final AddressService addressService;
    private final LearnerRelationshipPolicy learnerRelationshipPolicy;

    public PersonService(
            PersonRepository personRepository,
            AddressService addressService,
            LearnerRelationshipPolicy learnerRelationshipPolicy
    ) {
        super(personRepository, "Person");
        this.personRepository = personRepository;
        this.addressService = addressService;
        this.learnerRelationshipPolicy = learnerRelationshipPolicy;
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
