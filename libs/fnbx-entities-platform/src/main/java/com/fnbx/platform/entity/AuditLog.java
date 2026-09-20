package com.fnbx.platform.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail. <b>Insert only.</b>
 *
 * <p>Enforced at two layers: the {@code shared.fn_forbid_mutation} trigger, and
 * {@code REVOKE UPDATE, DELETE} on every service role. They complement each
 * other - a trigger can be disabled by anyone with the privilege, a grant cannot.
 *
 * <h2>Deliberately narrow scope</h2>
 * Only events with no business table of their own: LOGIN_*, CONFIG_*, ROLE_*,
 * CASH_CLOSE_VOIDED, DATA_EXPORTED, USER_*.
 *
 * <p>Close status changes are <b>not</b> written here - they live in
 * {@code cashclose.cash_close_decision}, which carries old_status/new_status/action.
 * Writing both places duplicates the truth, and the two copies will drift.
 *
 * <p>Application and debug logs are not written here either; those belong in
 * Datadog/ELK. This table is for auditors, not developers.
 */
@Entity
@Table(schema = "platform", name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "business_id")
    private UUID businessId;

    @Setter(AccessLevel.NONE)
    @Column(name = "occurred_at", insertable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;
}
