package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Pre-submit preview of what earlier shifts of the same day carried forward. */
@Data
@Builder
public class CarryForwardDTO {

    private Long branchId;
    private String branchCode;
    private LocalDate businessDate;
    private Long shiftTypeId;
    private String shiftTypeCode;
    private long priorShiftCount;
    private List<Long> priorCloseIds;
    private List<String> priorShiftNames;
    private long priorCashDiff;
    private long priorExplainedDiff;
    private long priorUnexplainedDiff;
    private long priorExpense;
    private long priorTips;
    private List<PriorItem> priorItems;
    private List<PriorExpenseItem> priorExpenseItems;

    @Data
    @Builder
    public static class PriorItem {
        private Long cashCloseId;
        private String shiftTypeCode;
        private String shiftName;
        private OffsetDateTime submittedAt;
        private long cashDiff;
        private long explainedDiff;
        private long unexplainedDiff;
        private long totalExpense;
        private long tipsAmount;
    }

    @Data
    @Builder
    public static class PriorExpenseItem {
        private Long cashCloseId;
        private String shiftName;
        private String movementType;
        private String category;
        private long amount;
        private boolean affectsDiff;
        private String description;
    }
}
