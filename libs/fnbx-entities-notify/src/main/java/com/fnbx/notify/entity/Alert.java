package com.fnbx.notify.entity;

import com.fnbx.notify.enums.AlertChannel;
import com.fnbx.notify.enums.AlertSeverity;
import com.fnbx.notify.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One notification, to one recipient, over one channel.
 *
 * <p><b>Grain:</b> a close that alerts two managers by email and Zalo produces
 * four rows. Group by {@code (sourceEntityId, alertType)} to collapse them.
 *
 * <h2>Why notify stays in the CORE group even though it watches cashclose</h2>
 * {@link #sourceModule} + {@link #sourceEntityId} form a <b>soft</b> link with no
 * FK, so notify depends on no FEATURE module and the "CORE does not depend on
 * FEATURE" rule of ADR-0001 holds. The price is no referential integrity on that
 * pair - a deliberate trade.
 *
 * <h2>The two most valuable columns</h2>
 * {@link #acknowledgedBy} / {@link #acknowledgedAt}. Without them you cannot
 * measure how many HIGH alerts were ignored - and an ignored alert is worse than
 * no alert, because it manufactures a false sense of safety.
 */
@Entity
@Table(schema = "notify", name = "alert")
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id")
    private UUID branchId;

    /** 'cashclose' | 'workforce' | 'inventory' - soft link, no FK. */
    @Column(name = "source_module", nullable = false)
    private String sourceModule;

    @Column(name = "source_entity_id")
    private UUID sourceEntityId;

    /** Who receives the notification. */
    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    /** The address actually used, snapshotted at send time for investigation. */
    @Column(name = "recipient_address")
    private String recipientAddress;

    /** Who the alert is about, e.g. "Tam: 3 unexplained gaps this week". */
    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, columnDefinition = "shared.alert_severity")
    private AlertSeverity severity;

    @Column(name = "message", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, columnDefinition = "shared.alert_channel")
    private AlertChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "shared.alert_status")
    private AlertStatus status = AlertStatus.PENDING;

    @Column(name = "sent_at")        private Instant sentAt;
    @Column(name = "failure_reason") private String failureReason;
    @Column(name = "retry_count", nullable = false) private int retryCount;

    @Column(name = "acknowledged_by") private UUID acknowledgedBy;
    @Column(name = "acknowledged_at") private Instant acknowledgedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public boolean isAcknowledged() { return acknowledgedAt != null; }
}
