package com.fnbx.cashclose;

import com.fnbx.cashclose.controller.CashCloseController;
import com.fnbx.cashclose.dto.request.ReplaceDenominationsRequest;
import com.fnbx.cashclose.exception.CashCloseExceptions;
import com.fnbx.cashclose.service.CashCloseService;
import com.fnbx.shared.exception.GlobalExceptionHandler;
import com.fnbx.shared.security.BranchHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the two new rules look like on the wire.
 *
 * <p>{@link CashCloseBranchScopeTest} proves the service refuses in the right
 * situations; this proves the refusal arrives at the client as the right HTTP
 * status. They are separate tests because they can fail independently: an error
 * code can carry the wrong status, and a controller can swallow an exception, and
 * neither shows up in the other's assertions.
 *
 * <p>Standalone MockMvc, no Spring context and no database. The point here is the
 * HTTP contract - status codes, the header binding, and the error body the client
 * parses - not persistence.
 */
@ExtendWith(MockitoExtension.class)
class CashCloseHttpStatusTest {

    private static final String COUNT_BODY = """
            {"counts":[{"faceValue":500000,"quantity":8}]}""";

    @Mock CashCloseService cashCloseService;

    private MockMvc mvc;
    private final UUID closeId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CashCloseController(cashCloseService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("A header naming another branch comes back 403")
    void wrongBranchHeaderIs403() throws Exception {
        when(cashCloseService.replaceDenominations(any(), any(), any()))
                .thenThrow(CashCloseExceptions.branchHeaderMismatch());

        mvc.perform(put("/cash-closes/{id}/denominations", closeId)
                        .header(BranchHeader.NAME, branchId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COUNT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(3018));
    }

    @Test
    @DisplayName("Recounting a close that is no longer a draft comes back 409")
    void recountingANonDraftIs409() throws Exception {
        when(cashCloseService.replaceDenominations(any(), any(), any()))
                .thenThrow(CashCloseExceptions.closeNotDraft("Close CC-1 is SUBMITTED"));

        mvc.perform(put("/cash-closes/{id}/denominations", closeId)
                        .header(BranchHeader.NAME, branchId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COUNT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(3014));
    }

    @Test
    @DisplayName("Submitting a close that is no longer a draft comes back 409")
    void submittingANonDraftIs409() throws Exception {
        when(cashCloseService.submit(any(), any(), any()))
                .thenThrow(CashCloseExceptions.closeNotDraft("Close CC-1 is APPROVED"));

        mvc.perform(post("/cash-closes/{id}/submit", closeId)
                        .header(BranchHeader.NAME, branchId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"counted and handed over\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("The branch the service receives is the one from the header")
    void theHeaderIsWhatReachesTheService() throws Exception {
        mvc.perform(put("/cash-closes/{id}/denominations", closeId)
                        .header(BranchHeader.NAME, branchId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COUNT_BODY))
                .andExpect(status().isOk());

        // The close id is in the path and the count is in the body; the branch is
        // neither, and that is the point of the change.
        verify(cashCloseService).replaceDenominations(
                eq(branchId), eq(closeId), any(ReplaceDenominationsRequest.class));
    }

    @Test
    @DisplayName("No header at all is a 400 - malformed, not denied")
    void missingHeaderIs400() throws Exception {
        mvc.perform(put("/cash-closes/{id}/denominations", closeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COUNT_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A body with no counts field is a 400 - an empty list is how you clear a count")
    void missingCountsFieldIs400() throws Exception {
        mvc.perform(put("/cash-closes/{id}/denominations", closeId)
                        .header(BranchHeader.NAME, branchId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
