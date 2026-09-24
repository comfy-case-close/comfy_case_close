package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class TipJarDTO {

    private Scope scope;
    private Summary summary;
    private List<PayoutDTO> payouts;

    @Data
    @Builder
    public static class Scope {
        private Long branchId;
        private String branchCode;
        private String branchLabel;
        private LocalDate fromDate;
        private LocalDate toDate;
    }

    @Data
    @Builder
    public static class Summary {
        private long tipsIn;
        private long tipsInsideDrawer;
        private long paidOut;
        private long balance;
        private long payoutCount;
    }

    @Data
    @Builder
    public static class PayoutDTO {
        private Long id;
        private Long branchId;
        private String branchCode;
        private String branchName;
        private long amount;
        private LocalDate payoutDate;
        private String recipientName;
        private String note;
        private Long createdById;
        private String createdByName;
        private OffsetDateTime createdAt;
    }
}
