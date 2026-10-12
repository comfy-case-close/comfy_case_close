package com.fnbx.cashclose;

import com.fnbx.cashclose.controller.CashCloseController;
import com.fnbx.cashclose.dto.request.CashMovementListFilter;
import com.fnbx.cashclose.dto.response.CashMovementResponse;
import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.service.CashCloseService;
import com.fnbx.cashclose.service.MovementResponseAssembler;
import com.fnbx.cashclose.service.impl.CashCloseServiceImpl;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.utils.PagedResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MyMovementsTest {
    @Mock CashMovementRepository movementRepository;
    @Mock MovementResponseAssembler movementResponses;
    @Mock BranchAccessGuard branchAccess;
    @Mock CashCloseService controllerService;
    @InjectMocks CashCloseServiceImpl service;

    @BeforeEach void context() {
        TenantContext.set(TenantContext.of(UUID.randomUUID(), UUID.randomUUID()));
    }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void ownListDoesNotRequireBranchPermissions() {
        Pageable pageable = PageRequest.of(0, 20);
        when(movementRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.<CashMovement>empty(pageable));
        when(movementResponses.toResponses(List.of())).thenReturn(List.of());
        var response = service.listMyMovements(new CashMovementListFilter(), pageable);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        verifyNoInteractions(branchAccess);
    }

    @Test void invalidDateRangeIsRejectedBeforeReading() {
        var filter = CashMovementListFilter.builder()
                .fromDate(LocalDate.of(2026, 9, 22)).toDate(LocalDate.of(2026, 9, 21)).build();
        assertThatThrownBy(() -> service.listMyMovements(filter, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DATE_RANGE_INVALID));
        verifyNoInteractions(movementRepository, movementResponses, branchAccess);
    }

    @Test void routeAcceptsFiltersAndPaginationWithoutABranchHeader() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new CashCloseController(controllerService)).build();
        when(controllerService.listMyMovements(any(), any())).thenReturn(PagedResponse.<CashMovementResponse>builder().content(List.of()).build());
        UUID branch = UUID.randomUUID();
        mvc.perform(get("/cash-closes/movements/mine")
                .param("branchId", branch.toString()).param("kindCode", "TIPS")
                .param("fromDate", "2026-09-21").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());
        var filter = ArgumentCaptor.forClass(CashMovementListFilter.class);
        verify(controllerService).listMyMovements(filter.capture(), eq(PageRequest.of(2, 5)));
        assertThat(filter.getValue().getBranchId()).isEqualTo(branch);
        assertThat(filter.getValue().getKindCode()).isEqualTo("TIPS");
        assertThat(filter.getValue().getFromDate()).isEqualTo(LocalDate.of(2026, 9, 21));
    }
}
