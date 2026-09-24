package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.FundWithdrawalRequest;
import com.comfy.caseclose.dto.response.FundWithdrawalPotDTO;
import com.comfy.caseclose.dto.response.WarningDTO;
import com.comfy.caseclose.entity.AppConfig;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.entity.CashClose;
import com.comfy.caseclose.entity.FundWithdrawal;
import com.comfy.caseclose.entity.ShiftType;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.repository.AppConfigRepository;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.CashCloseRepository;
import com.comfy.caseclose.repository.CashMovementRepository;
import com.comfy.caseclose.repository.FundWithdrawalRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.CustomUserDetails;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import com.comfy.caseclose.utils.enums.FundPeriodType;
import com.comfy.caseclose.utils.enums.RiskLevel;
import com.comfy.caseclose.utils.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundWithdrawalServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);

    @Mock private FundWithdrawalRepository fundWithdrawalRepository;
    @Mock private CashCloseRepository cashCloseRepository;
    @Mock private CashMovementRepository cashMovementRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppConfigRepository appConfigRepository;

    @InjectMocks private FundWithdrawalServiceImpl service;

    @BeforeEach
    void setUp() {
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setBranchCode("TX");
        branch.setBranchName("Thủ Đức");

        User admin = new User();
        admin.setId(7L);
        admin.setFullName("Admin");
        admin.setRole(UserRole.ADMIN);

        ShiftType shiftType = new ShiftType();
        shiftType.setShiftName("Kết ca tối");

        CashClose close = new CashClose();
        close.setId(101L);
        close.setBranch(branch);
        close.setShiftType(shiftType);
        close.setSubmittedBy(admin);
        close.setBusinessDate(LocalDate.of(2026, 8, 10));
        close.setSubmittedAt(OffsetDateTime.now());
        close.setWithdrawalAmount(1_000_000L);
        close.setCountedCash(3_000_000L);
        close.setStatus(CashCloseStatus.SUBMITTED);
        close.setRiskLevel(RiskLevel.LOW);

        lenient().when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        lenient().when(cashCloseRepository.findForReport(eq(FROM), eq(TO), any(), eq(1L))).thenReturn(List.of(close));
        lenient().when(cashMovementRepository.findByCashCloseIdIn(any())).thenReturn(List.of());
        lenient().when(fundWithdrawalRepository.findPostedOverlapping(FROM, TO, 1L)).thenReturn(List.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CustomUserDetails.from(admin), null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Withdrawing within the remaining pot records it with no warning")
    void record_withinPot_noWarning() {
        FundWithdrawalPotDTO pot = service.record(request(1_000_000L));

        verify(fundWithdrawalRepository).save(any(FundWithdrawal.class));
        assertThat(pot.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("Withdrawing more than the remaining pot still records it, and warns")
    void record_exceedingPot_savesAndWarns() {
        FundWithdrawalPotDTO pot = service.record(request(1_300_000L));

        ArgumentCaptor<FundWithdrawal> saved = ArgumentCaptor.forClass(FundWithdrawal.class);
        verify(fundWithdrawalRepository).save(saved.capture());
        assertThat(saved.getValue().getSystemWithdrawAmount()).isEqualTo(1_300_000L);

        assertThat(pot.getWarnings()).hasSize(1);
        WarningDTO warning = pot.getWarnings().getFirst();
        assertThat(warning.getCode()).isEqualTo(WarningDTO.WITHDRAW_EXCEEDS_REMAINING_POT);
        assertThat(warning.getAmount()).isEqualTo(1_300_000L);
        assertThat(warning.getLimit()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("A withdrawal above the configured warning threshold is recorded and flagged")
    void record_overWarningThreshold_savesAndWarns() {
        when(appConfigRepository.findById((short) 1)).thenReturn(Optional.of(configWithThreshold(800_000L)));

        FundWithdrawalPotDTO pot = service.record(request(900_000L));

        verify(fundWithdrawalRepository).save(any(FundWithdrawal.class));
        assertThat(pot.getWarnings()).hasSize(1);
        WarningDTO warning = pot.getWarnings().getFirst();
        assertThat(warning.getCode()).isEqualTo(WarningDTO.WITHDRAW_OVER_WARNING_THRESHOLD);
        assertThat(warning.getAmount()).isEqualTo(900_000L);
        assertThat(warning.getLimit()).isEqualTo(800_000L);
    }

    @Test
    @DisplayName("A withdrawal exactly at the threshold, or any withdrawal when the threshold is 0, is not flagged")
    void record_atOrDisabledThreshold_noWarning() {
        when(appConfigRepository.findById((short) 1)).thenReturn(Optional.of(configWithThreshold(900_000L)));
        assertThat(service.record(request(900_000L)).getWarnings()).isEmpty();

        when(appConfigRepository.findById((short) 1)).thenReturn(Optional.of(configWithThreshold(0L)));
        assertThat(service.record(request(900_000L)).getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("Both warnings can fire together")
    void record_pastPotAndThreshold_warnsTwice() {
        when(appConfigRepository.findById((short) 1)).thenReturn(Optional.of(configWithThreshold(500_000L)));

        FundWithdrawalPotDTO pot = service.record(request(1_300_000L));

        assertThat(pot.getWarnings()).extracting(WarningDTO::getCode)
                .containsExactly(WarningDTO.WITHDRAW_EXCEEDS_REMAINING_POT, WarningDTO.WITHDRAW_OVER_WARNING_THRESHOLD);
    }

    @Test
    @DisplayName("A pot period reaching into the future is rejected, and nothing is saved")
    void record_rejectsFuturePeriod() {
        FundWithdrawalRequest request = request(100_000L);
        request.setToDate(LocalDate.now().plusDays(5));

        assertThatThrownBy(() -> service.record(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be in the future");
        verify(fundWithdrawalRepository, never()).save(any(FundWithdrawal.class));
    }

    private AppConfig configWithThreshold(long threshold) {
        AppConfig config = new AppConfig();
        config.setFundWithdrawalWarningAbs(threshold);
        return config;
    }

    private FundWithdrawalRequest request(long systemWithdraw) {
        FundWithdrawalRequest request = new FundWithdrawalRequest();
        request.setBranchId(1L);
        request.setFromDate(FROM);
        request.setToDate(TO);
        request.setPeriodType(FundPeriodType.CUSTOM.name());
        request.setSystemWithdrawAmount(systemWithdraw);
        return request;
    }
}
