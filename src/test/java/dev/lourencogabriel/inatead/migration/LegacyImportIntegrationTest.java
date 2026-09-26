package dev.lourencogabriel.inatead.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyImportIntegrationTest {

    @Test
    void shouldLoadSelectedSchoolsAndWeekdayCohortsWithValidRelations() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:legacy_import_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var flyway = Flyway.configure().dataSource(dataSource).load();
        flyway.migrate();
        var jdbc = new JdbcTemplate(dataSource);

        assertEquals("4", flyway.info().current().getVersion().toString());
        assertEquals(142, count(jdbc, "select count(*) from organizations"));
        assertEquals(48, count(jdbc, """
                select count(*) from organization_type_assignments
                where organization_type_code = 'SCHOOL'
                """));
        assertEquals(95, count(jdbc, """
                select count(*) from organization_type_assignments
                where organization_type_code = 'EMPLOYER'
                """));
        assertEquals(1652, count(jdbc, "select count(*) from learners"));
        assertEquals(1652, count(jdbc, "select count(distinct registration_number) from learners"));
        assertEquals(1, count(jdbc, "select min(registration_number) from learners"));
        assertEquals(1652, count(jdbc, "select max(registration_number) from learners"));
        assertEquals(55, count(jdbc, "select count(*) from learner_guardians"));
        assertEquals(1, count(jdbc, """
                select count(*) from learner_guardians
                where relationship_type = 'IRMAO'
                  and is_legal_guardian = true and is_primary_contact = true
                """));
        assertEquals(55, count(jdbc, """
                select count(*) from learner_guardians g
                join people p on p.id = g.guardian_person_id
                join legacy_import_audit a
                  on a.entity_table = 'people' and a.entity_id = p.id
                where p.full_name like '[MOCK] RESPONSÁVEL DE %'
                  and a.source_file = 'Cadastro de Aprendizes - Atualizado Paranaguá.xlsx'
                """));
        assertEquals(50, count(jdbc, """
                select count(*) from learner_guardians g
                join people p on p.id = g.guardian_person_id
                where p.phone_number <> '41900000000'
                """));
        assertEquals(5, count(jdbc, """
                select count(*) from learner_guardians g
                join people p on p.id = g.guardian_person_id
                where p.phone_number = '41900000000'
                """));
        assertEquals(1716, count(jdbc, "select count(*) from contracts"));
        assertEquals(1716, count(jdbc, "select count(*) from cohort_enrollments"));
        assertEquals(6, count(jdbc, "select count(*) from cohorts"));
        assertEquals(4, count(jdbc, """
                select count(*) from cohorts
                where default_weekday between 1 and 4 and shift_code = 'NAO_INFORMADO'
                """));
        assertEquals(1, count(jdbc, """
                select count(*) from cohorts
                where code = 'SEXTA_FEIRA_MANHA' and default_weekday = 5 and shift_code = 'MANHA'
                """));
        assertEquals(1, count(jdbc, """
                select count(*) from cohorts
                where code = 'SEXTA_FEIRA_TARDE' and default_weekday = 5 and shift_code = 'TARDE'
                """));
        assertEquals(357, enrollmentCountForCohort(jdbc, "SEGUNDA_FEIRA"));
        assertEquals(260, enrollmentCountForCohort(jdbc, "TERCA_FEIRA"));
        assertEquals(281, enrollmentCountForCohort(jdbc, "QUARTA_FEIRA"));
        assertEquals(276, enrollmentCountForCohort(jdbc, "QUINTA_FEIRA"));
        assertEquals(275, enrollmentCountForCohort(jdbc, "SEXTA_FEIRA_MANHA"));
        assertEquals(267, enrollmentCountForCohort(jdbc, "SEXTA_FEIRA_TARDE"));
        assertEquals("SEXTA_FEIRA_MANHA", cohortCodeForSourceRow(jdbc, "aprendizes_atual.xlsx", 2));
        assertEquals("QUARTA_FEIRA", cohortCodeForSourceRow(jdbc, "aprendizes_historico.xlsx", 274));
        assertEquals("SEGUNDA_FEIRA", cohortCodeForSourceRow(jdbc, "aprendizes_historico.xlsx", 850));
        assertEquals(312, count(jdbc, """
                select count(*) from legacy_import_audit
                where entity_table = 'cohort_enrollments' and notes like '%[MOCK]%'
                """));
        assertEquals(1, count(jdbc, """
                select count(*) from legacy_import_audit
                where entity_table = 'cohort_enrollments'
                  and source_file = 'aprendizes_atual.xlsx' and source_row = 169
                  and notes like '%[MOCK]%'
                """));
        assertEquals(166, count(jdbc, """
                select count(*) from cohort_enrollments e
                join contracts c on c.id = e.contract_id
                join cohorts h on h.id = e.cohort_id
                where c.status = 'ACTIVE' and e.status = 'ACTIVE' and h.status = 'ACTIVE'
                """));
        assertEquals(1716, count(jdbc, """
                select count(*) from legacy_import_audit where entity_table = 'contracts'
                """));
        assertEquals(170, count(jdbc, """
                select count(*) from contracts c
                join legacy_import_audit a
                  on a.entity_table = 'contracts' and a.entity_id = c.id
                where a.notes like '%Horário de 6h15%'
                  and c.weekly_workload_minutes = 1800
                """));
        assertEquals(0, count(jdbc, "select count(*) from contracts where weekly_workload_minutes = 1875"));
        assertEquals(93, count(jdbc, "select count(distinct employer_id) from contracts"));
        assertEquals("82316373000130", employerTaxIdForHistoricalRow(jdbc, 274));
        assertEquals("75633560000182", employerTaxIdForHistoricalRow(jdbc, 1541));
        assertEquals("25205398000719", employerTaxIdForHistoricalRow(jdbc, 1547));
        assertEquals(0, count(jdbc, """
                select count(*) from legacy_import_audit
                where entity_table = 'contracts'
                  and source_file = 'aprendizes_historico.xlsx' and source_row = 276
                """));
        assertEquals(0, count(jdbc, """
                select count(*) from legacy_import_audit
                where entity_table = 'organizations'
                  and source_file = 'empresas.xlsx' and source_row = 35
                """));
        assertEquals(0, count(jdbc, """
                select count(*) from contracts c
                join legacy_import_audit a
                  on a.entity_table = 'organizations' and a.entity_id = c.employer_id
                where a.source_file = 'GERADO'
                """));

        assertEquals(0, count(jdbc, """
                select count(*) from contracts c
                left join organization_type_assignments t
                  on t.organization_id = c.employer_id and t.organization_type_code = 'EMPLOYER'
                where t.organization_id is null
                """));
        assertEquals(0, count(jdbc, """
                select count(*) from contracts c
                left join organization_type_assignments t
                  on t.organization_id = c.school_id and t.organization_type_code = 'SCHOOL'
                where c.school_id is not null and t.organization_id is null
                """));
        assertEquals(5, count(jdbc, """
                select count(*) from contracts c
                where c.school_id = c.employer_id
                """));
        assertEquals(0, count(jdbc, """
                select count(*) from (
                  select learner_id from contracts
                  where status = 'ACTIVE'
                  group by learner_id having count(*) > 1
                ) duplicates
                """));
        assertEquals(0, count(jdbc, """
                select count(*) from contracts c
                join learners l on l.id = c.learner_id
                join people p on p.id = l.person_id
                join organizations o on o.id = c.employer_id
                where c.status = 'ACTIVE'
                  and (c.start_date > date '2026-09-25'
                    or c.end_date < date '2026-09-25'
                    or l.status <> 'ACTIVE' or p.status <> 'ACTIVE'
                    or o.status <> 'ACTIVE')
                """));
        assertEquals(0, count(jdbc, """
                select count(*) from cohort_enrollments e
                join contracts c on c.id = e.contract_id
                join cohorts h on h.id = e.cohort_id
                where e.start_date < c.start_date or e.end_date > c.end_date
                  or e.start_date < h.start_date or e.end_date > h.end_date
                """));
        assertEquals(0, count(jdbc, """
                select count(*) from cohort_enrollments
                where status = 'COMPLETED' and end_date >= date '2026-09-25'
                """));
    }

    private int count(JdbcTemplate jdbc, String query) {
        return jdbc.queryForObject(query, Integer.class);
    }

    private int enrollmentCountForCohort(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("""
                select count(*) from cohort_enrollments e
                join cohorts h on h.id = e.cohort_id
                where h.code = ?
                """, Integer.class, code);
    }

    private String cohortCodeForSourceRow(JdbcTemplate jdbc, String file, int row) {
        return jdbc.queryForObject("""
                select h.code from cohort_enrollments e
                join cohorts h on h.id = e.cohort_id
                join legacy_import_audit a
                  on a.entity_table = 'cohort_enrollments' and a.entity_id = e.id
                where a.source_file = ? and a.source_row = ?
                """, String.class, file, row);
    }

    private String employerTaxIdForHistoricalRow(JdbcTemplate jdbc, int sourceRow) {
        return jdbc.queryForObject("""
                select o.tax_id from contracts c
                join organizations o on o.id = c.employer_id
                join legacy_import_audit a
                  on a.entity_table = 'contracts' and a.entity_id = c.id
                where a.source_file = 'aprendizes_historico.xlsx' and a.source_row = ?
                """, String.class, sourceRow);
    }
}
