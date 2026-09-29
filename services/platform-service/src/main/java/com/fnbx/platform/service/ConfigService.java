package com.fnbx.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Typed API over platform.app_config's branch > business > global rows. */
@Service
@RequiredArgsConstructor
public class ConfigService {
    private static final Set<String> NUMERIC = Set.of(
            "DIFF_ALLOWED_ABS", "DIFF_NOTE_REQUIRED_ABS", "DIFF_ALERT_ABS", "EXPENSE_ALERT_ABS",
            "WITHDRAWAL_ALERT_ABS", "FUND_WITHDRAWAL_WARNING_ABS", "DEFAULT_TARGET_CASH_REMAINING",
            "DEFAULT_CASH_REMAINING_TOLERANCE", "SESSION_TTL_HOURS");
    private static final Set<String> BOOLEAN = Set.of(
            "REQUIRE_POS_IMAGE", "REQUIRE_CASH_IMAGE", "REQUIRE_UNPAID_BILL_REPAYMENT",
            "REQUIRE_EXPENSE_RECEIPT_IMAGE", "REQUIRE_APPROVAL_SUPPLY",
            "REQUIRE_APPROVAL_GOODS_OR_SHIPPING", "REQUIRE_APPROVAL_REFUND",
            "REQUIRE_APPROVAL_STAFF_PARKING", "REQUIRE_APPROVAL_OTHER");
    private static final Set<String> TEXT = Set.of(
            "BILL_REPAYMENT_BANK_NAME", "BILL_REPAYMENT_ACCOUNT_NUMBER",
            "BILL_REPAYMENT_ACCOUNT_NAME", "BILL_REPAYMENT_TRANSFER_PREFIX");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final BranchAccessGuard access;

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID branchId) {
        requireBranch(branchId);
        Map<String, Object> effective = new LinkedHashMap<>();
        jdbc.query("""
                SELECT c.config_key, c.config_value::text FROM platform.app_config c
                JOIN identity.branch b ON b.branch_id=? AND b.business_id=?
                WHERE (c.scope='BRANCH' AND c.branch_id=b.branch_id AND c.business_id=b.business_id)
                   OR (c.scope='BUSINESS' AND c.business_id=b.business_id)
                   OR c.scope='GLOBAL'
                ORDER BY CASE c.scope WHEN 'BRANCH' THEN 1 WHEN 'BUSINESS' THEN 2 ELSE 3 END
                """, rs -> {
            String key = rs.getString(1);
            if (known(key)) effective.putIfAbsent(key, read(rs.getString(2)));
        }, branchId, TenantContext.current().businessId());
        return effective;
    }

    /** Business defaults, excluding any branch override. */
    @Transactional(readOnly = true)
    public Map<String, Object> getBusiness() {
        access.requireBusiness(Permission.BUSINESS_UPDATE);
        Map<String, Object> effective = new LinkedHashMap<>();
        jdbc.query("""
                SELECT c.config_key, c.config_value::text FROM platform.app_config c
                WHERE (c.scope='BUSINESS' AND c.business_id=?) OR c.scope='GLOBAL'
                ORDER BY CASE c.scope WHEN 'BUSINESS' THEN 1 ELSE 2 END
                """, rs -> {
            String key = rs.getString(1);
            if (known(key)) effective.putIfAbsent(key, read(rs.getString(2)));
        }, TenantContext.current().businessId());
        return effective;
    }

    @Transactional
    public Map<String, Object> update(UUID branchId, Map<String, JsonNode> values) {
        access.require(branchId, Permission.CONFIG_WRITE);
        requireBranch(branchId);
        if (values == null || values.isEmpty()) throw invalid("Provide configuration values");
        // Validate the entire request before writing any row.
        values.forEach(this::validate);
        UUID businessId = TenantContext.current().businessId();
        UUID actor = TenantContext.current().userId();
        values.forEach((key, value) -> {
            int changed = jdbc.update("""
                    UPDATE platform.app_config SET config_value=?::jsonb, updated_by=?
                    WHERE scope='BRANCH' AND business_id=? AND branch_id=? AND config_key=?
                    """, value.toString(), actor, businessId, branchId, key);
            if (changed == 0) jdbc.update("""
                    INSERT INTO platform.app_config(scope,business_id,branch_id,config_key,config_value,updated_by)
                    VALUES ('BRANCH',?,?,?,?::jsonb,?)
                    """, businessId, branchId, key, value.toString(), actor);
        });
        return get(branchId);
    }

    @Transactional
    public Map<String, Object> updateBusiness(Map<String, JsonNode> values) {
        access.requireBusiness(Permission.BUSINESS_UPDATE);
        if (values == null || values.isEmpty()) throw invalid("Provide configuration values");
        values.forEach(this::validate);
        UUID businessId = TenantContext.current().businessId();
        UUID actor = TenantContext.current().userId();
        values.forEach((key, value) -> {
            int changed = jdbc.update("""
                    UPDATE platform.app_config SET config_value=?::jsonb, updated_by=?
                    WHERE scope='BUSINESS' AND business_id=? AND config_key=?
                    """, value.toString(), actor, businessId, key);
            if (changed == 0) jdbc.update("""
                    INSERT INTO platform.app_config(scope,business_id,config_key,config_value,updated_by)
                    VALUES ('BUSINESS',?,?,?::jsonb,?)
                    """, businessId, key, value.toString(), actor);
        });
        return getBusiness();
    }

    private void requireBranch(UUID branchId) {
        if (branchId == null || access.effective(branchId).isEmpty())
            throw new AccessDeniedException("Branch access denied");
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM identity.branch WHERE branch_id=? AND business_id=? AND is_active)
                """, Boolean.class, branchId, TenantContext.current().businessId());
        if (!exists) throw new AccessDeniedException("Branch access denied");
    }

    private boolean known(String key) { return NUMERIC.contains(key) || BOOLEAN.contains(key) || TEXT.contains(key); }

    private Object read(String raw) {
        try { return json.readValue(raw, Object.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored config JSON", e); }
    }

    private void validate(String key, JsonNode value) {
        if (!known(key) || value == null || value.isNull())
            throw invalid("Unknown or null configuration key: " + key);
        if (NUMERIC.contains(key)) {
            if (!value.isNumber() || new BigDecimal(value.asText()).signum() < 0
                    || new BigDecimal(value.asText()).scale() > 0)
                throw invalid(key + " must be a non-negative integer");
            if (key.equals("SESSION_TTL_HOURS") && value.asLong() < 1)
                throw invalid("SESSION_TTL_HOURS must be positive");
        } else if (BOOLEAN.contains(key) && !value.isBoolean()) {
            throw invalid(key + " must be boolean");
        } else if (TEXT.contains(key) && (!value.isTextual() || value.asText().isBlank())) {
            throw invalid(key + " must be nonblank text");
        }
    }

    private AppException invalid(String message) { return new AppException(ErrorCode.VALIDATION_FAILED, message); }
}
