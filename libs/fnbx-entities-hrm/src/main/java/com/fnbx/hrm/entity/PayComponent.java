package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.AppliesTo;
import com.fnbx.hrm.enums.ComponentType;
import com.fnbx.hrm.enums.ProrationBasis;
import com.fnbx.hrm.enums.ShareScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * The earnings/deductions catalogue (spec section 5.5):
 * {@code BASE_PAY, KPI_ALW, KPI_ADJ, RESP_ALW, LUNCH_ALW, HOUSING_ALW,
 * PHONE_ALW, FUEL_ALW, OT_PAY, WEEKEND_PREMIUM, BONUS, ADVANCE}.
 *
 * <p>Once earnings are rows here rather than spread-sheet cells, the two
 * different gross-pay formulas (FULLTIME vs PARTTIME) collapse into one
 * aggregate: {@code SUM(amount) WHERE componentType = EARNING} - the single
 * largest simplification the component table buys (architecture.md 2.2).
 */
@Entity
@Table(schema = "payroll", name = "pay_component")
@Getter
@Setter
@NoArgsConstructor
public class PayComponent {

    @Id
    @Column(name = "pay_component_id")
    private UUID payComponentId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "component_code", nullable = false)
    private String componentCode;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, columnDefinition = "payroll.component_type")
    private ComponentType componentType;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "proration_basis", nullable = false, columnDefinition = "payroll.proration_basis")
    private ProrationBasis prorationBasis = ProrationBasis.NONE;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "share_scope", nullable = false, columnDefinition = "payroll.share_scope")
    private ShareScope shareScope = ShareScope.LINE;

    /** DECISION[DEC-01]: tentative for the 4 person-level allowances - see {@link AppliesTo}. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, columnDefinition = "payroll.applies_to")
    private AppliesTo appliesTo = AppliesTo.BOTH;

    /** TRUE for BONUS, ADVANCE, KPI_ADJ. */
    @Column(name = "is_manual_input", nullable = false)
    private boolean manualInput;

    /** TRUE only for KPI_ADJ. */
    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;
}
