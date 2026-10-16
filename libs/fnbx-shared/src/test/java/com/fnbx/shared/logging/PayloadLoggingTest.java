package com.fnbx.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.shared.exception.GlobalExceptionHandler;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class PayloadLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(HttpPayloadLogger.class);
    private Level previous;
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, HttpPayloadLoggingAutoConfiguration.class,
                    ServletPayloadLoggingAutoConfiguration.class, RequestLoggingAutoConfiguration.class))
            .withUserConfiguration(Web.class);
    @BeforeEach void setup() { previous = logger.getLevel(); logger.setLevel(Level.DEBUG); }
    @AfterEach void clear() { logger.setLevel(previous); }
    private MockMvc mvc(org.springframework.web.context.WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(RequestLoggingFilter.class)).build();
    }

    @Test void logsDetailedJsonAndMasksNestedCredentialsHeadersQueryAndEchoesWithoutChangingHttp(CapturedOutput output) {
        runner.run(context -> {
            String body = """
                {"password":"password-secret-123","email":"person@example.com","salary":33000,
                 "children":[{"access_token":"access-secret-123","otp":"876543"}],
                 "fileBase64":"binary-secret"}
                """;
            mvc(context).perform(post("/probe/json").contentType(MediaType.APPLICATION_JSON).content(body)
                    .header("Authorization", "Bearer header-secret-123").header("X-API-Key", "api-secret-123")
                    .header("X-Platform-Key", "platform-secret-123").header("Cookie", "session=cookie-secret")
                    .param("refresh_token", "query-secret-123").header("X-Request-ID", "payload-test-1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.password").value("password-secret-123"))
                    .andExpect(jsonPath("$.salary").value(33000));
            assertThat(output).contains("direction=request", "direction=response", "[REDACTED]", "[BINARY OMITTED]",
                    "person@example.com", "33000", "Service operation").doesNotContain("password-secret-123",
                    "access-secret-123", "876543", "header-secret-123", "api-secret-123", "platform-secret-123",
                    "cookie-secret", "query-secret-123", "binary-secret");
            assertThat(output.getAll().split("HTTP payload direction=request", -1)).hasSize(2);
            assertThat(output.getAll().split("HTTP payload direction=response", -1)).hasSize(2);
        });
    }

    @Test void truncatesOnlyTheLogAndCanShowTheFullRedactedResponse(CapturedOutput output) {
        runner.withPropertyValues("fnb.logging.payload.max-response-chars=160").run(context -> {
            mvc(context).perform(get("/probe/long")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.tail").value("END_OF_RESPONSE"));
            assertThat(output).contains("direction=response truncated=true").doesNotContain("END_OF_RESPONSE", "long-secret");
        });
        runner.withPropertyValues("fnb.logging.payload.max-response-chars=160", "fnb.logging.payload.full-response=true").run(context -> {
            mvc(context).perform(get("/probe/long")).andExpect(status().isOk());
            assertThat(output).contains("direction=response truncated=false", "END_OF_RESPONSE").doesNotContain("long-secret");
        });
    }

    @Test void payloadSwitchAndInfoLevelSuppressPayloadWithoutSuppressingOperationalInfo(CapturedOutput output) {
        runner.withPropertyValues("fnb.logging.payload.enabled=false").run(context -> {
            mvc(context).perform(get("/probe/long")).andExpect(status().isOk());
        });
        logger.setLevel(Level.INFO);
        runner.run(context -> mvc(context).perform(get("/probe/long")).andExpect(status().isOk()));
        assertThat(output).doesNotContain("HTTP payload").contains("Request completed method=GET");
    }

    @Test void malformedJsonAndSecretPathUseNormalErrorsAndNeverDumpRawInput(CapturedOutput output) {
        runner.run(context -> {
            mvc(context).perform(post("/probe/json").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"malformed-secret\","))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2001));
            mvc(context).perform(get("/public/payslip-confirmations/path-secret")).andExpect(status().isBadRequest());
            assertThat(output).contains("direction=response", "MALFORMED_REQUEST", "{token}")
                    .doesNotContain("malformed-secret", "path-secret");
        });
    }

    @Test void binariesAndMultipartLogMetadataWithoutConsumingOrDumpingTheirContents(CapturedOutput output) {
        runner.run(context -> {
            byte[] bytes = "private-binary-content".getBytes(StandardCharsets.UTF_8);
            mvc(context).perform(get("/probe/pdf")).andExpect(status().isOk()).andExpect(content().bytes(bytes));
            mvc(context).perform(get("/probe/bytes")).andExpect(status().isOk()).andExpect(content().bytes(bytes));
            mvc(context).perform(multipart("/probe/upload").file(new MockMultipartFile("file", "report.pdf", "application/pdf", bytes)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.sizeBytes").value(bytes.length));
            assertThat(output).contains("report.pdf", "sizeBytes", "non_json_or_binary")
                    .doesNotContain("private-binary-content", Base64.getEncoder().encodeToString(bytes));
        });
    }

    @Test void redactsSignedUrlsAndNestedStringValuesBeforeTruncation(CapturedOutput output) {
        var properties = new PayloadLoggingProperties();
        var payload = new HttpPayloadLogger(new ObjectMapper(), properties);
        payload.write("response", Map.of(), Map.of("path", "/api/v1/payroll/public/payslip-confirmations/url-secret/confirm",
                "url", "https://storage.test/file?X-Goog-Credential=credential-secret&X-Goog-Signature=signature-secret",
                "rows", List.of(Map.of("newPassword", "nested-secret"), Map.of("note", "echo nested-secret"))), payload.newRedactor());
        assertThat(output).contains("[REDACTED]").doesNotContain("url-secret", "credential-secret", "signature-secret", "nested-secret");
    }

    @Test void disabledOrNonDebugDoesNotSerializeBodies() {
        var properties = new PayloadLoggingProperties();
        var logging = new HttpPayloadLogger(new ObjectMapper(), properties);
        var body = new CountingBody();
        properties.setEnabled(false);
        logging.write("response", Map.of(), body, logging.newRedactor());
        properties.setEnabled(true); logger.setLevel(Level.INFO);
        logging.write("response", Map.of(), body, logging.newRedactor());
        assertThat(body.calls).isZero();
    }

    @Test void nestedBinaryIsOmittedAndAdditionalFieldsCannotDisableCredentialMasking(CapturedOutput output) {
        var properties = new PayloadLoggingProperties();
        properties.setFullResponse(true); properties.setAdditionalSensitiveFields(Set.of("taxCode"));
        var logging = new HttpPayloadLogger(new ObjectMapper(), properties);
        logging.write("response", Map.of(), Map.of("blob", "nested-binary-secret".getBytes(StandardCharsets.UTF_8),
                "taxCode", "private-tax", "api_key", "private-key", "password", "private-password"), logging.newRedactor());
        assertThat(output).contains("sizeBytes", "[REDACTED]").doesNotContain("private-tax", "private-key", "private-password",
                Base64.getEncoder().encodeToString("nested-binary-secret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test void securityFilterErrorsAreCapturedWithoutReadingRejectedBodies(CapturedOutput output) throws Exception {
        var properties = new PayloadLoggingProperties();
        var logging = new ServletPayloadLogging(new HttpPayloadLogger(new ObjectMapper().findAndRegisterModules(), properties));
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/private");
        request.setContentType("application/json"); request.setContent("{\"password\":\"unread-secret\"}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Bearer security-secret");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new com.fnbx.shared.security.ServletSecurityErrorHandler(new ObjectMapper().findAndRegisterModules(), logging)
                .commence(request, response, new org.springframework.security.authentication.BadCredentialsException("exception-secret"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(output).contains("direction=request", "direction=response", "2409", "[REDACTED]")
                .doesNotContain("unread-secret", "security-secret", "exception-secret");
    }

    public static class CountingBody {
        int calls;
        public String getValue() { calls++; return "value"; }
    }

    @Test void invalidLimitFailsConfigurationAndEnvironmentNamesBind() {
        runner.withPropertyValues("fnb.logging.payload.max-response-chars=0").run(context -> assertThat(context).hasFailed());
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.SystemEnvironmentPropertySource("payload-systemEnvironment", Map.of(
                        "FNB_LOGGING_PAYLOAD_MAXRESPONSECHARS", "1234", "FNB_LOGGING_PAYLOAD_FULLRESPONSE", "true"))))
                .run(context -> {
                    assertThat(context.getBean(PayloadLoggingProperties.class).getMaxResponseChars()).isEqualTo(1234);
                    assertThat(context.getBean(PayloadLoggingProperties.class).isFullResponse()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc
    static class Web {
        @Bean Probe probe() { return new Probe(); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
    }
    @RestController
    static class Probe {
        @PostMapping("/probe/json") Map<String,Object> json(@RequestBody Map<String,Object> body) {
            org.slf4j.LoggerFactory.getLogger(Probe.class).info("Service operation probe");
            return body;
        }
        @GetMapping("/probe/long") Map<String,Object> longBody() {
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("padding", "a".repeat(1000)); body.put("password", "long-secret"); body.put("tail", "END_OF_RESPONSE");
            return body;
        }
        @GetMapping("/public/payslip-confirmations/{token}") void error(@PathVariable String token) {
            throw new com.fnbx.shared.exception.AppException(com.fnbx.shared.exception.ErrorCode.VALIDATION_FAILED);
        }
        @GetMapping(value="/probe/pdf", produces="application/pdf") InputStreamResource pdf() {
            return new InputStreamResource(new ByteArrayInputStream("private-binary-content".getBytes(StandardCharsets.UTF_8)));
        }
        @GetMapping(value="/probe/bytes", produces="application/octet-stream") byte[] bytes() {
            return "private-binary-content".getBytes(StandardCharsets.UTF_8);
        }
        @PostMapping("/probe/upload") Map<String,Object> upload(@RequestParam MultipartFile file) {
            return Map.of("sizeBytes", file.getSize());
        }
    }
}
