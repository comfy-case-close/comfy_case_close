package com.fnbx.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
public class PermissionConfiguration {
 @Bean public BranchAccessGuard branchAccessGuard(JdbcTemplate jdbc) { return new BranchAccessGuard(jdbc); }
}
