package com.fnbx.shared.logging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.slf4j.*;

/** Standalone child JVM exercises Boot's real file appender using a service's actual YAML. */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
public class FileLoggingProbe {
    public static void main(String[] args) {
        try (var context = SpringApplication.run(FileLoggingProbe.class, args)) {
            MDC.put("requestId", "file-test-1");
            var log = LoggerFactory.getLogger(FileLoggingProbe.class);
            for (int i = 0; i < 400; i++) log.info("Rolling probe marker={} {}", i, "x".repeat(250));
            MDC.clear();
        } finally {
            ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).stop();
        }
    }
}
