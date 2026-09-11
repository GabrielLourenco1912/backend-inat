package dev.lourencogabriel.inatead.person.service;

import dev.lourencogabriel.inatead.person.model.PersonType;
import dev.lourencogabriel.inatead.person.model.PersonTypeCode;
import dev.lourencogabriel.inatead.person.repository.PersonTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PersonTypeCatalogInitializer implements ApplicationRunner {

    private final PersonTypeRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean firstInitialization = repository.count() == 0;
        for (PersonTypeCode code : PersonTypeCode.values()) {
            if (!repository.existsById(code)) {
                repository.save(new PersonType(code));
            }
        }
        repository.flush();
        // Classify legacy records once. Later restarts must preserve administrators' choices.
        if (firstInitialization) {
            jdbcTemplate.update("""
                    insert into person_type_assignments (person_id, person_type_code)
                    select existing.person_id, existing.type_code
                    from (
                        select u.person_id, r.code as type_code
                        from users u
                        join user_roles ur on ur.user_id = u.id
                        join roles r on r.id = ur.role_id
                        join person_types pt on pt.code = r.code
                        union
                        select person_id, 'LEARNER' from learners
                        union
                        select guardian_person_id, 'GUARDIAN' from learner_guardians
                        union
                        select instructor_person_id, 'INSTRUCTOR' from lessons
                        union
                        select m.person_id, pt.code
                        from organization_memberships m
                        join person_types pt on pt.code = m.membership_role
                    ) existing
                    where existing.person_id is not null
                      and not exists (
                        select 1 from person_type_assignments assigned
                        where assigned.person_id = existing.person_id
                          and assigned.person_type_code = existing.type_code
                      )
                    """);
        }
    }
}
