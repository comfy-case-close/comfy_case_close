package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.TipPayoutRequest;
import com.comfy.caseclose.dto.response.TipJarDTO;
import com.comfy.caseclose.dto.response.TipPayoutResultDTO;
import com.comfy.caseclose.dto.response.WarningDTO;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.entity.TipPayout;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.TipPayoutRepository;
import com.comfy.caseclose.repository.TipRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.CustomUserDetails;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TipJarServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);
    private static final LocalDate BEGINNING = LocalDate.of(2000, 1, 1);

    @Mock private TipRepository tipRepository;
    @Mock private TipPayoutRepository tipPayoutRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private TipJarServiceImpl service;

    private Branch branch;
    private User admin;

    @BeforeEach
    void setUp() {
        branch = new Branch();
        branch.setId(1L);
        branch.setBranchCode("TX");
        branch.setBranchName("Tú Xương");

        admin = new User();
        admin.setId(7L);
        admin.setFullName("Admin");
        admin.setRole(UserRole.ADMIN);

        lenient().when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        lenient().when(tipRepository.sumByFlow(anyBoolean(), any(), any(), anyList(), any())).thenReturn(0L);
        lenient().when(tipPayoutRepository.sumInRange(any(), any(), any())).thenReturn(0L);
        lenient().when(tipPayoutRepository.findInRange(any(), any(), any())).thenReturn(List.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CustomUserDetails.from(admin), null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("The jar summary keeps the two tip flows apart and derives the balance from all time up to the range end")
    void getTipJar_summary() {
        when(tipRepository.sumByFlow(eq(false), eq(FROM), eq(TO), anyList(), eq(1L))).thenReturn(300_000L);
        when(tipRepository.sumByFlow(eq(true), eq(FROM), eq(TO), anyList(), eq(1L))).thenReturn(50_000L);
        when(tipRepository.sumByFlow(eq(false), eq(BEGINNING), eq(TO), anyList(), eq(1L))).thenReturn(1_000_000L);
        when(tipPayoutRepository.sumInRange(eq(BEGINNING), eq(TO), eq(1L))).thenReturn(400_000L);
        when(tipPayoutRepository.findInRange(FROM, TO, 1L)).thenReturn(List.of(payout(120_000L, "Lan")));

        TipJarDTO jar = service.getTipJar(1L, FROM, TO);

        assertThat(jar.getSummary().getTipsIn()).isEqualTo(300_000L);
        assertThat(jar.getSummary().getTipsInsideDrawer()).isEqualTo(50_000L);
        assertThat(jar.getSummary().getPaidOut()).isEqualTo(120_000L);
        assertThat(jar.getSummary().getPayoutCount()).isEqualTo(1L);
        assertThat(jar.getSummary().getBalance()).isEqualTo(600_000L);
        assertThat(jar.getPayouts()).singleElement().satisfies(p -> {
            assertThat(p.getRecipientName()).isEqualTo("Lan");
            assertThat(p.getBranchName()).isEqualTo("Tú Xương");
        });
    }

    @Test
    @DisplayName("Paying out within the jar balance is recorded with no warning")
    void recordPayout_withinBalance() {
        when(tipRepository.sumByFlow(eq(false), eq(BEGINNING), eq(TO), anyList(), eq(1L))).thenReturn(500_000L);

        TipPayoutResultDTO result = service.recordPayout(request(200_000L, TO));

        ArgumentCaptor<TipPayout> saved = ArgumentCaptor.forClass(TipPayout.class);
        verify(tipPayoutRepository).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualTo(200_000L);
        assertThat(saved.getValue().getPayoutDate()).isEqualTo(TO);
        assertThat(saved.getValue().getCreatedBy()).isSameAs(admin);
        assertThat(result.getBalanceAfter()).isEqualTo(300_000L);
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("Paying out more than the jar holds is still recorded, and flagged")
    void recordPayout_overBalance_savesAndWarns() {
        when(tipRepository.sumByFlow(eq(false), eq(BEGINNING), eq(TO), anyList(), eq(1L))).thenReturn(100_000L);

        TipPayoutResultDTO result = service.recordPayout(request(250_000L, TO));

        verify(tipPayoutRepository).save(any(TipPayout.class));
        assertThat(result.getBalanceAfter()).isEqualTo(-150_000L);
        assertThat(result.getWarnings()).singleElement().satisfies(w -> {
            assertThat(w.getCode()).isEqualTo(WarningDTO.PAYOUT_EXCEEDS_JAR_BALANCE);
            assertThat(w.getAmount()).isEqualTo(250_000L);
            assertThat(w.getLimit()).isEqualTo(100_000L);
        });
    }

    @Test
    @DisplayName("A payout or a jar range dated in the future is rejected")
    void rejectsFutureDates() {
        LocalDate future = LocalDate.now().plusDays(3);

        assertThatThrownBy(() -> service.recordPayout(request(10_000L, future)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be in the future");
        verify(tipPayoutRepository, never()).save(any(TipPayout.class));

        assertThatThrownBy(() -> service.getTipJar(1L, FROM, future))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be in the future");
    }

    private TipPayoutRequest request(long amount, LocalDate date) {
        TipPayoutRequest request = new TipPayoutRequest();
        request.setBranchId(1L);
        request.setAmount(amount);
        request.setPayoutDate(date);
        request.setRecipientName("Lan");
        return request;
    }

    private TipPayout payout(long amount, String recipient) {
        TipPayout payout = new TipPayout();
        payout.setId(5L);
        payout.setBranch(branch);
        payout.setAmount(amount);
        payout.setPayoutDate(LocalDate.of(2026, 8, 10));
        payout.setRecipientName(recipient);
        payout.setCreatedBy(admin);
        return payout;
    }
}
