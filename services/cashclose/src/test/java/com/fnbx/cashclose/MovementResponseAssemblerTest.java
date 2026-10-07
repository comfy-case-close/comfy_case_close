package com.fnbx.cashclose;

import com.fnbx.cashclose.dto.response.CashMovementResponse;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.enums.CloseStatus;
import com.fnbx.cashclose.mapper.CashCloseMapper;
import com.fnbx.cashclose.service.MovementResponseAssembler;
import com.fnbx.files.entity.StoredFile;
import com.fnbx.platform.entity.MovementKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovementResponseAssemblerTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final CashCloseMapper mapper = mock(CashCloseMapper.class);
    private final MovementResponseAssembler assembler = new MovementResponseAssembler(mapper, entityManager);

    /** The page that broke: no line has a receipt, so no file is loaded at all. */
    @Test void linesWithoutReceiptsCarryTheirCloseAndNoFile() {
        CashClose close = new CashClose();
        close.setCashCloseId(UUID.randomUUID());
        close.setBranchId(UUID.randomUUID());
        close.setShiftTypeId(UUID.randomUUID());
        close.setBusinessDate(LocalDate.of(2026, 10, 3));
        close.setStatus(CloseStatus.SUBMITTED);
        MovementKind kind = mock(MovementKind.class);
        when(kind.getKindSk()).thenReturn(2L);
        CashMovement first = line(close, 2L);
        CashMovement second = line(close, 2L);
        returns(MovementKind.class, kind);
        returns(CashClose.class, close);
        when(mapper.toResponse(any(), any())).thenAnswer(invocation -> new CashMovementResponse());

        List<CashMovementResponse> responses = assembler.toResponses(List.of(first, second));

        assertThat(responses).hasSize(2).allSatisfy(response -> {
            assertThat(response.getBusinessDate()).isEqualTo(close.getBusinessDate());
            assertThat(response.getShiftTypeId()).isEqualTo(close.getShiftTypeId());
            assertThat(response.getBranchId()).isEqualTo(close.getBranchId());
            assertThat(response.getCloseStatus()).isEqualTo("SUBMITTED");
            assertThat(response.getFileKind()).isNull();
        });
        // One query per kind of row, not per line - and none for files when nothing has a receipt.
        verify(entityManager, never()).createQuery(anyString(), eq(StoredFile.class));
    }

    @SuppressWarnings("unchecked")
    private <E> void returns(Class<E> type, E row) {
        TypedQuery<E> query = mock(TypedQuery.class);
        when(query.setParameter(eq("ids"), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(row));
        when(entityManager.createQuery(anyString(), eq(type))).thenReturn(query);
    }

    private static CashMovement line(CashClose close, long kindSk) {
        CashMovement movement = new CashMovement();
        movement.setMovementId(UUID.randomUUID());
        movement.setCashCloseId(close.getCashCloseId());
        movement.setKindSk(kindSk);
        movement.setSignedAmount(new BigDecimal("-62000"));
        return movement;
    }
}
