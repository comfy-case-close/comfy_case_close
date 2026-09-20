package com.fnbx.cashclose.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One count line: how many notes of denomination X.
 *
 * <p>Counting by denomination rather than typing a total forces staff to look at
 * the actual cash. Typing "4,450,000" takes three seconds and can be invented;
 * counting 8 x 500k, 4 x 100k, 1 x 50k requires the money in hand.
 *
 * <p><b>This is the only source of counted cash.</b> {@code cash_close} no longer
 * carries a {@code counted_cash} column. In Comfy's real data 16 of 181 closes
 * had a stored total that disagreed with the denomination count - caused by
 * duplicate IDs appending a recount instead of overwriting it. The unique
 * constraint on {@code (cash_close_id, denomination_id)} makes that impossible,
 * and deriving the total removes the place where it could happen at all.
 *
 * <p>{@code businessId} is denormalised on purpose so RLS applies directly here
 * without joining up to {@code cash_close} on every query. Safe because the
 * composite FK {@code (cash_close_id, business_id)} makes a mismatch impossible.
 */
@Entity
@Table(schema = "cashclose", name = "cash_denomination_line")
@Getter
@Setter
@NoArgsConstructor
public class CashDenominationLine {

    @Id
    @Column(name = "line_id")
    private UUID lineId;

    @Column(name = "cash_close_id", nullable = false)   private UUID cashCloseId;
    @Column(name = "business_id", nullable = false)     private UUID businessId;
    @Column(name = "denomination_id", nullable = false) private Short denominationId;
    @Column(name = "quantity", nullable = false)        private int quantity;
}
