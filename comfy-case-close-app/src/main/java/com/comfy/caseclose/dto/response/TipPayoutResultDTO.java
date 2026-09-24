package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TipPayoutResultDTO {

    private TipJarDTO.PayoutDTO payout;
    private long balanceAfter;
    @Builder.Default
    private List<WarningDTO> warnings = List.of();
}
