package com.fnbx.cashclose;

import java.util.*;
import com.fnbx.cashclose.dto.request.OpenDraftRequest;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.service.impl.CashCloseServiceImpl;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashCloseAuthorizationTest {
    @Mock CashCloseRepository closeRepository;
    @Mock CashMovementRepository movementRepository;
    @Mock com.fnbx.cashclose.repository.CashCloseDecisionRepository closeDecisionRepository;
    @Mock com.fnbx.cashclose.repository.CashCloseCalcRepository calcRepository;
    @Mock com.fnbx.cashclose.mapper.CashCloseMapper mapper;
    @InjectMocks CashCloseServiceImpl service;
    private final UUID business = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();

    @AfterEach void clear() { SecurityContextHolder.clearContext(); TenantContext.clear(); }

    @Test void cannotCreateACloseForAnUnassignedBranch() {
        signedIn(Map.of(UUID.randomUUID(), "ADMIN"));
        var request = new OpenDraftRequest(); request.setBranchId(branch);
        assertThatThrownBy(() -> service.openDraft(request)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(closeRepository);
    }

    @Test void adminAtAnotherBranchCannotApproveThisBranchsClose() {
        signedIn(Map.of(UUID.randomUUID(), "ADMIN", branch, "STAFF"));
        var close = close(CloseStatus.SUBMITTED);
        when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
        assertThatThrownBy(() -> service.approve(close.getCashCloseId(), "approved"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(movementRepository);
        assertThat(close.getStatus()).isEqualTo(CloseStatus.SUBMITTED);
    }

    @Test void foreignBranchResourcesAndHistoryReturnNotFound() {
        signedIn(Map.of(UUID.randomUUID(), "MANAGER"));
        var close = close(CloseStatus.DRAFT);
        when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
        assertThatThrownBy(() -> service.getById(close.getCashCloseId())).isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CASH_CLOSE_NOT_FOUND));
        assertThatThrownBy(() -> service.getMovements(close.getCashCloseId())).isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CASH_CLOSE_NOT_FOUND));
        assertThatThrownBy(() -> service.getHistory(close.getCashCloseId())).isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CASH_CLOSE_NOT_FOUND));
        assertThatThrownBy(() -> service.getMovementHistory(close.getCashCloseId())).isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CASH_CLOSE_NOT_FOUND));
    }

    @Test void authorizedReviewerStillFollowsTheExistingStateMachine() {
        signedIn(Map.of(branch, "MANAGER"));
        var close = close(CloseStatus.DRAFT);
        when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
        assertThatThrownBy(() -> service.approve(close.getCashCloseId(), "approved"))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION));
    }

    @Test void approvalKeepsTheAuthorizingRoleAfterTheUserChangesRole() {
        signedIn(Map.of(branch, "MANAGER"));
        var close = close(CloseStatus.SUBMITTED);
        when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
        service.approve(close.getCashCloseId(), "reviewed");
        var capture = ArgumentCaptor.forClass(com.fnbx.cashclose.entity.CashCloseDecision.class);
        verify(closeDecisionRepository).save(capture.capture());
        signedIn(Map.of(branch, "ACCOUNTANT"));
        assertThat(capture.getValue().getActedRole()).isEqualTo("MANAGER");
        assertThat(capture.getValue().getActedBy()).isEqualTo(staff);
        var response = org.mapstruct.factory.Mappers.getMapper(com.fnbx.cashclose.mapper.CashCloseMapper.class)
                .toCloseDecisionList(List.of(capture.getValue())).get(0);
        assertThat(response.getActedRole()).isEqualTo("MANAGER");
    }

    private void signedIn(Map<UUID, String> assignments) {
        Map<String, String> grants = new HashMap<>(); assignments.forEach((id, role) -> grants.put(id.toString(), role));
        Jwt jwt = Jwt.withTokenValue("validated-in-security-tests").header("alg", "HS256").subject("STAFF")
                .claim("uid", staff.toString()).claim("business_id", business.toString())
                .claim("branch_roles", grants).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        TenantContext.set(TenantContext.of(business, staff));
    }
    private CashClose close(CloseStatus status) {
        var close = new CashClose(); close.setCashCloseId(UUID.randomUUID()); close.setBranchId(branch);
        close.setBusinessId(business); close.setStatus(status); return close;
    }
}
