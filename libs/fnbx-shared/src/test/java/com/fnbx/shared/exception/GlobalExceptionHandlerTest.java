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

    @RestController
    @RequestMapping("/probe")
    static class Probe {
        @GetMapping("/domain") String domain() { throw new AppException(ErrorCode.ILLEGAL_TRANSITION, "Cannot move DRAFT -> APPROVED"); }
        @GetMapping("/denied") String denied() { throw new AccessDeniedException("private authorization detail"); }
        @GetMapping("/unexpected") String unexpected() { throw new IllegalStateException("SELECT secret-password"); }
        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Input body(@RequestBody Input input) { return input; }
    }
    record Input(int amount) {}
}
