package com.fnbx.cashclose;

import java.util.Optional;
import java.util.UUID;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.mapper.CashCloseMapper;
import com.fnbx.cashclose.repository.CashCloseCalcRepository;
import com.fnbx.cashclose.repository.CashCloseDecisionRepository;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.CashMovementRepository;
import com.fnbx.cashclose.service.FundWithdrawalService;
import com.fnbx.cashclose.service.impl.CashCloseServiceImpl;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class CashCloseServiceLoggingTest {
    @Mock CashCloseRepository closes;
    @Mock CashMovementRepository movements;
    @Mock CashCloseDecisionRepository decisions;
    @Mock CashCloseCalcRepository calculations;
    @Mock CashCloseMapper mapper;
    @Mock BranchAccessGuard branchAccess;
    @Mock EntityManager entityManager;
    @Mock FundWithdrawalService withdrawals;
    @InjectMocks CashCloseServiceImpl service;
    private CashClose close;

    @BeforeEach void setup() {
        UUID business = UUID.randomUUID();
        TenantContext.set(TenantContext.of(business, UUID.randomUUID()));
        close = new CashClose();
        close.setCashCloseId(UUID.randomUUID());
        close.setBusinessId(business);
        close.setBranchId(UUID.randomUUID());
        close.setStatus(CloseStatus.SUBMITTED);
        when(closes.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
        when(decisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void logsBusinessEventOnlyAfterCommit(CapturedOutput output) {
        new TransactionTemplate(new TestTransactionManager()).executeWithoutResult(status -> {
            service.approve(close.getBranchId(), close.getCashCloseId(), "private reviewer note");
            assertThat(output).doesNotContain("Cash close state changed");
        });
        assertThat(output).contains("Cash close state changed cashCloseId=" + close.getCashCloseId())
                .contains("action=APPROVE from=SUBMITTED to=APPROVED")
                .doesNotContain("private reviewer note");
    }

    @Test void rollbackDoesNotProduceABusinessSuccessLog(CapturedOutput output) {
        new TransactionTemplate(new TestTransactionManager()).executeWithoutResult(status -> {
            service.approve(close.getBranchId(), close.getCashCloseId(), "reviewed");
            status.setRollbackOnly();
        });
        assertThat(output).doesNotContain("Cash close state changed");
    }

    /** Exercises Spring's real synchronization/commit lifecycle without requiring PostgreSQL. */
    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
