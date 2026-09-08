package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.learner.dto.LearnerDtos;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import org.springframework.stereotype.Service;

@Service
public class LearnerService extends AbstractCrudService<Learner, LearnerDtos.Request, LearnerDtos.Response> {

    private final LearnerRepository learnerRepository;
    private final PersonRepository personRepository;

    public LearnerService(LearnerRepository learnerRepository, PersonRepository personRepository) {
        super(learnerRepository, "Learner");
        this.learnerRepository = learnerRepository;
        this.personRepository = personRepository;
    }

    @Override
    protected Learner newEntity() {
        return new Learner();
    }

    @Override
    protected void apply(Learner entity, LearnerDtos.Request request) {
        entity.setPerson(EntityLookup.require(personRepository, request.personId(), "Person"));
        entity.setRegistrationNumber(request.registrationNumber().trim());
        entity.setHasCompletedHighSchool(request.hasCompletedHighSchool());
        entity.setStatus(request.status());
    }

    @Override
    protected void validate(Learner entity, boolean creating) {
        boolean duplicatePerson = creating
                ? learnerRepository.existsByPerson_Id(entity.getPerson().getId())
                : learnerRepository.existsByPerson_IdAndIdNot(entity.getPerson().getId(), entity.getId());
        if (duplicatePerson) {
            throw new ResourceConflictException("This person is already registered as a learner");
        }

        boolean duplicateRegistration = creating
                ? learnerRepository.existsByRegistrationNumber(entity.getRegistrationNumber())
                : learnerRepository.existsByRegistrationNumberAndIdNot(
                        entity.getRegistrationNumber(),
                        entity.getId()
                );
        if (duplicateRegistration) {
            throw new ResourceConflictException("This learner registration number is already in use");
        }
    }

    @Override
    protected LearnerDtos.Response toResponse(Learner entity) {
        return new LearnerDtos.Response(
                entity.getId(),
                entity.getPerson().getId(),
                entity.getRegistrationNumber(),
                entity.isHasCompletedHighSchool(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
