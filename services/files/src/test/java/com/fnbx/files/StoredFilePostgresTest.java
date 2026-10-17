package com.fnbx.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.files.entity.StoredFile;
import com.fnbx.shared.enums.FileKind;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real PostgreSQL JSONB binding, using a transaction-local temporary table only. */
@EnabledIfEnvironmentVariable(named = "FNB_FILES_TEST_DB_URL", matches = ".+")
class StoredFilePostgresTest {
    @Test void uploadsPngMetadataWithNoOcrResult() throws Exception { persistMetadata(null); }
    @Test void roundTripsAnExtractedJsonObject() throws Exception { persistMetadata("{\"amount\":125000}"); }

    private void persistMetadata(String extracted) throws Exception {
        Configuration config = new Configuration().addAnnotatedClass(StoredFile.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .setPhysicalNamingStrategy(new PhysicalNamingStrategyStandardImpl() {
                    @Override public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment env) {
                        return name != null && name.getText().equals("files") ? Identifier.toIdentifier("pg_temp") : name;
                    }
                });
        try (var factory = config.buildSessionFactory();
             var connection = DriverManager.getConnection(System.getenv("FNB_FILES_TEST_DB_URL"),
                     System.getenv("FNB_FILES_TEST_DB_USER"), System.getenv("FNB_FILES_TEST_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var session = factory.withOptions().connection(connection).openSession()) {
                var transaction = session.beginTransaction();
                try {
                    session.doWork(conn -> {
                        try (var statement = conn.createStatement()) {
                            statement.execute("CREATE TEMP TABLE stored_file (LIKE files.stored_file INCLUDING DEFAULTS INCLUDING CONSTRAINTS) ON COMMIT DROP");
                        }
                    });
                    var file = new StoredFile();
                    file.setFileId(UUID.randomUUID());
                    file.setBusinessId(UUID.randomUUID());
                    file.setFileKind(FileKind.POS_REPORT);
                    file.setFileName("proof.png");
                    file.setContentType("image/png");
                    file.setByteSize(658507);
                    file.setSha256("a".repeat(64));
                    file.setStorageProvider("GCS");
                    file.setStorageKey("test/proof.png");
                    file.setOcrExtracted(extracted);
                    session.persist(file);
                    session.flush();
                    session.clear();
                    var stored = session.find(StoredFile.class, file.getFileId());
                    assertThat(stored.getContentType()).isEqualTo("image/png");
                    assertThat(stored.getOcrStatus()).isEqualTo("NONE");
                    if (extracted == null) assertThat(stored.getOcrExtracted()).isNull();
                    else {
                        var json = new ObjectMapper();
                        assertThat(json.readTree(stored.getOcrExtracted())).isEqualTo(json.readTree(extracted));
                    }
                } finally {
                    transaction.rollback();
                }
            }
        }
    }
}
