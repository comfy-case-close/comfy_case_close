package com.fnbx.cashclose;

import com.fnbx.cashclose.controller.CashCloseController;
import com.fnbx.cashclose.dto.request.SubmitCashCloseRequest;
import com.fnbx.cashclose.exception.CashCloseExceptions;
import com.fnbx.cashclose.service.CashCloseService;
import com.fnbx.shared.exception.GlobalExceptionHandler;
import com.fnbx.shared.security.BranchHeader;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Submission contract at the HTTP boundary. */
@ExtendWith(MockitoExtension.class)
class CashCloseHttpStatusTest {
    @Mock CashCloseService cashCloseService;
    private MockMvc mvc;
    private final UUID branchId = UUID.randomUUID();
    private final UUID shiftId = UUID.randomUUID();

    private String body() {
        return "{\"shiftTypeId\":\""+shiftId+"\",\"businessDate\":\"2026-09-21\","
                + "\"denominations\":{\"counts\":[{\"faceValue\":500000,\"quantity\":1}]}}";
    }

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CashCloseController(cashCloseService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void wrongBranchHeaderIsForbidden() throws Exception {
        when(cashCloseService.submit(any(), any())).thenThrow(CashCloseExceptions.branchHeaderMismatch());
        mvc.perform(post("/cash-closes").header(BranchHeader.NAME,branchId)
                .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(3018));
    }

    @Test void branchComesFromHeader() throws Exception {
        mvc.perform(post("/cash-closes").header(BranchHeader.NAME,branchId)
                .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated());
        verify(cashCloseService).submit(eq(branchId),any(SubmitCashCloseRequest.class));
    }

    @Test void missingHeaderIsBadRequest() throws Exception {
        mvc.perform(post("/cash-closes").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest());
    }

    @Test void missingCountIsBadRequest() throws Exception {
        mvc.perform(post("/cash-closes").header(BranchHeader.NAME,branchId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shiftTypeId\":\""+shiftId+"\",\"businessDate\":\"2026-09-21\"}"))
                .andExpect(status().isBadRequest());
    }
}
