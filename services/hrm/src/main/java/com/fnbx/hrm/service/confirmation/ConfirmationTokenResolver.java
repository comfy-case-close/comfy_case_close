package com.fnbx.hrm.service.confirmation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Finds the tenant a confirmation token belongs to; the only query that runs before a tenant is known. */
@Component
@RequiredArgsConstructor
public class ConfirmationTokenResolver {

    private final JdbcTemplate jdbc;

    public Optional<UUID> resolveBusiness(String tokenHash) {
        List<UUID> businesses = jdbc.queryForList(
                "SELECT business_id FROM payroll.fn_resolve_payslip_confirmation(?)", UUID.class, tokenHash);
        return businesses.stream().findFirst();
    }
}
