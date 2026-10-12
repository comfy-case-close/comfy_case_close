package com.fnbx.hrm.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hrm")
public record TentativeDecisionProperties(List<TentativeDecision> tentativeDecisions) {

    public TentativeDecisionProperties {
        tentativeDecisions = tentativeDecisions == null ? List.of() : List.copyOf(tentativeDecisions);
    }

    public record TentativeDecision(String code, String title, String revisitWhen) {}
}
