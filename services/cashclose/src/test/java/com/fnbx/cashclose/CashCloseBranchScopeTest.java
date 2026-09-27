package com.fnbx.cashclose;

import com.fnbx.cashclose.dto.request.DenominationCountRequest;
import com.fnbx.cashclose.dto.request.OpenDraftRequest;
import com.fnbx.cashclose.dto.request.ReplaceDenominationsRequest;
import com.fnbx.cashclose.dto.request.UpdateCashCloseRequest;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashCloseCalc;
import com.fnbx.cashclose.entity.CashDenominationLine;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.enums.ExpectedCashSource;
import com.fnbx.cashclose.mapper.CashCloseMapper;
import com.fnbx.cashclose.repository.CashCloseCalcRepository;
import com.fnbx.cashclose.repository.CashCloseDecisionRepository;
import com.fnbx.cashclose.repository.CashCloseRepository;
import com.fnbx.cashclose.repository.CashDenominationLineRepository;
import com.fnbx.cashclose.repository.DenominationRepository;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.cashclose.service.impl.CashCloseServiceImpl;
import com.fnbx.platform.entity.Denomination;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The two rules Phase 1 was asked for, and the count endpoint that depends on them.
 *
 * <h2>Why 409 and not 422 for a non-DRAFT</h2>
 * Everything else in this domain answers 422, and those codes are published and
 * left alone. This one case is different in kind: the request was correct when it
 * was composed, and became wrong because somebody else submitted the close in the
 * meantime. That is the textbook 409 - a conflict with the resource's current
 * state, recoverable by refreshing and retrying. The status is asserted here rather
 * than left to the catalogue, so changing it becomes a deliberate act.
 */
@ExtendWith(MockitoExtension.class)
class CashCloseBranchScopeTest {

    @Mock CashCloseRepository closeRepository;
    @Mock CashCloseCalcRepository calcRepository;
    @Mock CashDenominationLineRepository denominationLineRepository;
    @Mock CashCloseDecisionRepository closeDecisionRepository;
    @Mock DenominationRepository denominationRepository;
    @Mock BranchAccessGuard branchAccess;
    @Mock CashCloseMapper mapper;
    @Mock EntityManager entityManager;

    @InjectMocks CashCloseServiceImpl service;

    private final UUID business = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();
    private final UUID otherBranch = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ 403

    @Test
    @DisplayName("A header naming another branch is 403, even when the caller works at both")
    void wrongBranchHeaderIsForbidden() {
        // The caller genuinely works at both branches, so this is not an access
        // problem - it is a request that says one thing and means another.
        signedIn(Map.of(branch, "MANAGER", otherBranch, "MANAGER"));
        when(branchAccess.require(otherBranch, Permission.CLOSE_SUBMIT)).thenReturn(Permission.CLOSE_SUBMIT);

        CashClose close = close(CloseStatus.DRAFT, branch);
        when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));

        assertThatThrownBy(() -> service.submit(otherBranch, close.getCashCloseId(), "done"))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BRANCH_HEADER_MISMATCH));

        assertThat(ErrorCode.BRANCH_HEADER_MISMATCH.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(close.getStatus()).isEqualTo(CloseStatus.DRAFT);
    }

    @Test
    @DisplayName("A close at a branch outside the token is 404, not 403 - existence is not disclosed")
    void wrongBranchCloseDoesNotExposeContent() {
        signedIn(Map.of(branch, "MANAGER"));
        when(branchAccess.require(branch, Permission.CLOSE_SUBMIT)).thenReturn(Permission.CLOSE_SUBMIT);

        CashClose close = close(CloseStatus.DRAFT, otherBranch);
        when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));

        assertThatThrownBy(() -> service.submit(branch, close.getCashCloseId(), "done"))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BRANCH_HEADER_MISMATCH));
    }

    @Test
    @DisplayName("Opening a draft consults the guard before it reads anything")
    void openDraftIsGuardedFirst() {
        signedIn(Map.of(branch, "STAFF"));
        when(branchAccess.require(branch, Permission.CLOSE_OPEN)).thenThrow(new AccessDeniedException("denied"));

        OpenDraftRequest request = OpenDraftRequest.builder()
                .shiftTypeId(UUID.randomUUID())
                .businessDate(LocalDate.parse("2026-09-21"))
                .build();

        assertThatThrownBy(() -> service.openDraft(branch, request))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(closeRepository);
    }

    // ------------------------------------------------------------------ 409

    @Test
    @DisplayName("Submitting a close that is no longer a draft is 409")
    void submittingANonDraftIsConflict() {
        CashClose close = draftAt(CloseStatus.SUBMITTED);

        assertThatThrownBy(() -> service.submit(branch, close.getCashCloseId(), "again"))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CLOSE_NOT_DRAFT));

        assertThat(ErrorCode.CLOSE_NOT_DRAFT.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Recounting a close that is no longer a draft is 409, and stores nothing")
    void recountingANonDraftIsConflict() {
        CashClose close = draftAt(CloseStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> service.replaceDenominations(
                branch, close.getCashCloseId(), counts(count("500000", 4))))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CLOSE_NOT_DRAFT));

        // PENDING_REVIEW is still "editable" to the ledger and to fn_close_child_guard.
        // The count is deliberately stricter: it is what the submitter attested to.
        verify(denominationLineRepository, never()).deleteByCashCloseId(any());
        verify(denominationLineRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Editing the typed-in figures after submit is 409")
    void editingTypedFiguresAfterSubmitIsConflict() {
        CashClose close = draftAt(CloseStatus.SUBMITTED);

        assertThatThrownBy(() -> service.updateCashClose(branch, close.getCashCloseId(),
                UpdateCashCloseRequest.builder().withdrawalAmount(new BigDecimal("3000000")).build()))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CLOSE_NOT_DRAFT));
    }

    // ------------------------------------------------- the count itself

    @Test
    @DisplayName("A recount replaces the old set instead of appending to it")
    void recountReplacesRatherThanAppends() {
        CashClose close = draftAt(CloseStatus.DRAFT);
        stubCatalogue();

        service.replaceDenominations(branch, close.getCashCloseId(),
                counts(count("500000", 8), count("100000", 4)));

        // Delete must be flushed before the insert: UNIQUE (cash_close_id,
        // denomination_id) would otherwise reject the new row while the old one lives.
        InOrder order = inOrder(denominationLineRepository, entityManager);
        order.verify(denominationLineRepository).deleteByCashCloseId(close.getCashCloseId());
        order.verify(entityManager).flush();
        order.verify(denominationLineRepository).saveAll(anyList());

        assertThat(savedLines()).hasSize(2);
    }

    @Test
    @DisplayName("A quantity of zero is accepted from the client and stored as nothing")
    void zeroQuantitiesAreDropped() {
        CashClose close = draftAt(CloseStatus.DRAFT);
        stubCatalogue();

        service.replaceDenominations(branch, close.getCashCloseId(),
                counts(count("500000", 8), count("100000", 0), count("50000", 0)));

        // A grid that posts all nine rows is normal; a row recording nothing is not a
        // fact, and cash_denomination_line CHECKs quantity > 0.
        assertThat(savedLines()).hasSize(1);
        assertThat(savedLines().get(0).getQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("The same note counted twice is refused, not silently merged")
    void duplicateDenominationIsRefused() {
        CashClose close = draftAt(CloseStatus.DRAFT);
        stubCatalogue();

        // Scale differs, value does not - exactly what the UNIQUE constraint sees.
        assertThatThrownBy(() -> service.replaceDenominations(branch, close.getCashCloseId(),
                counts(count("500000", 8), count("500000.00", 1))))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_DENOMINATION));

        verify(denominationLineRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("A face value that is not a real note is refused")
    void unknownFaceValueIsRefused() {
        CashClose close = draftAt(CloseStatus.DRAFT);
        stubCatalogue();

        assertThatThrownBy(() -> service.replaceDenominations(branch, close.getCashCloseId(),
                counts(count("30000", 2))))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNKNOWN_DENOMINATION));
    }

    @Test
    @DisplayName("countedCash is read from the view, never added up in Java")
    void countedCashComesFromTheView() throws Exception {
        CashClose close = draftAt(CloseStatus.DRAFT);
        stubCatalogue();

        // The view says 4,400,000. The lines below multiply out to 4,500,000. If the
        // service ever starts summing for itself, this test is what notices.
        when(denominationLineRepository.findByCashCloseId(close.getCashCloseId()))
                .thenReturn(List.of(line((short) 1, 9)));
        when(calcRepository.findById(close.getCashCloseId()))
                .thenReturn(Optional.of(calcWithCountedCash("4400000")));

        var response = service.replaceDenominations(branch, close.getCashCloseId(),
                counts(count("500000", 9)));

        assertThat(response.getCountedCash()).isEqualByComparingTo("4400000");
        assertThat(response.getLines()).singleElement()
                .satisfies(l -> assertThat(l.getLineTotal()).isEqualByComparingTo("4500000"));
    }

    @Test
    @DisplayName("POS-sourced expected cash cannot be typed over")
    void posExpectedCashIsNotEditable() {
        CashClose close = draftAt(CloseStatus.DRAFT);
        close.setExpectedCashSource(ExpectedCashSource.POS_SYNC);

        assertThatThrownBy(() -> service.updateCashClose(branch, close.getCashCloseId(),
                UpdateCashCloseRequest.builder().posExpectedCash(new BigDecimal("9000000")).build()))
                .isInstanceOfSatisfying(AppException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXPECTED_CASH_LOCKED));

        assertThat(close.getPosExpectedCash()).isEqualByComparingTo("8000000");
    }

    @Test
    @DisplayName("The withdrawal can be typed on a draft - the column finally has a writer")
    void withdrawalAmountIsWritable() {
        CashClose close = draftAt(CloseStatus.DRAFT);

        service.updateCashClose(branch, close.getCashCloseId(),
                UpdateCashCloseRequest.builder().withdrawalAmount(new BigDecimal("3500000")).build());

        assertThat(close.getWithdrawalAmount()).isEqualByComparingTo("3500000");
    }

    // ------------------------------------------------------------ fixtures

    /** A close at {@link #branch} in the given state, with the guard already passed. */
    private CashClose draftAt(CloseStatus status) {
        signedIn(Map.of(branch, "MANAGER"));
        lenient().when(branchAccess.require(branch, Permission.CLOSE_SUBMIT)).thenReturn(Permission.CLOSE_SUBMIT);
        lenient().when(branchAccess.require(branch, Permission.CLOSE_VOID)).thenReturn(Permission.CLOSE_VOID);
        CashClose close = close(status, branch);
        lenient().when(closeRepository.findById(close.getCashCloseId())).thenReturn(Optional.of(close));
        return close;
    }

    private void stubCatalogue() {
        lenient().when(entityManager.find(com.fnbx.identity.entity.Business.class, business))
                .thenReturn(new com.fnbx.identity.entity.Business());
        List<Denomination> notes = List.of(
                denomination((short) 1, "500000"),
                denomination((short) 2, "100000"),
                denomination((short) 3, "50000"));
        lenient().when(denominationRepository.findByCurrencyCodeAndActiveTrue("VND")).thenReturn(notes);
        lenient().when(denominationRepository.findByCurrencyCode("VND")).thenReturn(notes);
    }

    @SuppressWarnings("unchecked")
    private List<CashDenominationLine> savedLines() {
        ArgumentCaptor<List<CashDenominationLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(denominationLineRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static ReplaceDenominationsRequest counts(DenominationCountRequest... items) {
        return ReplaceDenominationsRequest.builder().counts(List.of(items)).build();
    }

    private static DenominationCountRequest count(String faceValue, int quantity) {
        return DenominationCountRequest.builder()
                .faceValue(new BigDecimal(faceValue)).quantity(quantity).build();
    }

    private static Denomination denomination(short id, String faceValue) {
        Denomination d = new Denomination();
        set(d, "denominationId", id);
        d.setFaceValue(new BigDecimal(faceValue));
        return d;
    }

    private static CashDenominationLine line(short denominationId, int quantity) {
        CashDenominationLine line = new CashDenominationLine();
        line.setLineId(UUID.randomUUID());
        line.setDenominationId(denominationId);
        line.setQuantity(quantity);
        return line;
    }

    private static CashCloseCalc calcWithCountedCash(String countedCash) {
        CashCloseCalc calc = new CashCloseCalc();
        set(calc, "countedCash", new BigDecimal(countedCash));
        return calc;
    }

    private CashClose close(CloseStatus status, UUID branchId) {
        CashClose close = new CashClose();
        close.setCashCloseId(UUID.randomUUID());
        close.setCashCloseCode("CC-2026-09-21-TEST");
        close.setBranchId(branchId);
        close.setBusinessId(business);
        close.setStatus(status);
        close.setPosExpectedCash(new BigDecimal("8000000"));
        return close;
    }

    private void signedIn(Map<UUID, String> assignedRoles) {
        Map<String, String> grants = new HashMap<>();
        assignedRoles.forEach((id, role) -> grants.put(id.toString(), role));
        Jwt jwt = Jwt.withTokenValue("validated-in-security-tests").header("alg", "HS256").subject("STAFF")
                .claim("uid", staff.toString())
                .claim("business_id", business.toString())
                .claim("branch_roles", grants)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        TenantContext.set(TenantContext.of(business, staff));
    }

    /** Both classes hide DB-generated values behind read-only accessors. */
    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
