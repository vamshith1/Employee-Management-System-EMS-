package com.ems;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Runs every {@link ApiFlowIntegrationTest} scenario against a real PostgreSQL server (the production
 * database on Render) instead of H2, so dialect-specific problems surface in the build.
 */
@DirtiesContext // close this Postgres-backed context after the class instead of caching it
class PostgresApiFlowIntegrationTest extends ApiFlowIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            // keep binaries and data under target/ (the system temp drive may be small)
            File base = new File("target/embedded-pg").getAbsoluteFile();
            EmbeddedPostgres pg = EmbeddedPostgres.builder()
                    .setOverrideWorkingDirectory(new File(base, "work"))
                    .setDataDirectory(new File(base, "data-" + System.nanoTime()))
                    .setCleanDataDirectory(true)
                    .start();
            // @DirtiesContext closes the Spring context (and its pool) after this class;
            // the server itself is stopped last, when the test JVM exits.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { pg.close(); } catch (IOException ignored) { /* already stopping */ }
            }));
            return pg;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

}
