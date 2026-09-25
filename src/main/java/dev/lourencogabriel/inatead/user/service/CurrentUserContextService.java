package dev.lourencogabriel.inatead.user.service;

import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.organization.repository.OrganizationMembershipRepository;
import dev.lourencogabriel.inatead.shared.model.OrganizationType;
import dev.lourencogabriel.inatead.user.dto.CurrentUserContextResponse;
import dev.lourencogabriel.inatead.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CurrentUserContextService {

    private final UserService userService;
    private final LearnerRepository learnerRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CurrentUserContextResponse find(String userId) {
        UserResponse user = this.userService.findById(userId);
        String personId = user.personId();
        String learnerId = personId == null
                ? null
                : this.learnerRepository.findByPerson_Id(personId)
                        .map(learner -> learner.getId())
                        .orElse(null);
        var organizationIds = personId == null || !user.roles().contains("EMPLOYER_MANAGER")
                ? java.util.List.<String>of()
                : this.membershipRepository.findActiveByPersonId(
                                personId,
                                BusinessTime.today(this.clock)
                        ).stream()
                        .filter(membership -> membership.getOrganization().hasType(OrganizationType.EMPLOYER))
                        .map(membership -> membership.getOrganization().getId())
                        .distinct()
                        .toList();

        return new CurrentUserContextResponse(user, learnerId, organizationIds);
    }
}
