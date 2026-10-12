package com.fnbx.hrm.service.attendance;

import com.fnbx.hrm.enums.LateLevel;
import com.fnbx.hrm.repository.LatePenaltyRuleRepository;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LateRulebook {

    private final LatePenaltyRuleRepository repository;

    public Function<LocalDate, LatePenalties> cachedByDate() {
        Map<LocalDate, LatePenalties> cache = new HashMap<>();
        return date -> cache.computeIfAbsent(date, this::on);
    }

    public LatePenalties on(LocalDate date) {
        Map<LateLevel, LatePenalty> byLevel = new EnumMap<>(LateLevel.class);
        LatePenalties.RULE_CODES.forEach((level, code) -> repository.findEffective(code, date)
                .ifPresent(rule -> byLevel.put(level,
                        new LatePenalty(code, rule.getDeductHours(), rule.getDeductRatio(), rule.isVoidsShift()))));
        return new LatePenalties(byLevel);
    }
}
