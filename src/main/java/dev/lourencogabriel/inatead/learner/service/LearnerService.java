package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.learner.dto.LearnerDtos;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.service.PersonTypeRules;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearnerService extends AbstractCrudService<Learner, LearnerDtos.Request, LearnerDtos.Response> {

    private final LearnerRepository learnerRepository;
    private final PersonRepository personRepository;
    private final LearnerRelationshipPolicy relationshipPolicy;

    public LearnerService(
            LearnerRepository learnerRepository,
            PersonRepository personRepository,
            LearnerRelationshipPolicy relationshipPolicy
    ) {
        super(learnerRepository, "Learner");
        this.learnerRepository = learnerRepository;
        this.personRepository = personRepository;
        this.relationshipPolicy = relationshipPolicy;
    }

    @Transactional(readOnly = true)
    public PageResponse<LearnerDtos.Response> findByPerson(String personId, Pageable pageable) {
        return PageResponse.from(learnerRepository.findByPerson_Id(personId, pageable).map(this::toResponse));
    }

    @Override
    protected Learner newEntity() {
        return new Learner();
    }

    @Override
    @Transactional
    public LearnerDtos.Response create(LearnerDtos.Request request) {
        Learner learner = newEntity();
        apply(learner, request);
        validate(learner, true);
        return toResponse(learnerRepository.saveAndFlush(learner));
    }

    @Override
    protected void apply(Learner entity, LearnerDtos.Request request) {
        if (entity.getId() != null
                && !entity.getPerson().getId().equals(request.personId())) {
            throw new BusinessRuleException("personId cannot be changed");
        }
        entity.setPerson(personRepository.findByIdForUpdate(request.personId())
                .orElseThrow(() -> new ResourceNotFoundException("Person", request.personId())));
        entity.setHasCompletedHighSchool(request.hasCompletedHighSchool());
        entity.setStatus(request.status());
    }

    @Override
    protected void validate(Learner entity, boolean creating) {
        PersonTypeRules.requireType(entity.getPerson(), PersonTypeCode.LEARNER);
        boolean duplicatePerson = creating
                ? learnerRepository.existsByPerson_Id(entity.getPerson().getId())
                : learnerRepository.existsByPerson_IdAndIdNot(entity.getPerson().getId(), entity.getId());
        if (duplicatePerson) {
            throw new ResourceConflictException("This person is already registered as a learner");
        }

        if (!creating) {
            relationshipPolicy.validateLearnerUpdate(entity);
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
