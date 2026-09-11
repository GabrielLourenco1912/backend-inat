package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.learner.dto.LearnerGuardianDtos;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.learner.model.LearnerGuardian;
import dev.lourencogabriel.inatead.learner.model.LearnerGuardianId;
import dev.lourencogabriel.inatead.learner.repository.LearnerGuardianRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LearnerGuardianService {

    private final LearnerGuardianRepository repository;
    private final LearnerRepository learnerRepository;
    private final PersonRepository personRepository;
    private final LearnerRelationshipPolicy relationshipPolicy;

    @Transactional
    public LearnerGuardianDtos.Response create(
            String learnerId,
            LearnerGuardianDtos.Request request
    ) {
        Learner learner = EntityLookup.require(learnerRepository, learnerId, "Learner");
        Person guardian = requireGuardianForUpdate(request.guardianPersonId());
        LearnerGuardianId id = new LearnerGuardianId(learnerId, guardian.getId());
        if (repository.existsById(id)) {
            throw new ResourceConflictException("This guardian is already linked to the learner");
        }

        relationshipPolicy.validateGuardianEligibility(guardian);
        validate(learner, guardian, request, null);
        LearnerGuardian link = new LearnerGuardian();
        link.setId(id);
        link.setLearner(learner);
        link.setGuardianPerson(guardian);
        apply(link, request);
        return toResponse(repository.save(link));
    }

    @Transactional(readOnly = true)
    public List<LearnerGuardianDtos.Response> findByLearner(String learnerId) {
        if (!learnerRepository.existsById(learnerId)) {
            throw new ResourceNotFoundException("Learner", learnerId);
        }
        return repository.findAllByLearner_Id(learnerId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public LearnerGuardianDtos.Response update(
            String learnerId,
            String guardianPersonId,
            LearnerGuardianDtos.Request request
    ) {
        if (!guardianPersonId.equals(request.guardianPersonId())) {
            throw new BusinessRuleException("guardianPersonId cannot be changed");
        }
        LearnerGuardian link = require(learnerId, guardianPersonId);
        requireGuardianForUpdate(guardianPersonId);
        relationshipPolicy.validateGuardianEligibility(link.getGuardianPerson());
        relationshipPolicy.validateGuardianUpdate(link, request.legalGuardian());
        validate(link.getLearner(), link.getGuardianPerson(), request, guardianPersonId);
        apply(link, request);
        return toResponse(repository.save(link));
    }

    @Transactional
    public void delete(String learnerId, String guardianPersonId) {
        LearnerGuardian link = require(learnerId, guardianPersonId);
        relationshipPolicy.validateGuardianDelete(link);
        repository.delete(link);
    }

    private void apply(LearnerGuardian link, LearnerGuardianDtos.Request request) {
        link.setRelationshipType(request.relationshipType().trim().toUpperCase());
        link.setLegalGuardian(request.legalGuardian());
        link.setPrimaryContact(request.primaryContact());
    }

    private void validate(
            Learner learner,
            Person guardian,
            LearnerGuardianDtos.Request request,
            String currentGuardianId
    ) {
        if (learner.getPerson().getId().equals(guardian.getId())) {
            throw new BusinessRuleException("A learner cannot be their own guardian");
        }
        if (request.primaryContact()) {
            boolean duplicate = currentGuardianId == null
                    ? repository.existsByLearner_IdAndPrimaryContactTrue(learner.getId())
                    : repository.existsByLearner_IdAndPrimaryContactTrueAndGuardianPerson_IdNot(
                            learner.getId(),
                            currentGuardianId
                    );
            if (duplicate) {
                throw new BusinessRuleException("The learner already has a primary contact");
            }
        }
    }

    private LearnerGuardian require(String learnerId, String guardianPersonId) {
        return repository.findById(new LearnerGuardianId(learnerId, guardianPersonId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Learner guardian",
                        learnerId + "/" + guardianPersonId
                ));
    }

    private Person requireGuardianForUpdate(String id) {
        return personRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Guardian", id));
    }

    private LearnerGuardianDtos.Response toResponse(LearnerGuardian link) {
        return new LearnerGuardianDtos.Response(
                link.getLearner().getId(),
                link.getGuardianPerson().getId(),
                link.getGuardianPerson().getFullName(),
                link.getRelationshipType(),
                link.isLegalGuardian(),
                link.isPrimaryContact(),
                link.getCreatedAt(),
                link.getUpdatedAt()
        );
    }
}
