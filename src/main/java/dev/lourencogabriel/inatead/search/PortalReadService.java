package dev.lourencogabriel.inatead.search;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.time.BusinessTime;
import dev.lourencogabriel.inatead.shared.model.UserAccountStatus;
import dev.lourencogabriel.inatead.user.model.SystemRoleCode;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Bounded read models. Filtering, authorization, counts and limits all run in SQL. */
@Service
@RequiredArgsConstructor
public class PortalReadService {
    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository users;
    private final DomainAuthorization authorization;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper mapper;

    public record Actor(String userId, String personId, Set<String> roles) {
        public boolean admin() { return roles.contains("ADMIN"); }
        public boolean instructor() { return roles.contains("INSTRUCTOR"); }
        public boolean manager() { return roles.contains("EMPLOYER_MANAGER"); }
    }

    public Actor actor(Authentication authentication) {
        var id = authorization.userId(authentication);
        if (id == null) throw new AccessDeniedException("Authentication required");
        var user = users.findWithRolesByUlid(id).filter(u -> u.getStatus() == UserAccountStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Active account required"));
        return new Actor(id, user.getPerson() == null ? null : user.getPerson().getId(),
                user.getUserRoles().stream().map(r -> r.getRole().getCode()).filter(SystemRoleCode::isSupported).collect(Collectors.toSet()));
    }

    static class Query {
        String from, order, status;
        final Map<String, String> columns = new LinkedHashMap<>();
        final List<String> where = new ArrayList<>(), search = new ArrayList<>();
        final Map<String, Object> args = new HashMap<>();
        Query(String from, String order) { this.from = from; this.order = order; }
        Query add(String alias, String... columns) {
            for (String column : columns) field(camel(column), alias + "." + column);
            return this;
        }
        Query field(String name, String expression) { columns.put(name, expression); return this; }
        Query find(String... expressions) { search.addAll(List.of(expressions)); return this; }
        void eq(Map<String, String> filters, String key, String column) {
            String value = filters.get(key);
            if (value != null && !value.isBlank()) { where.add(column + " = :" + key); args.put(key, value); }
        }
        String tail() { return " from " + from + (where.isEmpty() ? "" : " where " + String.join(" and ", where)); }
    }

    private static String camel(String name) {
        var b = new StringBuilder(); boolean upper = false;
        for (char c : name.toCharArray()) { if (c == '_') upper = true; else { b.append(upper ? Character.toUpperCase(c) : c); upper = false; } }
        return b.toString();
    }
    private static final String MANAGED = "select m.organization_id from organization_memberships m where m.person_id=:actorPerson and m.status='ACTIVE' and m.start_date<=:today and (m.end_date is null or m.end_date>=:today) and exists(select 1 from organization_type_assignments ot where ot.organization_id=m.organization_id and ot.organization_type_code='EMPLOYER')";
    private String lessonScope(Actor actor, String alias) {
        return "(" + (actor.instructor() ? alias + ".instructor_person_id=:actorPerson or " : "")
                + "exists(select 1 from lesson_participants sp join learners sl on sl.id=sp.learner_id where sp.lesson_id=" + alias + ".id and sl.person_id=:actorPerson))";
    }
    private String learnerScope(Actor actor, String alias) {
        return "(" + alias + ".person_id=:actorPerson"
                + (actor.instructor() ? " or exists(select 1 from lesson_participants sp join lessons sx on sx.id=sp.lesson_id where sp.learner_id=" + alias + ".id and sx.instructor_person_id=:actorPerson)" : "")
                + (actor.manager() ? " or exists(select 1 from contracts sc where sc.learner_id=" + alias + ".id and sc.employer_id in (" + MANAGED + "))" : "") + ")";
    }
    private String contractScope(Actor actor, String alias) {
        return "(exists(select 1 from learners sl where sl.id=" + alias + ".learner_id and sl.person_id=:actorPerson)"
                + (actor.manager() ? " or " + alias + ".employer_id in (" + MANAGED + ")" : "") + ")";
    }
    private void admin(Actor actor) { if (!actor.admin()) throw new AccessDeniedException("Administrator required"); }
    private void allowed(boolean permitted) { if (!permitted) throw new AccessDeniedException("Context access required"); }
    private static Query timestamps(Query q, String alias) { return q.add(alias, "created_at", "updated_at"); }
    private static void address(Query q, String alias) {
        for (String column : List.of("id", "postal_code", "street", "street_number", "address_line_2", "district", "city", "state_code", "country_code")) q.field("address." + camel(column), alias + "." + column);
    }
    private static void file(Query q) {
        for (String column : List.of("id", "original_name", "mime_type", "size_bytes", "checksum_sha256", "created_at", "uploaded_by_user_id")) q.field("file." + camel(column), "f." + column);
    }
    private static void lesson(Query q, String alias) {
        q.add(alias, "id", "cohort_id", "instructor_person_id", "title", "starts_at", "ends_at", "delivery_mode", "room", "meeting_url", "external_lesson_url", "status")
                .field("description", "''").field("cohortCode", "ch.code").field("cohortName", "ch.name").field("instructorName", "ip.full_name");
        timestamps(q, alias);
    }
    private static void participant(Query q, String prefix) {
        for (String column : List.of("id", "lesson_id", "learner_id", "source_enrollment_id", "assigned_by_user_id", "participation_type", "status", "assignment_reason", "assigned_at", "updated_at")) q.field(prefix + camel(column), "e." + column);
        q.field(prefix + "learnerName", "p.full_name").field(prefix + "learnerRegistrationNumber", "l.registration_number");
    }
    private static void attendance(Query q, String prefix) {
        for (String column : List.of("id", "lesson_participant_id", "recorded_by_user_id", "status", "check_in_at", "check_out_at", "recorded_at", "notes", "updated_at")) q.field(prefix + camel(column), "ar." + column);
        q.field(prefix + "lessonId", "e.lesson_id").field(prefix + "learnerId", "e.learner_id");
    }

    private Query query(String resource, Map<String, String> f, Actor a, Authentication auth) {
        Query q;
        switch (resource) {
            case "people" -> {
                admin(a);
                q = timestamps(new Query("people e join addresses ad on ad.id=e.address_id", "e.full_name,e.id"), "e")
                        .add("e", "id", "full_name", "tax_id", "contact_email", "phone_number", "birth_date", "gender", "status").find("e.full_name", "e.tax_id", "e.contact_email", "e.phone_number", "ad.city");
                address(q, "ad"); q.status = "e.status";
                q.search.add("(select group_concat(concat(pt.person_type_code,' ',case pt.person_type_code when 'ADMIN' then 'administrador' when 'INSTRUCTOR' then 'instrutor' when 'LEARNER' then 'aprendiz' when 'GUARDIAN' then 'responsável' else 'gestor de empresa' end)) from person_type_assignments pt where pt.person_id=e.id)");
                if (f.containsKey("personType") && !f.get("personType").isBlank()) {
                    enumValue(f, "personType", "ADMIN", "INSTRUCTOR", "LEARNER", "EMPLOYER_MANAGER", "GUARDIAN");
                    q.where.add("exists(select 1 from person_type_assignments pt where pt.person_id=e.id and pt.person_type_code=:personType)"); q.args.put("personType", f.get("personType"));
                }
            }
            case "learners" -> {
                q = timestamps(new Query("learners e join people p on p.id=e.person_id left join contracts c on c.id=(select sc.id from contracts sc where sc.learner_id=e.id order by case when sc.status='ACTIVE' and sc.start_date<=:today and (sc.end_date is null or sc.end_date>=:today) then 0 else 1 end,sc.start_date desc,sc.id desc limit 1) left join organizations o on o.id=c.employer_id left join cohort_enrollments ce on ce.id=(select se.id from cohort_enrollments se where se.contract_id=c.id order by case when se.status='ACTIVE' and se.start_date<=:today and (se.end_date is null or se.end_date>=:today) then 0 else 1 end,se.start_date desc,se.id desc limit 1) left join cohorts ch on ch.id=ce.cohort_id", "e.registration_number,e.id"), "e")
                        .add("e", "id", "person_id", "registration_number", "has_completed_high_school", "status")
                        .field("fullName", "p.full_name").field("companyName", "coalesce(nullif(o.trade_name,''),o.legal_name)").field("cohortCode", "ch.code").find("p.full_name", "e.registration_number", "o.legal_name", "o.trade_name", "ch.code", "ch.name");
                q.status = "e.status";
                if (!a.admin()) q.where.add(learnerScope(a, "e"));
                if (f.containsKey("organizationId")) {
                    q.where.add("exists(select 1 from contracts oc where oc.learner_id=e.id and (oc.employer_id=:organizationId or oc.school_id=:organizationId))"); q.args.put("organizationId", f.get("organizationId"));
                }
                q.eq(f, "personId", "e.person_id");
            }
            case "organizations" -> {
                q = timestamps(new Query("organizations e join addresses ad on ad.id=e.address_id left join organizations parent on parent.id=e.parent_organization_id", "coalesce(nullif(e.trade_name,''),e.legal_name),e.id"), "e")
                        .add("e", "id", "parent_organization_id", "legal_name", "trade_name", "tax_id", "contact_email", "phone_number", "attendance_closing_day", "status")
                        .field("parentOrganizationName", "coalesce(nullif(parent.trade_name,''),parent.legal_name)").find("e.legal_name", "e.trade_name", "e.tax_id", "e.contact_email", "ad.city");
                address(q, "ad"); q.status = "e.status";
                if (!a.admin()) { allowed(a.manager()); q.where.add("e.id in (" + MANAGED + ")"); }
            }
            case "contracts" -> {
                q = timestamps(new Query("contracts e join learners l on l.id=e.learner_id join people p on p.id=l.person_id join organizations o on o.id=e.employer_id left join organizations school on school.id=e.school_id", "e.start_date desc,e.id desc"), "e")
                        .add("e", "id", "learner_id", "employer_id", "school_id", "start_date", "end_date", "monthly_salary", "weekly_workload_minutes", "status")
                        .field("learnerName", "p.full_name").field("learnerRegistrationNumber", "l.registration_number").field("employerName", "coalesce(nullif(o.trade_name,''),o.legal_name)").field("schoolName", "coalesce(nullif(school.trade_name,''),school.legal_name)")
                        .find("p.full_name", "l.registration_number", "o.legal_name", "o.trade_name", "school.legal_name", "e.start_date", "e.end_date", "e.monthly_salary"); q.status = "e.status";
                if (!a.admin()) q.where.add(contractScope(a, "e"));
                q.eq(f, "learnerId", "e.learner_id");
                if (f.containsKey("organizationId")) { q.where.add("(e.employer_id=:organizationId or e.school_id=:organizationId)"); q.args.put("organizationId", f.get("organizationId")); }
            }
            case "cohorts" -> {
                admin(a); q = timestamps(new Query("cohorts e", "e.start_date desc,e.id desc"), "e").add("e", "id", "code", "name", "default_weekday", "shift_code", "start_date", "end_date", "max_learners", "status").find("e.code", "e.name"); q.status = "e.status";
                q.field("enrollmentCount", "(select count(*) from cohort_enrollments ce where ce.cohort_id=e.id)")
                        .field("reservedCount", "(select count(*) from cohort_enrollments ce where ce.cohort_id=e.id and ce.status in ('PENDING','ACTIVE'))")
                        .field("hasOpenLessons", "exists(select 1 from lessons sl where sl.cohort_id=e.id and sl.status in ('SCHEDULED','IN_PROGRESS'))");
            }
            case "lessons" -> {
                q = new Query("lessons e join cohorts ch on ch.id=e.cohort_id join people ip on ip.id=e.instructor_person_id", "e.starts_at,e.id"); lesson(q, "e"); q.status = "e.status";
                q.find("e.title", "e.description", "ch.code", "ch.name", "ip.full_name", "e.delivery_mode", "e.starts_at");
                q.search.add("case e.delivery_mode when 'ONSITE' then 'presencial' else 'online' end");
                if (!a.admin()) q.where.add(lessonScope(a, "e"));
                q.eq(f, "cohortId", "e.cohort_id"); q.eq(f, "deliveryMode", "e.delivery_mode"); enumValue(f, "deliveryMode", "ONLINE", "ONSITE");
                period(q, f, "e.starts_at");
                if ("true".equals(f.get("upcoming"))) q.where.add("e.ends_at>=:now and e.status in ('SCHEDULED','IN_PROGRESS')");
            }
            case "activities" -> {
                q = timestamps(new Query("activities e join lessons ls on ls.id=e.lesson_id", "e.available_at desc,e.id desc"), "e")
                        .add("e", "id", "lesson_id", "created_by_user_id", "title", "available_at", "due_at", "max_score", "status").field("description", "''").field("lessonTitle", "ls.title").find("e.title", "ls.title", "e.due_at"); q.status = "e.status";
                if (!a.admin()) { q.where.add(lessonScope(a, "ls")); if (!a.instructor()) q.where.add("e.status<>'DRAFT'"); else q.where.add("(e.status<>'DRAFT' or ls.instructor_person_id=:actorPerson)"); }
                q.eq(f, "lessonId", "e.lesson_id");
                if (f.containsKey("learnerId")) { q.where.add("exists(select 1 from lesson_participants lp where lp.lesson_id=e.lesson_id and lp.learner_id=:learnerId)"); q.args.put("learnerId", f.get("learnerId")); }
                String self = f.getOrDefault("learnerId", "");
                if (!a.admin() && !a.instructor()) self = learnerId(a);
                q.args.put("submissionLearner", self);
                q.field("submissionStatus", "(select s.status from activity_submissions s where s.activity_id=e.id and s.learner_id=:submissionLearner)")
                        .field("submissionId", "(select s.id from activity_submissions s where s.activity_id=e.id and s.learner_id=:submissionLearner)")
                        .field("pendingReviewCount", "(select count(*) from activity_submissions s where s.activity_id=e.id and s.status in ('SUBMITTED','LATE'))");
                String workflow = f.getOrDefault("workflow", "");
                if (!workflow.isBlank()) switch (workflow) {
                    case "TODO" -> q.where.add("e.status='PUBLISHED' and not exists(select 1 from activity_submissions s where s.activity_id=e.id and s.learner_id=:submissionLearner)");
                    case "REVIEW" -> { allowed(a.admin() || a.instructor()); q.where.add("exists(select 1 from activity_submissions s where s.activity_id=e.id and s.status in ('SUBMITTED','LATE'))"); }
                    case "RETURNED", "GRADED", "SUBMITTED" -> { q.args.put("workflow", workflow); q.where.add("exists(select 1 from activity_submissions s where s.activity_id=e.id and s.learner_id=:submissionLearner and (s.status=:workflow" + (workflow.equals("SUBMITTED") ? " or s.status='LATE'" : "") + "))"); }
                    default -> throw bad("Invalid activity workflow");
                }
            }
            case "cohort-enrollments" -> {
                admin(a); q = timestamps(new Query("cohort_enrollments e join contracts c on c.id=e.contract_id join learners l on l.id=c.learner_id join people p on p.id=l.person_id join organizations o on o.id=c.employer_id join cohorts ch on ch.id=e.cohort_id", "e.start_date desc,e.id desc"), "e")
                        .add("e", "id", "contract_id", "cohort_id", "start_date", "end_date", "status").field("learnerId", "l.id").field("learnerName", "p.full_name").field("learnerRegistrationNumber", "l.registration_number").field("employerName", "coalesce(nullif(o.trade_name,''),o.legal_name)")
                        .field("contractStatus", "c.status").field("contractStartDate", "c.start_date").field("contractEndDate", "c.end_date").field("cohortCode", "ch.code").field("cohortName", "ch.name").find("p.full_name", "l.registration_number", "o.legal_name", "o.trade_name", "ch.code", "ch.name", "e.contract_id"); q.status = "e.status";
                q.eq(f, "cohortId", "e.cohort_id"); q.eq(f, "contractId", "e.contract_id"); q.eq(f, "learnerId", "l.id");
            }
            case "organization-memberships" -> {
                admin(a); q = timestamps(new Query("organization_memberships e join people p on p.id=e.person_id", "e.start_date desc,e.id desc"), "e")
                        .add("e", "id", "person_id", "organization_id", "membership_role", "job_title", "start_date", "end_date", "status")
                        .field("personName", "p.full_name").field("contactEmail", "p.contact_email").field("phoneNumber", "p.phone_number").find("p.full_name", "e.membership_role", "e.job_title", "p.contact_email", "p.phone_number"); q.status = "e.status"; q.eq(f, "organizationId", "e.organization_id");
            }
            case "learner-guardians" -> {
                admin(a); q = timestamps(new Query("learner_guardians e join people p on p.id=e.guardian_person_id left join addresses ad on ad.id=p.address_id", "p.full_name,e.guardian_person_id"), "e")
                        .add("e", "learner_id", "guardian_person_id", "relationship_type").field("id", "e.guardian_person_id").field("guardianName", "p.full_name").field("legalGuardian", "e.is_legal_guardian").field("primaryContact", "e.is_primary_contact").field("city", "ad.city").field("stateCode", "ad.state_code").field("contactEmail", "p.contact_email").field("phoneNumber", "p.phone_number").find("p.full_name", "e.relationship_type", "p.contact_email", "p.phone_number"); q.eq(f, "learnerId", "e.learner_id");
            }
            case "lesson-participants", "lesson-attendance", "learner-lessons" -> {
                q = new Query("lesson_participants e join learners l on l.id=e.learner_id join people p on p.id=l.person_id join lessons ls on ls.id=e.lesson_id join cohorts ch on ch.id=ls.cohort_id join people ip on ip.id=ls.instructor_person_id left join attendance_records ar on ar.lesson_participant_id=e.id", "p.full_name,e.id");
                q.eq(f, "lessonId", "e.lesson_id"); q.eq(f, "learnerId", "e.learner_id");
                if (resource.equals("learner-lessons")) {
                    lesson(q, "ls"); attendance(q, "attendance."); q.order="ls.starts_at desc,ls.id desc"; q.status="coalesce(ar.status,ls.status)"; q.find("ls.title", "ch.code", "ls.delivery_mode", "ls.starts_at");
                    q.search.add("case ls.delivery_mode when 'ONSITE' then 'presencial' else 'online' end");
                    if (!a.admin()) q.where.add(lessonScope(a, "ls"));
                } else {
                    allowed(a.admin() || a.instructor()); if (!a.admin()) q.where.add("ls.instructor_person_id=:actorPerson");
                    participant(q, resource.equals("lesson-attendance") ? "participant." : ""); q.status="e.status";
                    if (resource.equals("lesson-attendance")) attendance(q, "attendance.");
                    q.find("p.full_name", "l.registration_number", "e.assignment_reason", "e.participation_type");
                    q.search.add("case e.participation_type when 'TRANSFERRED' then 'remanejado' when 'MAKEUP' then 'reposição' when 'EXTRA' then 'extra' else 'regular' end");
                    if ("true".equals(f.get("eligibleMissing"))) q.where.add("ar.id is null and e.status='EXPECTED'");
                    q.eq(f, "participationType", "e.participation_type"); enumValue(f,"participationType","REGULAR","TRANSFERRED","MAKEUP","EXTRA");
                    String state=f.getOrDefault("attendanceStatus", "");
                    if (state.equals("MISSING")) q.where.add("ar.id is null"); else { enumValue(f,"attendanceStatus","PRESENT","ABSENT","EXCUSED","LATE","PARTIAL"); q.eq(f,"attendanceStatus","ar.status"); }
                }
            }
            case "activity-submissions" -> {
                q = timestamps(new Query("activity_submissions e join learners l on l.id=e.learner_id join people p on p.id=l.person_id join activities act on act.id=e.activity_id join lessons ls on ls.id=act.lesson_id", "e.created_at desc,e.id desc"), "e")
                        .add("e", "id", "activity_id", "learner_id", "graded_by_user_id", "status", "submitted_at", "score", "graded_at").field("learnerName", "p.full_name").field("learnerRegistrationNumber", "l.registration_number").find("p.full_name", "l.registration_number"); q.status="e.status";
                q.eq(f,"activityId","e.activity_id"); q.eq(f,"learnerId","e.learner_id");
                if (!a.admin()) q.where.add("(l.person_id=:actorPerson" + (a.instructor() ? " or ls.instructor_person_id=:actorPerson" : "") + ")");
            }
            case "person-documents", "contract-documents" -> {
                admin(a); boolean personal=resource.equals("person-documents");
                q = new Query((personal ? "person_documents" : "contract_documents") + " e join document_types dt on dt.id=e.document_type_id join files f on f.id=e.file_id" + (personal ? " join people p on p.id=e.person_id" : ""), "e.created_at desc,e.id desc");
                q.add("e","id","document_type_id","created_at").field("documentTypeName","dt.name"); file(q);
                q.find("dt.name","dt.code","f.original_name"); q.eq(f,"documentTypeId","e.document_type_id");
                if (personal) {
                    q.add("e","person_id","document_number","issued_on","expires_on","verification_status","verified_by_user_id","verified_at","updated_at").field("personName","p.full_name").field("learnerId","(select l.id from learners l where l.person_id=p.id)"); q.search.addAll(List.of("p.full_name","e.document_number")); q.eq(f,"personId","e.person_id");
                    enumValue(f,"verificationStatus","VERIFIED","EXPIRED"); q.eq(f,"verificationStatus","e.verification_status"); q.status="e.verification_status";
                    if ("EXPIRED".equals(f.get("verificationStatus"))) q.order="e.expires_on,e.id";
                } else { q.add("e","contract_id","version_number").field("current","e.is_current");q.search.add("e.version_number"); q.eq(f,"contractId","e.contract_id"); bool(q,f,"current","e.is_current"); if(f.containsKey("documentTypeId")) q.order="e.version_number desc,e.id desc"; }
            }
            case "users" -> {
                admin(a); q=timestamps(new Query("users e join people p on p.id=e.person_id", "e.display_name,e.id"),"e").add("e","id","person_id","display_name","login_email","status","email_verified_at","locked_until","last_login_at").field("personName","p.full_name").find("e.display_name","e.login_email"); q.status="e.status";
                String text=f.getOrDefault("q", "").trim(); if(!text.isEmpty()) q.search.add("(select group_concat(concat(r.code,' ',r.name)) from user_roles ur join roles r on r.id=ur.role_id where ur.user_id=e.id)");
            }
            case "roles", "document-types" -> {
                admin(a); q=new Query(resource.equals("roles") ? "roles e" : "document_types e", "e.name,e.id").add("e","id","code","name","description").find("e.code","e.name","e.description");
                if(resource.equals("roles")) q.where.add("e.code in ('ADMIN','INSTRUCTOR','LEARNER','EMPLOYER_MANAGER')");
                else {
                    q.add("e","scope"); String purpose=f.getOrDefault("purpose", "");
                    if(purpose.equals("person-document")) { q.where.add("e.scope in ('PERSON','BOTH') and not exists(select 1 from person_documents pd where pd.document_type_id=e.id and pd.person_id=:contextId)"); q.args.put("contextId", required(f,"contextId")); }
                    else if(purpose.equals("contract-document")) q.where.add("e.scope in ('CONTRACT','BOTH')");
                }
            }
            case "notifications" -> {
                admin(a); q=timestamps(new Query("notifications e left join users au on au.id=e.audience_id and e.audience_type='USER' left join cohorts ac on ac.id=e.audience_id and e.audience_type='COHORT'", "e.created_at desc,e.id desc"),"e")
                        .add("e","id","title","priority","scheduled_at","expires_at").field("audience.type","e.audience_type").field("audience.targetId","e.audience_id").field("audienceName","case when e.audience_type='ALL' then 'Todos' when e.audience_type='USER' then au.display_name else ac.code end").find("e.title","e.message","au.display_name","ac.code","ac.name");
                for(String state:List.of("FAILED","CANCELLED","SENT","DELIVERED","PENDING")) q.field(camel(state.toLowerCase(Locale.ROOT))+"Count","(select count(*) from notification_recipients nr where nr.notification_id=e.id and nr.delivery_status='"+state+"')");
                q.field("deliveryCount","(select count(*) from notification_recipients nr where nr.notification_id=e.id)").field("channels","(select group_concat(distinct nr.channel) from notification_recipients nr where nr.notification_id=e.id)"); q.eq(f,"priority","e.priority");
            }
            case "notification-recipients" -> {
                q=timestamps(new Query("notification_recipients e join notifications n on n.id=e.notification_id join users u on u.id=e.recipient_user_id", "e.created_at desc,e.id desc"),"e")
                        .add("e","id","notification_id","recipient_user_id","channel","delivery_status","sent_at","delivered_at","read_at","failure_reason")
                        .field("recipientName","u.display_name").field("title","n.title").field("message","n.message").field("priority","n.priority").field("actionUrl","n.action_url").field("scheduledAt","n.scheduled_at").field("expiresAt","n.expires_at").find("n.title","n.message","u.display_name","e.failure_reason");
                if("true".equals(f.get("mine"))) { q.where.add("e.recipient_user_id=:actorUser and (n.scheduled_at is null or n.scheduled_at<=:now) and (n.expires_at is null or n.expires_at>:now)"); }
                else admin(a);
                q.eq(f,"notificationId","e.notification_id"); q.eq(f,"channel","e.channel"); enumValue(f,"channel","IN_APP","EMAIL","PUSH");
                q.eq(f,"deliveryStatus","e.delivery_status"); enumValue(f,"deliveryStatus","PENDING","SENT","DELIVERED","FAILED","CANCELLED"); q.eq(f,"priority","n.priority");
                if(f.containsKey("read") && !f.get("read").isBlank()) { enumValue(f,"read","true","false"); q.where.add("e.read_at is " + (f.get("read").equals("true") ? "not " : "") + "null"); }
            }
            case "status-history", "school-history", "document-history" -> {
                admin(a);
                if(resource.equals("status-history")) { q=new Query("lifecycle_status_history e", "e.changed_at desc,e.id desc").add("e","id","previous_status","new_status","change_reason","reason_detail","changed_by_user_id","changed_at").find("e.previous_status","e.new_status","e.reason_detail"); enumValue(f,"aggregateType","CONTRACT","COHORT","COHORT_ENROLLMENT"); q.eq(f,"aggregateType","e.aggregate_type"); q.eq(f,"aggregateId","e.aggregate_id"); }
                else if(resource.equals("document-history")) { q=new Query("person_document_status_history e", "e.changed_at desc,e.id desc").add("e","id","previous_status","new_status","change_reason","changed_by_user_id","changed_at").find("e.previous_status","e.new_status","e.change_reason"); q.eq(f,"documentId","e.person_document_id"); }
                else { q=new Query("contract_school_changes e left join organizations old on old.id=e.previous_school_id left join organizations ne on ne.id=e.new_school_id left join users u on u.id=e.changed_by_user_id", "e.changed_at desc,e.id desc").add("e","id","previous_school_id","new_school_id","changed_by_user_id","changed_at").field("previousSchoolName","coalesce(nullif(old.trade_name,''),old.legal_name)").field("newSchoolName","coalesce(nullif(ne.trade_name,''),ne.legal_name)").field("changedByName","u.display_name").find("old.legal_name","ne.legal_name","u.display_name"); q.eq(f,"contractId","e.contract_id"); }
            }
            case "lesson-files", "activity-files", "submission-files" -> {
                String context=required(f,"contextId");
                if(resource.equals("lesson-files")) allowed(authorization.canAccessLesson(auth,context));
                else if(resource.equals("activity-files")) allowed(authorization.canAccessActivity(auth,context));
                else allowed(authorization.canAccessSubmission(auth,context));
                String table=resource.replace('-','_'), column=resource.equals("lesson-files") ? "lesson_id" : resource.equals("activity-files") ? "activity_id" : "submission_id";
                q=new Query(table+" e join files f on f.id=e.file_id",resource.equals("submission-files") ? "e.created_at desc,e.file_id" : "e.sort_order,e.file_id")
                        .add("e",column,"created_at").field("id","e.file_id").find("f.original_name","f.mime_type"); if(!resource.equals("submission-files")) q.add("e","sort_order"); file(q); q.where.add("e."+column+"=:contextId"); q.args.put("contextId",context);
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown read resource");
        }
        q.args.put("actorPerson", a.personId()); q.args.put("actorUser", a.userId()); q.args.put("today", BusinessTime.today(clock)); q.args.put("now", LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        if(f.containsKey("id")) { q.where.add(q.columns.get("id")+"=:id"); q.args.put("id",f.get("id")); }
        String status=f.getOrDefault("status", "");
        if(!status.isBlank() && resource.equals("learner-guardians")) {
            enumValue(f,"status","PRIMARY","LEGAL","OTHER");
            q.where.add(switch(status) { case "PRIMARY" -> "e.is_primary_contact=true"; case "LEGAL" -> "e.is_legal_guardian=true"; default -> "e.is_primary_contact=false and e.is_legal_guardian=false"; });
        } else if(!status.isBlank()) {
            if(q.status==null) throw bad("Status filter not supported");
            Set<String> valid=switch(resource) {
                case "people","learners","organizations","organization-memberships" -> Set.of("ACTIVE","INACTIVE","SUSPENDED");
                case "contracts" -> Set.of("DRAFT","ACTIVE","SUSPENDED","ENDED","CANCELLED");
                case "cohorts" -> Set.of("PLANNED","ACTIVE","COMPLETED","CANCELLED");
                case "cohort-enrollments" -> Set.of("PENDING","ACTIVE","COMPLETED","CANCELLED");
                case "lessons" -> Set.of("SCHEDULED","IN_PROGRESS","COMPLETED","CANCELLED");
                case "activities" -> Set.of("DRAFT","PUBLISHED","CLOSED","CANCELLED");
                case "activity-submissions" -> Set.of("DRAFT","SUBMITTED","LATE","RETURNED","GRADED");
                case "lesson-participants","lesson-attendance" -> Set.of("EXPECTED","CANCELLED");
                case "users" -> Set.of("INVITED","ACTIVE","LOCKED","DISABLED");
                case "person-documents" -> Set.of("VERIFIED","EXPIRED");
                case "learner-lessons" -> Set.of("SCHEDULED","IN_PROGRESS","COMPLETED","CANCELLED","PRESENT","ABSENT","LATE","PARTIAL","EXCUSED");
                default -> Set.of();
            };
            if(!valid.contains(status)) throw bad("Invalid status"); q.where.add(q.status+"=:status"); q.args.put("status",status);
        }
        enumValue(f,"priority","LOW","NORMAL","HIGH","URGENT");
        String text=f.getOrDefault("q", "").trim();
        if(text.matches("\\d{2}/\\d{2}/\\d{4}")) {
            try { text=LocalDate.of(Integer.parseInt(text.substring(6)),Integer.parseInt(text.substring(3,5)),Integer.parseInt(text.substring(0,2))).toString(); } catch(DateTimeException ignored) { }
        } else if(text.matches("[0-9.]+,[0-9]{2}")) text=text.replace(".","").replace(',','.');
        if(f.containsKey("documentTypeId")&&!f.get("documentTypeId").isBlank()) integer(f,"documentTypeId",1,1,32767);
        if(text.length()>160) throw bad("Search text exceeds 160 characters");
        if(!text.isEmpty() && !q.search.isEmpty()) {
            q.args.put("q","%"+text.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            q.where.add("("+q.search.stream().map(expr -> "lower(concat('',"+expr+")) like :q escape '!' ").collect(Collectors.joining(" or "))+")");
        }
        if(resource.equals("lesson-attendance") && f.containsKey("previewFill")) {
            String json=f.get("previewFill"); if(json.length()>2048) throw bad("Invalid fill preview");
            try {
                @SuppressWarnings("unchecked") Map<String,String> raw=mapper.readValue(json,Map.class);
                var selection=attendanceFilters(required(f,"lessonId"),raw);
                Query preview=query(resource,selection,a,auth);
                String predicate=String.join(" and ",preview.where);
                for(var argument:preview.args.entrySet()) { predicate=predicate.replaceAll(":"+java.util.regex.Pattern.quote(argument.getKey())+"\\b",":fill_"+argument.getKey());q.args.put("fill_"+argument.getKey(),argument.getValue()); }
                q.field("matchesPendingFill","case when "+predicate+" then true else false end");
            } catch (Exception ex) { throw bad("Invalid fill preview"); }
        }
        return q;
    }

    public static Map<String,String> attendanceFilters(String lessonId,Map<String,String> source) {
        Map<String,String> filters=new HashMap<>();
        for(String key:List.of("q","status","participationType","attendanceStatus")) if(source.get(key)!=null) filters.put(key,source.get(key));
        filters.put("lessonId",lessonId); filters.put("eligibleMissing","true"); return filters;
    }

    @Transactional(readOnly=true)
    public long count(String resource,Map<String,String> filters,Authentication auth) {
        Query q=query(resource,filters,actor(auth),auth);
        return Objects.requireNonNull(jdbc.queryForObject("select count(*)"+q.tail(),q.args,Long.class));
    }

    private String learnerId(Actor a) {
        return jdbc.query("select id from learners where person_id=:person", Map.of("person",Objects.requireNonNullElse(a.personId(),"")), (rs,i)->rs.getString(1)).stream().findFirst().orElse("");
    }
    private static String required(Map<String,String> f,String key) { String v=f.get(key); if(v==null||v.isBlank()) throw bad(key+" is required"); return v; }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static void enumValue(Map<String,String> f,String key,String... values) { String v=f.get(key); if(v!=null&&!v.isBlank()&&!List.of(values).contains(v)) throw bad("Invalid "+key); }
    private static void bool(Query q,Map<String,String> f,String key,String column) { if(f.containsKey(key)&&!f.get(key).isBlank()) { enumValue(f,key,"true","false"); q.where.add(column+"=:"+key); q.args.put(key,Boolean.parseBoolean(f.get(key))); } }
    private static void period(Query q,Map<String,String> f,String column) {
        try {
            LocalDate start=f.containsKey("startDate")&&!f.get("startDate").isBlank()?LocalDate.parse(f.get("startDate")):null;
            LocalDate end=f.containsKey("endDate")&&!f.get("endDate").isBlank()?LocalDate.parse(f.get("endDate")):null;
            if(start!=null&&end!=null&&end.isBefore(start)) throw bad("endDate cannot precede startDate");
            if(start!=null) { q.where.add(column+">=:startDate"); q.args.put("startDate",LocalDateTime.ofInstant(start.atStartOfDay(BusinessTime.ZONE).toInstant(),ZoneOffset.UTC)); }
            if(end!=null) { q.where.add(column+"<:endDate"); q.args.put("endDate",LocalDateTime.ofInstant(end.plusDays(1).atStartOfDay(BusinessTime.ZONE).toInstant(),ZoneOffset.UTC)); }
        } catch(DateTimeException ex) { throw bad("Invalid date"); }
    }

    @Transactional(readOnly=true)
    public PageResponse<Map<String,Object>> page(String resource,Map<String,String> filters,Authentication auth) {
        return page(resource,filters,actor(auth),auth);
    }
    private PageResponse<Map<String,Object>> page(String resource,Map<String,String> f,Actor actor,Authentication auth) {
        int page=integer(f,"page",0,0,Integer.MAX_VALUE), size=integer(f,"size",20,1,100);
        Query q=query(resource,f,actor,auth);
        long total=Objects.requireNonNull(jdbc.queryForObject("select count(*)"+q.tail(),q.args,Long.class));
        q.args.put("limit",size); q.args.put("offset",(long)page*size);
        String select=q.columns.entrySet().stream().map(e->e.getValue()+" as `"+e.getKey()+"`").collect(Collectors.joining(","));
        List<Map<String,Object>> content=total==0||((long)page*size)>=total?new ArrayList<>():jdbc.query("select "+select+q.tail()+" order by "+q.order+" limit :limit offset :offset",q.args,(rs,index)->row(rs,new ArrayList<>(q.columns.keySet())));
        enrich(resource,content);
        int pages=(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size);
        return new PageResponse<>(content,page,size,total,pages,page==0,page>=pages-1);
    }
    public static int integer(Map<String,String> f,String key,int fallback,int min,int max) {
        try { int value=Integer.parseInt(f.getOrDefault(key,String.valueOf(fallback))); if(value<min||value>max) throw bad("Invalid "+key); return value; } catch(NumberFormatException ex) { throw bad("Invalid "+key); }
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> row(ResultSet rs,List<String> labels)throws SQLException {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
            String label=labels.get(i-1); Object value=rs.getObject(i);
            if(value instanceof java.sql.Timestamp) value=utcInstant(rs,i);
            else if(value instanceof LocalDateTime time) value=time.toInstant(ZoneOffset.UTC);
            else if(value instanceof java.sql.Date d) value=d.toLocalDate();
            else if(value instanceof byte[] bytes && bytes.length==1) value=bytes[0]!=0;
            String[] keys=label.split("\\."); Map<String,Object> current=result;
            if(Set.of("hasCompletedHighSchool","current","legalGuardian","primaryContact","hasOpenLessons","matchesPendingFill").contains(keys[keys.length-1]) && value instanceof Number number) value=number.intValue()!=0;
            for(int k=0;k<keys.length-1;k++) current=(Map<String,Object>)current.computeIfAbsent(keys[k],ignored->new LinkedHashMap<>());
            current.put(keys[keys.length-1],value);
        }
        if(result.get("attendance") instanceof Map<?,?> attendance && attendance.get("id")==null) result.put("attendance",null);
        return result;
    }
    private static Instant utcInstant(ResultSet rs,int column) throws SQLException {
        var timestamp=rs.getTimestamp(column,Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return timestamp==null?null:timestamp.toInstant();
    }
    private void enrich(String resource,List<Map<String,Object>> content) {
        if(content.isEmpty()) return;
        if(resource.equals("users")) {
            var ids=content.stream().map(row->row.get("id")).toList();
            Map<String,List<Map<String,Object>>> assignments=new HashMap<>();
            jdbc.query("select ur.user_id,ur.role_id,r.code,r.name,ur.granted_at,ur.granted_by_user_id from user_roles ur join roles r on r.id=ur.role_id where ur.user_id in (:ids) and r.code in ('ADMIN','INSTRUCTOR','LEARNER','EMPLOYER_MANAGER') order by r.code",Map.of("ids",ids),rs->{
                Map<String,Object> grant=new LinkedHashMap<>();grant.put("userId",rs.getString(1));grant.put("roleId",rs.getInt(2));grant.put("roleCode",rs.getString(3));grant.put("roleName",rs.getString(4));grant.put("grantedAt",utcInstant(rs,5));grant.put("grantedByUserId",rs.getString(6));assignments.computeIfAbsent(rs.getString(1),ignored->new ArrayList<>()).add(grant);
            });
            Map<String,List<String>> types=new HashMap<>();
            jdbc.query("select person_id,person_type_code from person_type_assignments where person_id in (:ids) order by person_type_code",Map.of("ids",content.stream().map(row->row.get("personId")).toList()),rs->{types.computeIfAbsent(rs.getString(1),ignored->new ArrayList<>()).add(rs.getString(2));});
            for(var row:content) { var grants=assignments.getOrDefault(row.get("id"),List.of());row.put("roleAssignments",grants);row.put("roles",grants.stream().map(grant->grant.get("roleCode")).toList());row.put("personTypes",types.getOrDefault(row.get("personId"),List.of())); }
            return;
        }
        String table,owner,code,field;
        switch(resource) {
            case "people" -> { table="person_type_assignments"; owner="person_id"; code="person_type_code"; field="personTypes"; }
            case "organizations" -> { table="organization_type_assignments"; owner="organization_id"; code="organization_type_code"; field="organizationTypes"; }
            case "users" -> { table="user_roles ur join roles r on r.id=ur.role_id"; owner="ur.user_id"; code="r.code"; field="roles"; }
            default -> { return; }
        }
        Map<String,List<String>> values=new HashMap<>();
        jdbc.query("select "+owner+" as owner_id,"+code+" as code from "+table+" where "+owner+" in (:ids) order by "+code, Map.of("ids",content.stream().map(r->r.get("id")).toList()),rs->{ values.computeIfAbsent(rs.getString("owner_id"),k->new ArrayList<>()).add(rs.getString("code")); });
        content.forEach(r->r.put(field,values.getOrDefault(r.get("id"),List.of())));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> item(String resource,String id,Authentication auth) {
        var result=page(resource,Map.of("id",id,"size","1"),auth);
        if(result.content().isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found");
        return result.content().getFirst();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> dashboard(Authentication auth) {
        Actor a=actor(auth); Map<String,Object> result=new LinkedHashMap<>();
        for(String resource:List.of("learners","contracts","organizations","lessons","activities")) {
            boolean permitted=a.admin() || switch(resource) { case "learners" -> a.instructor()||a.manager()||a.roles().contains("LEARNER"); case "contracts" -> a.manager()||a.roles().contains("LEARNER"); case "organizations" -> a.manager(); default -> a.instructor()||a.roles().contains("LEARNER"); };
            if(!permitted) { result.put(resource+"Count",0L); continue; }
            Query q=query(resource,Map.of(),a,auth);
            if(resource.equals("activities")) q.where.add("e.status in ('DRAFT','PUBLISHED')");
            result.put(resource+"Count",jdbc.queryForObject("select count(*)"+q.tail(),q.args,Long.class));
        }
        boolean academic=a.admin()||a.instructor()||a.roles().contains("LEARNER");
        result.put("lessons",academic?page("lessons",Map.of("upcoming","true","size","5"),a,auth).content():List.of());
        result.put("notices",page("notification-recipients",Map.of("mine","true","channel","IN_APP","read","false","size","4"),a,auth).content());
        return result;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> organizationSummary(String id,Authentication auth) {
        allowed(authorization.canAccessOrganization(auth,id));
        var args=Map.of("id",id);
        return Map.of("contractCount",count("contracts",Map.of("organizationId",id),auth),
                "learnerCount",count("learners",Map.of("organizationId",id),auth),
                "hasEmployerContracts",Objects.requireNonNull(jdbc.queryForObject("select count(*) from contracts where employer_id=:id",args,Long.class))>0,
                "hasSchoolContracts",Objects.requireNonNull(jdbc.queryForObject("select count(*) from contracts where school_id=:id",args,Long.class))>0);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> versionSummary(String id,short type,Authentication auth) {
        admin(actor(auth));
        var args=Map.of("id",id,"type",type);
        Integer max=jdbc.queryForObject("select coalesce(max(version_number),0) from contract_documents where contract_id=:id and document_type_id=:type",args,Integer.class);
        var current=jdbc.query("select id from contract_documents where contract_id=:id and document_type_id=:type and is_current=true",args,(rs,i)->rs.getString(1));
        Map<String,Object> result=new LinkedHashMap<>();result.put("nextVersion",Objects.requireNonNull(max)+1);result.put("currentId",current.isEmpty()?null:current.getFirst());return result;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> lessonSummary(String id,Authentication auth) {
        allowed(authorization.canAccessLesson(auth,id));
        boolean manage=authorization.canManageLesson(auth,id);
        var args=Map.of("id",id);
        long participants=manage?Objects.requireNonNull(jdbc.queryForObject("select count(*) from lesson_participants where lesson_id=:id",args,Long.class)):0;
        long records=manage?Objects.requireNonNull(jdbc.queryForObject("select count(*) from attendance_records ar join lesson_participants lp on lp.id=ar.lesson_participant_id where lp.lesson_id=:id",args,Long.class)):0;
        long activities=Objects.requireNonNull(jdbc.queryForObject("select count(*) from activities where lesson_id=:id"+(manage?"":" and status<>'DRAFT'"),args,Long.class));
        return Map.of("participantCount",participants,"attendanceCount",records,"activityCount",activities);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> fileSummary(String resource,String id,Authentication auth) {
        boolean lesson=resource.equals("lessons");
        if(!lesson&&!resource.equals("activities")) throw bad("Invalid file context");
        allowed(lesson?authorization.canAccessLesson(auth,id):authorization.canAccessActivity(auth,id));
        String table=lesson?"lesson_files":"activity_files",column=lesson?"lesson_id":"activity_id";
        var args=Map.of("id",id);
        int order=Objects.requireNonNull(jdbc.queryForObject("select coalesce(max(sort_order),-1)+1 from "+table+" where "+column+"=:id",args,Integer.class));
        return Map.of("nextSortOrder",order);
    }
}
