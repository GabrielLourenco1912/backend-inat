package dev.lourencogabriel.inatead.learner.service;

import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.learner.dto.LearnerDtos;
import dev.lourencogabriel.inatead.learner.dto.LearnerGuardianDtos;
import dev.lourencogabriel.inatead.learner.dto.LearnerOnboardingDtos;
import dev.lourencogabriel.inatead.person.dto.PersonDtos;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.person.service.PersonTypeRules;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LearnerOnboardingService {

    private final PersonRepository personRepository;
    private final PersonService personService;
    private final LearnerService learnerService;
    private final LearnerGuardianService learnerGuardianService;
    private final Clock clock;

    @Transactional
    public LearnerOnboardingDtos.Response onboard(LearnerOnboardingDtos.Request request) {
        List<LearnerOnboardingDtos.Guardian> guardians = List.copyOf(request.guardians());
        validateGuardians(request.person().birthDate(), guardians);

        ResolvedPerson resolvedPerson = resolvePerson(request.person());
        return createLearner(resolvedPerson, request.learner(), guardians);
    }

    @Transactional
    public LearnerOnboardingDtos.Response fromPerson(
            String personId,
            LearnerOnboardingDtos.ExistingPersonRequest request
    ) {
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Person", personId));
        PersonTypeRules.requireType(person, PersonTypeCode.LEARNER);
        List<LearnerOnboardingDtos.Guardian> guardians = List.copyOf(request.guardians());
        validateGuardians(person.getBirthDate(), guardians);

        return createLearner(
                new ResolvedPerson(personService.findById(personId), false),
                request.learner(),
                guardians
        );
    }

    private LearnerOnboardingDtos.Response createLearner(
            ResolvedPerson resolvedPerson,
            LearnerOnboardingDtos.LearnerProfile profile,
            List<LearnerOnboardingDtos.Guardian> guardians
    ) {
        LearnerDtos.Response learner = learnerService.create(new LearnerDtos.Request(
                resolvedPerson.person().id(),
                profile.registrationNumber(),
                profile.hasCompletedHighSchool(),
                profile.status()
        ));

        List<LearnerGuardianDtos.Response> linkedGuardians = new ArrayList<>(guardians.size());
        for (LearnerOnboardingDtos.Guardian guardian : guardians) {
            linkedGuardians.add(learnerGuardianService.create(
                    learner.id(),
                    new LearnerGuardianDtos.Request(
                            guardian.guardianPersonId(),
                            guardian.relationshipType(),
                            guardian.legalGuardian(),
                            guardian.primaryContact()
                    )
            ));
        }

        return new LearnerOnboardingDtos.Response(
                resolvedPerson.person(),
                learner,
                List.copyOf(linkedGuardians),
                resolvedPerson.created()
        );
    }

    private ResolvedPerson resolvePerson(PersonDtos.Request request) {
        return personRepository.findByTaxId(request.taxId())
                .map(person -> reusePerson(person, request))
                .orElseGet(() -> new ResolvedPerson(personService.create(request), true));
    }

    private ResolvedPerson reusePerson(Person person, PersonDtos.Request request) {
        if (!person.getBirthDate().equals(request.birthDate())) {
            throw new ResourceConflictException(
                    "The supplied tax ID belongs to a person with a different birth date"
            );
        }
        return new ResolvedPerson(personService.findById(person.getId()), false);
    }

    private void validateGuardians(
            LocalDate learnerBirthDate,
            List<LearnerOnboardingDtos.Guardian> guardians
    ) {
        Set<String> guardianPersonIds = new HashSet<>();
        for (LearnerOnboardingDtos.Guardian guardian : guardians) {
            if (!guardianPersonIds.add(guardian.guardianPersonId())) {
                throw new BusinessRuleException(
                        "The same guardian cannot be supplied more than once"
                );
            }
        }

        boolean minor = learnerBirthDate.plusYears(18).isAfter(BusinessTime.today(clock));
        boolean hasLegalGuardian = guardians.stream()
                .anyMatch(LearnerOnboardingDtos.Guardian::legalGuardian);
        if (minor && !hasLegalGuardian) {
            throw new BusinessRuleException(
                    "A minor learner requires at least one legal guardian"
            );
        }
    }

    private record ResolvedPerson(PersonDtos.Response person, boolean created) {
    }
}
