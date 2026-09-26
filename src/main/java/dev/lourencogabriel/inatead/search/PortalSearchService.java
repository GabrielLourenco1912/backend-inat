package dev.lourencogabriel.inatead.search;

import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.person.service.PersonService;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.learner.service.LearnerService;
import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.organization.service.OrganizationService;
import dev.lourencogabriel.inatead.organization.model.Organization;
import dev.lourencogabriel.inatead.contract.service.ContractService;
import dev.lourencogabriel.inatead.contract.model.Contract;
import dev.lourencogabriel.inatead.cohort.service.CohortService;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.lesson.service.LessonService;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.activity.service.ActivityService;
import dev.lourencogabriel.inatead.notification.service.NotificationService;
import dev.lourencogabriel.inatead.user.service.UserService;
import dev.lourencogabriel.inatead.user.service.RoleService;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.document.service.DocumentTypeService;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortalSearchService {
    private static final Map<String, Class<? extends Enum<?>>> STATUS_TYPES = Map.of(
            "people", RecordStatus.class,
            "learners", RecordStatus.class,
            "organizations", RecordStatus.class,
            "contracts", ContractStatus.class,
            "cohorts", CohortStatus.class,
            "lessons", LessonStatus.class,
            "activities", ActivityStatus.class,
            "users", UserAccountStatus.class
    );

    private final EntityManager em;
    private final Clock clock;
    private final DomainAuthorization authorization;
    private final UserRepository userRepository;
    private final PersonService people;
    private final LearnerService learners;
    private final OrganizationService organizations;
    private final ContractService contracts;
    private final CohortService cohorts;
    private final LessonService lessons;
    private final ActivityService activities;
    private final NotificationService notifications;
    private final UserService users;
    private final RoleService roles;
    private final DocumentTypeService documentTypes;

    public record Option(String id, String label) {}
    private record Resource(String entity, String id, List<String> fields) {}

    private Resource resource(String name) {
        return switch (name) {
            case "people" -> new Resource("Person", "id", List.of("fullName", "taxId", "contactEmail", "phoneNumber", "address.city"));
            case "learners" -> new Resource("Learner", "id", List.of("registrationNumber", "person.fullName"));
            case "organizations" -> new Resource("Organization", "id", List.of("legalName", "tradeName", "taxId", "contactEmail", "address.city"));
            case "contracts" -> new Resource("Contract", "id", List.of("id", "learner.registrationNumber", "learner.person.fullName", "employer.legalName", "employer.tradeName"));
            case "cohorts" -> new Resource("Cohort", "id", List.of("code", "name"));
            case "lessons" -> new Resource("Lesson", "id", List.of("title", "cohort.code", "cohort.name", "instructorPerson.fullName"));
            case "activities" -> new Resource("Activity", "id", List.of("title", "lesson.title"));
            case "notifications" -> new Resource("Notification", "id", List.of("title", "message"));
            case "users" -> new Resource("User", "ulid", List.of("name", "email"));
            case "roles" -> new Resource("Role", "id", List.of("code", "name", "description"));
            case "document-types" -> new Resource("DocumentType", "id", List.of("code", "name", "description"));
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown search resource");
        };
    }

    @Transactional(readOnly = true)
    public PageResponse<Object> search(String resource, String text, int page, int size, String personType, String status,
                                      String purpose, String contextId, boolean lookup, Authentication authentication) {
        var spec = resource(resource);
        var userId = authorization.userId(authentication);
        if (userId == null) throw new AccessDeniedException("Authentication required");
        var user = userRepository.findWithRolesByUlid(userId)
                .filter(u -> u.getStatus() == UserAccountStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Active account required"));
        var roleCodes = user.getUserRoles().stream().map(r -> r.getRole().getCode())
                .filter(SystemRoleCode::isSupported).collect(java.util.stream.Collectors.toSet());
        boolean admin = roleCodes.contains("ADMIN");
        var params = new LinkedHashMap<String, Object>();
        var where = new ArrayList<String>();
        String personId = user.getPerson() == null ? null : user.getPerson().getId();
        if (!admin) {
            if (personId == null) throw new AccessDeniedException("Person required");
            params.put("person", personId);
            params.put("today", BusinessTime.today(clock));
            String managed = "select m.organization.id from OrganizationMembership m where m.person.id = :person and m.status = 'ACTIVE' and m.startDate <= :today and (m.endDate is null or m.endDate >= :today) and exists (select ot.code from Organization o join o.organizationTypes ot where o = m.organization and ot.code = 'EMPLOYER')";
            boolean manager = roleCodes.contains("EMPLOYER_MANAGER");
            boolean instructor = roleCodes.contains("INSTRUCTOR");
            String lessonScope = "(" + (instructor ? "@lesson@.instructorPerson.id = :person or " : "") + "exists (select p.id from LessonParticipant p where p.lesson = @lesson@ and p.learner.person.id = :person))";
            switch (resource) {
                case "organizations" -> { if (!manager) throw new AccessDeniedException("Organization access required"); where.add("e.id in (" + managed + ")"); }
                case "contracts" -> where.add("(e.learner.person.id = :person" + (manager ? " or e.employer.id in (" + managed + ")" : "") + ")");
                case "learners" -> where.add("(e.person.id = :person" + (manager ? " or exists (select c.id from Contract c where c.learner = e and c.employer.id in (" + managed + "))" : "") + (instructor ? " or exists (select p.id from LessonParticipant p where p.learner = e and p.lesson.instructorPerson.id = :person)" : "") + ")");
                case "lessons" -> where.add(lessonScope.replace("@lesson@", "e"));
                case "activities" -> {
                    where.add(lessonScope.replace("@lesson@", "e.lesson"));
                    where.add("(e.status <> 'DRAFT'" + (instructor ? " or e.lesson.instructorPerson.id = :person" : "") + ")");
                }
                default -> throw new AccessDeniedException("Administrator required");
            }
        }
        if (personType != null && !personType.isBlank()) {
            if (!resource.equals("people")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personType only applies to people");
            try { params.put("personType", PersonTypeCode.valueOf(personType)); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid person type"); }
            where.add("exists (select pt.code from Person p join p.personTypes pt where p = e and pt.code = :personType)");
        }
        if (status != null && !status.isBlank()) {
            var statusType = STATUS_TYPES.get(resource);
            if (statusType == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status filter not supported for resource");
            var requestedStatus = status.trim().toUpperCase(Locale.ROOT);
            var value = Arrays.stream(statusType.getEnumConstants())
                    .filter(candidate -> candidate.name().equals(requestedStatus))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status"));
            params.put("status", value);
            where.add("e.status = :status");
        }
        if (resource.equals("roles")) where.add("e.code in ('ADMIN', 'INSTRUCTOR', 'LEARNER', 'EMPLOYER_MANAGER')");
        if (lookup) applyPurpose(resource, purpose, contextId, where, params, authentication, admin);
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            params.put("q", "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
            var matches = new ArrayList<>(spec.fields().stream()
                    .filter(field -> admin || (!field.endsWith("person.fullName") && !field.equals("instructorPerson.fullName")))
                    .map(field -> field.endsWith("registrationNumber")
                            ? "cast(e." + field + " as string) like :q escape '!'"
                            : "lower(e." + field + ") like :q escape '!'")
                    .toList());
            if (resource.equals("people")) matches.add("exists (select pt.code from Person p join p.personTypes pt where p = e and lower(cast(pt.code as string)) like :q escape '!')");
            if (resource.equals("people")) {
                var labels = Map.of(PersonTypeCode.ADMIN, "administrador", PersonTypeCode.INSTRUCTOR, "instrutor",
                        PersonTypeCode.LEARNER, "aprendiz", PersonTypeCode.EMPLOYER_MANAGER, "gestor de empresa", PersonTypeCode.GUARDIAN, "responsável");
                var matchingTypes = labels.entrySet().stream().filter(entry -> entry.getValue().contains(q)).map(Map.Entry::getKey).toList();
                if (!matchingTypes.isEmpty()) {
                    params.put("matchingTypes", matchingTypes);
                    matches.add("exists (select pt.code from Person p join p.personTypes pt where p = e and pt.code in :matchingTypes)");
                }
            }
            if (resource.equals("users")) matches.add("exists (select ur.id from UserRole ur where ur.user = e and (lower(ur.role.name) like :q escape '!' or lower(ur.role.code) like :q escape '!'))");
            if (resource.equals("notifications")) {
                matches.add("(e.audienceType = 'USER' and exists (select u.ulid from User u where u.ulid = e.audienceId and lower(u.name) like :q escape '!'))");
                matches.add("(e.audienceType = 'COHORT' and exists (select c.id from Cohort c where c.id = e.audienceId and (lower(c.name) like :q escape '!' or lower(c.code) like :q escape '!')))");
                if ("todos".contains(q)) matches.add("e.audienceType = 'ALL'");
            }
            where.add("(" + String.join(" or ", matches) + ")");
        }
        String from = " from " + spec.entity() + " e" + (where.isEmpty() ? "" : " where " + String.join(" and ", where));
        var countQuery = em.createQuery("select count(e)" + from, Long.class);
        var rowsQuery = em.createQuery("select e" + from + " order by e." + spec.id(), Object.class);
        // Bind only parameters referenced by the chosen scope/purpose.
        for (var parameter : countQuery.getParameters()) countQuery.setParameter(parameter.getName(), params.get(parameter.getName()));
        for (var parameter : rowsQuery.getParameters()) rowsQuery.setParameter(parameter.getName(), params.get(parameter.getName()));
        long total = countQuery.getSingleResult();
        long offset = (long) page * size;
        List<Object> rows = offset >= total ? List.of() : rowsQuery.setFirstResult(Math.toIntExact(offset)).setMaxResults(size).getResultList();
        List<Object> content = rows.stream().map(entity -> lookup ? option(resource, entity, admin) : dto(resource, entity)).toList();
        return PageResponse.from(new PageImpl<>(content, PageRequest.of(page, size), total));
    }

    private void applyPurpose(String resource, String purpose, String contextId, List<String> where,
                              Map<String, Object> params, Authentication authentication, boolean admin) {
        if (!Set.of("people", "learners", "organizations", "contracts", "cohorts", "lessons", "users").contains(resource))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resource has no lookup");
        String expected = switch (purpose) {
            case "guardian", "instructor", "member" -> "people";
            case "contract-learner", "participant" -> "learners";
            case "employer", "school", "parent" -> "organizations";
            case "enrollment" -> "contracts";
            case "lesson-cohort", "audience-cohort" -> "cohorts";
            case "activity" -> "lessons";
            case "audience-user" -> "users";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lookup purpose");
        };
        if (!expected.equals(resource)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purpose does not match resource");
        switch (purpose) {
            case "guardian" -> {
                var today = BusinessTime.today(clock);
                var cutoff = today.minusYears(18);
                // plusYears(18), used by the domain policy, maps February 29 to February 28.
                if (!cutoff.plusDays(1).plusYears(18).isAfter(today)) cutoff = cutoff.plusDays(1);
                params.put("adultDate", cutoff);
                where.add("e.birthDate <= :adultDate and exists (select pt.code from Person p join p.personTypes pt where p = e and pt.code = 'GUARDIAN')");
            }
            case "instructor" -> where.add("e.status = 'ACTIVE' and exists (select pt.code from Person p join p.personTypes pt where p = e and pt.code = 'INSTRUCTOR') and exists (select u.ulid from User u join u.userRoles ur where u.person = e and u.status = 'ACTIVE' and ur.role.code = 'INSTRUCTOR')");
            case "contract-learner", "participant" -> {
                where.add("e.status = 'ACTIVE' and e.person.status = 'ACTIVE'");
                if (purpose.equals("participant")) {
                    if (contextId == null || !authorization.canManageLesson(authentication, contextId)) throw new AccessDeniedException("Lesson management required");
                    var lesson = em.find(Lesson.class, contextId);
                    if (lesson == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
                    params.put("lesson", contextId);
                    where.add("not exists (select p.id from LessonParticipant p where p.lesson.id = :lesson and p.learner = e)");
                    if (lesson.getDeliveryMode() == DeliveryMode.ONLINE) {
                        params.put("lessonDate", BusinessTime.date(lesson.getStartsAt()));
                        where.add("exists (select c.id from Contract c where c.learner = e and c.status = 'ACTIVE' and c.weeklyWorkloadMinutes = 1800 and c.startDate <= :lessonDate and (c.endDate is null or c.endDate >= :lessonDate))");
                    }
                }
            }
            case "employer" -> where.add("e.status = 'ACTIVE' and exists (select ot.code from Organization o join o.organizationTypes ot where o = e and ot.code = 'EMPLOYER')");
            case "school" -> where.add("e.status = 'ACTIVE' and exists (select ot.code from Organization o join o.organizationTypes ot where o = e and ot.code = 'SCHOOL')");
            case "enrollment" -> where.add("e.status in ('DRAFT', 'ACTIVE', 'SUSPENDED')");
            case "lesson-cohort" -> where.add("e.status = 'ACTIVE'");
            case "activity" -> {
                if (!admin && !authorization.hasAnyRole(authentication, "INSTRUCTOR")) throw new AccessDeniedException("Instructor required");
                where.add("e.status <> 'CANCELLED'");
                if (!admin) where.add("e.instructorPerson.id = :person");
            }
            case "audience-user" -> where.add("e.status = 'ACTIVE'");
            default -> { }
        }
    }

    private Object dto(String resource, Object entity) {
        var id = em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        return switch (resource) {
            case "people" -> people.findById(id.toString());
            case "learners" -> learners.findById(id.toString());
            case "organizations" -> organizations.findById(id.toString());
            case "contracts" -> contracts.findById(id.toString());
            case "cohorts" -> cohorts.findById(id.toString());
            case "lessons" -> lessons.findById(id.toString());
            case "activities" -> activities.findById(id.toString());
            case "notifications" -> notifications.findById(id.toString());
            case "users" -> users.findById(id.toString());
            case "roles" -> roles.findById(((Number) id).shortValue());
            case "document-types" -> documentTypes.findById(((Number) id).shortValue());
            default -> throw new IllegalArgumentException(resource);
        };
    }
    private Option option(String resource, Object entity, boolean admin) {
        var id = em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity).toString();
        String label = switch (resource) {
            case "people" -> ((Person) entity).getFullName();
            case "learners" -> { var e = (Learner) entity; yield e.getRegistrationNumber() + (admin ? " · " + e.getPerson().getFullName() : ""); }
            case "organizations" -> { var e = (Organization) entity; yield e.getTradeName() == null || e.getTradeName().isBlank() ? e.getLegalName() : e.getTradeName(); }
            case "contracts" -> { var e = (Contract) entity; yield (admin ? e.getLearner().getPerson().getFullName() : e.getLearner().getRegistrationNumber()) + " · " + e.getId(); }
            case "cohorts" -> { var e = (Cohort) entity; yield e.getCode() + " · " + e.getName(); }
            case "lessons" -> ((Lesson) entity).getTitle();
            case "users" -> ((User) entity).getName() + " · " + ((User) entity).getEmail();
            default -> throw new IllegalArgumentException(resource);
        };
        return new Option(id, label);
    }
}
