package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One earning or deduction line on a {@link PayrollLine} - what turns the two
 * different gross-pay formulas (FULLTIME {@code BD} vs PARTTIME {@code BF})
 * into one aggregate: {@code SUM(amount) WHERE componentType = EARNING}
 * (architecture.md 2.2).
 *
 * <p>{@link #quantity} / {@link #rate} are NULL for manual items (BONUS,
 * ADVANCE, KPI_ADJ) - only {@link #amount} is typed in directly.
 *
 * <p>The ERD's "UNIQUE (payrollLineId, componentId) for non-manual components
 * only" is enforced by the service layer, not a DB constraint - see the
 * comment in payroll/1.0.0.1-tables.xml for why a partial index cannot do it.
 */
@Entity
@Table(schema = "payroll", name = "payroll_line_item")
@Getter
@Setter
@NoArgsConstructor
public class PayrollLineItem {

    @Id
    @Column(name = "payroll_line_item_id")
    private UUID payrollLineItemId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "payroll_line_id", nullable = false)
    private UUID payrollLineId;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    /** Days or hours; NULL for manual items. */
    @Column(name = "quantity")
    private BigDecimal quantity;

    /** Unrounded rate used, 2dp for display (R11). */
    @Column(name = "rate")
    private BigDecimal rate;

    /** ENGINE or INPUT (manual); rounded to whole dong. */
    @Column(name = "amount", nullable = false, columnDefinition = "shared.d_money")
    private BigDecimal amount = BigDecimal.ZERO;

    /** Human-readable trace, e.g. {@code "16.50 days x 192,307.69"}. */
    @Column(name = "calc_note")
    private String calcNote;

    /** Manual items only. */
    @Column(name = "note")
    private String note;
}
