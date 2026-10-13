package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.enums.LateLevel;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

public final class LatePenalties {

    public static final Map<LateLevel, String> RULE_CODES = Map.of(
            LateLevel.LEVEL_0, "T1", LateLevel.LEVEL_1, "T2", LateLevel.LEVEL_2, "T3");

    private static final LatePenalties DEFAULTS = new LatePenalties(new EnumMap<>(Map.of(
            LateLevel.LEVEL_0, new LatePenalty("T1", BigDecimal.ONE, null, false),
            LateLevel.LEVEL_1, new LatePenalty("T2", null, new BigDecimal("0.5"), false),
            LateLevel.LEVEL_2, new LatePenalty("T3", null, null, true))));

    private final Map<LateLevel, LatePenalty> byLevel;

    public LatePenalties(Map<LateLevel, LatePenalty> byLevel) {
        this.byLevel = byLevel;
    }

    public static LatePenalties defaults() {
        return DEFAULTS;
    }

    public LatePenalty of(LateLevel level) {
        return byLevel.getOrDefault(level, DEFAULTS.byLevel.get(level));
    }
}
