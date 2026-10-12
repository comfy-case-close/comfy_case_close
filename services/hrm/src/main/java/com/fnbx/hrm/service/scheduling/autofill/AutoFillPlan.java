package com.fnbx.hrm.service.scheduling.autofill;

import java.util.List;

public record AutoFillPlan(List<Placed> proposals, List<UnfilledGap> gaps) {
}
