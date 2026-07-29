package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** Day-level reconciliation for a business date, accounting for carry-forward across shifts. */
@Data
@Builder
public class DaySummaryDTO {

    private LocalDate businessDate;
    private Long branchId;
    private String branchCode;
    private long shiftCount;
    private List<Long> closeIds;
    private List<String> shiftNames;
    private Long currentCashCloseId;
    private String currentShiftTypeCode;
    private List<Long> priorCloseIds;
    private long dayTotalExpense;
    private long dayExpenseAffectingDiff;
    private long dayEndOfDayExpense;
    private long dayTipsAmount;
    private long dayCashDiff;
    private long priorExplainedDiff;
    private long currentExplainedDiff;
    private long dayExplainedDiff;
    private long dayUnexplainedDiff;
    private long effectiveUnexplainedDiff;
    private boolean appliedPriorCarry;
    private String carryMode;
    private long dayBillIssueAmount;
    private long dayWithdrawal;
}
