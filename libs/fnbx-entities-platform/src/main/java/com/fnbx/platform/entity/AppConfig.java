package com.fnbx.platform.entity;

import com.fnbx.platform.enums.ConfigScope;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Business parameters the owner can change without a deploy.
 *
 * <p><b>Belongs here:</b> DIFF_ALLOWED_ABS, DIFF_ALERT_ABS, EXPENSE_ALERT_ABS,
 * SESSION_TTL_HOURS, REQUIRE_POS_IMAGE - values that differ per branch and move
 * with business decisions.
 *
 * <p><b>Does not belong here:</b> DB URLs, API keys, S3 buckets, developer
 * feature flags. Those live in environment variables.
 *
 * <p>No history is kept. Instead each {@code cash_close} snapshots the thresholds
 * that judged it, and every change is written to {@code audit_log} by a trigger.
 */
@Entity
@Table(schema = "platform", name = "app_config")
@Getter
@Setter
@NoArgsConstructor
public class AppConfig {

    @Id
    @Column(name = "config_id")
    private UUID configId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, columnDefinition = "shared.config_scope")
    private ConfigScope scope = ConfigScope.GLOBAL;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "jsonb")
    private String configValue;

    @Column(name = "description")
    private String description;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
