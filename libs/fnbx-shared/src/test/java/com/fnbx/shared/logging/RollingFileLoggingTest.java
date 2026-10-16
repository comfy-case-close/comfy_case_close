package com.fnbx.shared.logging;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RollingFileLoggingTest {
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path directory;
    @Test void actualServiceYamlRollsByDayAndSizeAndKeepsHistoryAcrossProcessRestart() throws Exception {
        Path repo = Path.of("").toAbsolutePath();
        while (!Files.exists(repo.resolve("docker-compose.aws.yml"))) repo = repo.getParent();
        Path config = repo.resolve("services/cashclose/src/main/resources/application.yml");
        Path active = directory.resolve("cashclose-service/application.log");
        Files.createDirectories(active.getParent());
        Path expiredArchive = active.getParent().resolve("archive/application."
                + LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(45) + ".0.log.gz");
        Files.createDirectories(expiredArchive.getParent());
        try (var gzip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(expiredArchive))) {
            gzip.write("expired-marker".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        Files.writeString(active, "previous-day-marker\n");
        var yesterday = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1);
        Files.setLastModifiedTime(active, FileTime.from(yesterday.toInstant()));
        launch(config);
        Path archive = active.getParent().resolve("archive");
        assertThat(expiredArchive).doesNotExist();
        try (var paths = Files.list(archive)) {
            var archives = paths.toList();
            assertThat(archives).hasSizeGreaterThan(1);
            boolean previousDay = false;
            boolean correlated = Files.readString(active).contains("[cashclose-service requestId=file-test-1]");
            for (Path path : archives) {
                try (var gzip = new GZIPInputStream(Files.newInputStream(path))) {
                    String text = new String(gzip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    if (text.contains("previous-day-marker")) {
                        assertThat(path.getFileName().toString()).contains(yesterday.toLocalDate().toString());
                        previousDay = true;
                    }
                    correlated |= text.contains("[cashclose-service requestId=file-test-1]");
                }
            }
            assertThat(previousDay).isTrue(); assertThat(correlated).isTrue();
        }
        Files.writeString(active, "restart-persistent-marker\n", StandardOpenOption.APPEND);
        launch(config);
        StringBuilder persisted = new StringBuilder(Files.readString(active));
        try (var paths = Files.list(archive)) {
            for (Path path : paths.toList()) try (var gzip = new GZIPInputStream(Files.newInputStream(path))) {
                persisted.append(new String(gzip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        assertThat(persisted.toString()).as("Existing active/archive content survives application restart: %s", directory)
                .contains("restart-persistent-marker");
        assertThat(persisted.toString().split("Rolling probe marker=", -1)).hasSize(801);
    }
    private void launch(Path config) throws Exception {
        Path output = directory.resolve("probe-output.txt");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-cp", System.getProperty("java.class.path"), FileLoggingProbe.class.getName(),
                "--spring.main.web-application-type=none", "--spring.config.location=" + config.toUri());
        builder.environment().put("FNB_LOG_DIR", directory.toString());
        builder.environment().put("FNB_LOG_MAX_FILE_SIZE", "1KB");
        builder.environment().put("FNB_LOG_TOTAL_SIZE_CAP", "2MB");
        var child = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(60, TimeUnit.SECONDS)) { child.destroyForcibly(); fail("File logging probe timed out"); }
        assertThat(child.exitValue()).withFailMessage(Files.readString(output)).isZero();
        assertThat(Files.readString(output)).doesNotContain("FNATP is deprecated", "|-ERROR");
    }
}
