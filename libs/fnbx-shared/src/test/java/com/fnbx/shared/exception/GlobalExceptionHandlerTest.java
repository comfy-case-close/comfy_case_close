package com.fnbx.shared.exception;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void catalogCodesAreUniqueAndOnlyRepresentFailures() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode).toList()).doesNotHaveDuplicates();
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getStatus).toList()).allMatch(status -> status.isError());
    }

    @Test void unexpectedFailuresLogWhereButNeverTheMessageText() {
        String secret = new IllegalStateException("password=hunter2", null).getClass().getName();
        String wrapped = GlobalExceptionHandler.describe(
                new RuntimeException("SELECT ... WHERE token='abc'", new IllegalStateException("password=hunter2")));
        assertThat(wrapped).startsWith("java.lang.RuntimeException caused by " + secret)
                .contains("\tat com.fnbx.shared.exception.GlobalExceptionHandlerTest")
                .doesNotContain("hunter2").doesNotContain("token");

        String missing = null;
        try { missing.length(); } catch (NullPointerException npe) {
            assertThat(GlobalExceptionHandler.describe(npe))
                    .contains("Cannot invoke \"String.length()\" because \"missing\" is null");
        }
    }

    @Test void domainFailuresUseTheSharedEnvelopeAndPreserveStatus() throws Exception {
        mvc.perform(get("/probe/domain")).andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(3009))
                .andExpect(jsonPath("$.message").value("Cannot move DRAFT -> APPROVED"))
                .andExpect(jsonPath("$.path").value("/probe/domain"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void frameworkErrorsKeepTheirStatusAndSafeEnvelope() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{broken}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2001));
        mvc.perform(get("/probe/body")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST")).andExpect(jsonPath("$.code").value(2003));
        mvc.perform(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(2004));
        mvc.perform(get("/unknown")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(2002));
    }

    @Test void accessDenialStaysForbiddenAndUnexpectedFailuresNeverExposeDetails() throws Exception {
        mvc.perform(get("/probe/denied")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(2410));
        String body = mvc.perform(get("/probe/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(9999)).andExpect(jsonPath("$.message").value("Unexpected error"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret-password", "SELECT", "IllegalStateException");
    }

    @Test void rejectedInputIsABadRequestButOtherDataFailuresAreNot() throws Exception {
        mvc.perform(get("/probe/nul")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2000));
        mvc.perform(get("/probe/check")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(2000));
        mvc.perform(get("/probe/foreign-key")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(9999));
    }

    @Test void closedDatabaseConnectionsReturnRetryable503WithoutLeakingDetails() throws Exception {
        for (String path : new String[]{"/probe/db-transaction", "/probe/db-resource"}) {
            String body = mvc.perform(get(path)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(9001))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("secret-password", "jdbc:", "SQLException");
        }
        // A non-connectivity failure is still unexpected rather than retryable.
        mvc.perform(get("/probe/db-schema")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(9999));
    }

    @RestController
    @RequestMapping("/probe")
    static class Probe {
        @GetMapping("/db-transaction") String databaseTransaction() {
            throw new org.springframework.transaction.CannotCreateTransactionException("private transaction detail",
                    new java.sql.SQLTransientConnectionException("jdbc: secret-password", "08003"));
        }
        @GetMapping("/db-resource") String databaseResource() {
            throw new org.springframework.dao.DataAccessResourceFailureException("private database detail",
                    new java.sql.SQLException("jdbc: secret-password", "08006"));
        }
        @GetMapping("/db-schema") String databaseSchema() {
            throw new org.springframework.transaction.CannotCreateTransactionException("schema error",
                    new java.sql.SQLException("private schema", "42P01"));
        }
        @GetMapping("/domain") String domain() { throw new AppException(ErrorCode.ILLEGAL_TRANSITION, "Cannot move DRAFT -> APPROVED"); }
        @GetMapping("/nul") String nul() {
            throw new org.springframework.dao.DataIntegrityViolationException("x", new java.sql.SQLException("invalid byte sequence", "22021"));
        }
        @GetMapping("/check") String check() {
            throw new org.springframework.dao.DataIntegrityViolationException("x", new java.sql.SQLException("check", "23514"));
        }
        @GetMapping("/foreign-key") String foreignKey() {
            throw new org.springframework.dao.DataIntegrityViolationException("x", new java.sql.SQLException("fk", "23503"));
        }
        @GetMapping("/denied") String denied() { throw new AccessDeniedException("private authorization detail"); }
        @GetMapping("/unexpected") String unexpected() { throw new IllegalStateException("SELECT secret-password"); }
        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Input body(@RequestBody Input input) { return input; }
    }
    record Input(int amount) {}
}
