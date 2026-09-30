package com.fnbx.cashclose;

import com.fnbx.cashclose.dto.request.SubmitCashCloseRequest;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.service.impl.CashCloseServiceImpl;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashCloseBranchScopeTest {
    @Mock CashCloseRepository closeRepository;
    @Mock BranchAccessGuard branchAccess;
    @InjectMocks CashCloseServiceImpl service;
    private final UUID business = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();
    private final UUID otherBranch = UUID.randomUUID();

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void submissionChecksBranchPermissionBeforeWriting() {
        TenantContext.set(TenantContext.of(business, staff));
        when(branchAccess.require(branch, Permission.CLOSE_SUBMIT))
                .thenThrow(new AccessDeniedException("denied"));
        assertThatThrownBy(() -> service.submit(branch, new SubmitCashCloseRequest()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(closeRepository);
    }

    @Test void aCloseInAnotherBranchCannotBeRead() {
        TenantContext.set(TenantContext.of(business, staff));
        CashClose close = new CashClose();
        close.setCashCloseId(UUID.randomUUID());
        close.setBusinessId(business);
        close.setBranchId(otherBranch);
        when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
        assertThatThrownBy(() -> service.getById(branch, close.getCashCloseId()))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                            .isEqualTo(ErrorCode.BRANCH_HEADER_MISMATCH);
                });
    }
}
