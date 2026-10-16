package com.fnbx.shared.logging;

import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class SharedLoggingExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter("test-service")).build();
    }

    @Test void logsStableBusinessCodeAndPreservesResponse(CapturedOutput output) throws Exception {
        mvc.perform(post("/probe/rejected").header("X-Request-ID", "rejection-example"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Request-ID", "rejection-example"))
                .andExpect(jsonPath("$.code").value(3004));

        assertThat(output).contains("Request rejected errorCode=3004 errorName=MOVEMENTS_PENDING")
                .contains("status=422").doesNotContain("sensitive business detail");
    }

    @Test void logsUnexpectedFailureOnceWithFramesButWithoutExceptionMessages(CapturedOutput output) throws Exception {
        mvc.perform(get("/probe/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(9999))
                .andExpect(jsonPath("$.message").value("Unexpected error"));

        assertThat(output).contains("Unhandled request failure: java.lang.IllegalStateException caused by java.lang.RuntimeException")
                .contains("Probe.failure(")
                .doesNotContain("secret-database-value", "secret-cause-value");
        assertThat(output.getAll().split("Unhandled request failure", -1)).hasSize(2);
    }

    @Test void logsMalformedInputAndKeepsTheExistingErrorContract(CapturedOutput output) throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2001));
        assertThat(output).contains("Request rejected errorCode=2001 errorName=MALFORMED_REQUEST");
    }

    @RestController
    static class Probe {
        @PostMapping("/probe/rejected") void rejected() {
            throw new AppException(ErrorCode.MOVEMENTS_PENDING, "sensitive business detail");
        }
        @GetMapping("/probe/failure") void failure() {
            throw new IllegalStateException("secret-database-value", new RuntimeException("secret-cause-value"));
        }
        @PostMapping("/probe/body") void body(@RequestBody Body body) {}
    }
    record Body(String value) {}
}
