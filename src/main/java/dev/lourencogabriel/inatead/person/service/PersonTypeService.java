package dev.lourencogabriel.inatead.person.service;

import dev.lourencogabriel.inatead.learner.repository.LearnerGuardianRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationMembershipRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonType;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonTypeRepository;
import dev.lourencogabriel.inatead.user.repository.UserRoleRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PersonTypeService {

    private final PersonTypeRepository typeRepository;
    private final LearnerRepository learnerRepository;
    private final LearnerGuardianRepository guardianRepository;
    private final LessonRepository lessonRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRoleRepository userRoleRepository;

    public Set<PersonType> resolve(Set<PersonTypeCode> codes) {
        Set<PersonType> types = new HashSet<>(typeRepository.findAllById(codes));
        if (types.size() != codes.size()) {
            throw new BusinessRuleException("One or more person types are not available");
        }
        return types;
    }

    public void validateExistingAssignments(Person person) {
        String id = person.getId();
        if (!person.hasType(PersonTypeCode.LEARNER) && learnerRepository.existsByPerson_Id(id)) {
            throw typeInUse(PersonTypeCode.LEARNER, "a learner profile");
        }
        if (!person.hasType(PersonTypeCode.GUARDIAN) && guardianRepository.existsByGuardianPerson_Id(id)) {
            throw typeInUse(PersonTypeCode.GUARDIAN, "a learner guardian assignment");
        }
        if (!person.hasType(PersonTypeCode.INSTRUCTOR) && lessonRepository.existsByInstructorPerson_Id(id)) {
            throw typeInUse(PersonTypeCode.INSTRUCTOR, "a lesson instructor assignment");
        }
        for (String role : membershipRepository.findRoleCodesByPersonId(id)) {
            PersonTypeCode.fromCode(role).ifPresent(type -> {
                if (!person.hasType(type)) {
                    throw typeInUse(type, "an organization membership");
                }
            });
        }
        for (String role : userRoleRepository.findRoleCodesByPersonId(id)) {
            PersonTypeCode.fromCode(role).ifPresent(type -> {
                if (!person.hasType(type)) {
                    throw typeInUse(type, "an assigned user role");
                }
            });
        }
    }

    private BusinessRuleException typeInUse(PersonTypeCode type, String assignment) {
        return new BusinessRuleException("The " + type + " person type is required by " + assignment);
    }
}
