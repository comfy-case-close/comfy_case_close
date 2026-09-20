package com.fnbx.integration.entity;

import com.fnbx.integration.enums.PosVendor;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One shift's sales, normalised across POS vendors.
 *
 * <h2>This table closes the biggest hole in the old system</h2>
 * In the spreadsheet, staff typed the expected cash revenue by hand - and that
 * number is the baseline the gap is measured against:
 * <pre>gap = counted cash - expected cash (hand-typed)</pre>
 * The person counting the money also typed the yardstick. Hiding a shortfall took
 * one keystroke: the entire control collapsed at a single input field.
 *
 * <p>{@link #cashSales} here comes straight from the POS. Staff cannot touch it.
 *
 * <h2>Append only</h2>
 * When the POS restates a figure, insert a new row with {@code revision + 1}
 * instead of updating. An approved close must remain explainable by the numbers
 * it <b>saw at approval time</b>, not by today's numbers.
 */
@Entity
@Table(schema = "integration", name = "shift_sales")
@Getter
@Setter
@NoArgsConstructor
public class ShiftSales {

    @Id
    @Column(name = "shift_sales_id")
    private UUID shiftSalesId;

    @Column(name = "business_id", nullable = false)   private UUID businessId;
    @Column(name = "branch_id", nullable = false)     private UUID branchId;
    @Column(name = "shift_type_id", nullable = false) private UUID shiftTypeId;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;

    /** The most important number in this table: the drawer's expected revenue. */
    @Column(name = "cash_sales", nullable = false)    private BigDecimal cashSales = BigDecimal.ZERO;
    @Column(name = "card_sales", nullable = false)    private BigDecimal cardSales = BigDecimal.ZERO;
    @Column(name = "ewallet_sales", nullable = false) private BigDecimal ewalletSales = BigDecimal.ZERO;
    @Column(name = "other_sales", nullable = false)   private BigDecimal otherSales = BigDecimal.ZERO;

    @Setter(AccessLevel.NONE)
    @Column(name = "gross_sales", insertable = false, updatable = false)
    private BigDecimal grossSales;

    @Column(name = "order_count", nullable = false)   private int orderCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_vendor", nullable = false, columnDefinition = "shared.pos_vendor")
    private PosVendor sourceVendor;

    @Column(name = "connection_id") private UUID connectionId;

    /** Incremented when the POS restates. Older rows are never deleted. */
    @Column(name = "revision", nullable = false) private int revision = 1;

    @Setter(AccessLevel.NONE)
    @Column(name = "synced_at", insertable = false, updatable = false)
    private Instant syncedAt;
}
