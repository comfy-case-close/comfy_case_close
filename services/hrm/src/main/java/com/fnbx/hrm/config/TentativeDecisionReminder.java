package com.fnbx.hrm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(TentativeDecisionProperties.class)
public class TentativeDecisionReminder implements ApplicationRunner {

    private final TentativeDecisionProperties properties;

    public TentativeDecisionReminder(TentativeDecisionProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.tentativeDecisions().forEach(decision ->
                log.warn("Tentative decision {}: {} - revisit when: {}",
                        decision.code(), decision.title(), decision.revisitWhen()));
    }
}
