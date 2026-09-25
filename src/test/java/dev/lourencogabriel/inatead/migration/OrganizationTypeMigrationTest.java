package dev.lourencogabriel.inatead.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrganizationTypeMigrationTest {

    @Test
    void shouldCreateOrganizationTypeTablesInV1AndSeedCodesInV2() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:organization_type_migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        Flyway.configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(0, jdbc.queryForObject("select count(*) from organization_types", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from organization_type_assignments", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'ORGANIZATIONS' and column_name = 'ORGANIZATION_TYPE'
                """, Integer.class));

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertEquals(List.of("EMPLOYER", "SCHOOL"), jdbc.queryForList(
                "select code from organization_types order by code", String.class));

        String id = "01K5X3M8Y7ABCD1234EFGH5681";
        jdbc.update("""
                insert into addresses
                    (id, country_code, state_code, created_at, postal_code, updated_at,
                     street_number, city, district, street)
                values (?, 'BR', 'PR', current_timestamp, '83200000', current_timestamp,
                        '1', 'Paranaguá', 'Centro', 'Rua Teste')
                """, id);
        jdbc.update("""
                insert into organizations
                    (id, address_id, created_at, updated_at, tax_id, phone_number,
                     legal_name, contact_email, status)
                values (?, ?, current_timestamp, current_timestamp, '12345678000191', '41999999999',
                        'Organização Teste', 'contato@example.com', 'ACTIVE')
                """, id, id);
        jdbc.update("insert into organization_type_assignments values (?, 'EMPLOYER')", id);
        jdbc.update("insert into organization_type_assignments values (?, 'SCHOOL')", id);

        assertEquals(List.of("EMPLOYER", "SCHOOL"), jdbc.queryForList(
                "select organization_type_code from organization_type_assignments where organization_id = ? order by organization_type_code",
                String.class, id));
    }
}
