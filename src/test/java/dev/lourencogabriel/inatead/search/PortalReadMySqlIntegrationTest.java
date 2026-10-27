package dev.lourencogabriel.inatead.search;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Execute the same read contracts against the production database dialect. */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PortalReadMySqlIntegrationTest extends PortalReadIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired javax.sql.DataSource dataSource;
    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("inat_pagination_test").withUsername("test").withPassword("test");

    @org.junit.jupiter.api.Test void productionDialectAndPaginationIndexAreAvailable() throws java.sql.SQLException {
        try(var connection=dataSource.getConnection()) { org.junit.jupiter.api.Assertions.assertEquals("MySQL",connection.getMetaData().getDatabaseProductName()); }
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        org.junit.jupiter.api.Assertions.assertTrue(jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and index_name='idx_lessons_start_id'",Long.class)>0);
        org.junit.jupiter.api.Assertions.assertFalse(jdbc.queryForList("explain select id from lessons where starts_at >= '2026-01-01' order by starts_at,id limit 20").isEmpty());
    }
}
