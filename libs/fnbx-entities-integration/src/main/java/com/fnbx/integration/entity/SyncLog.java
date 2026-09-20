package com.fnbx.integration.entity;

import com.fnbx.integration.enums.SyncStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * POS sync log. Exists for two concrete reasons:
 *
 * <ol>
 *   <li><b>Reconciliation.</b> When the owner asks why yesterday's revenue differs
 *       from the iPOS report, you need to know exactly when the API was called and
 *       what came back.</li>
 *   <li><b>Retry.</b> A POS going down at 22:00 is routine. You need to know which
 *       run failed and how many attempts have been made.</li>
 * </ol>
 *
 * <p>Raw payloads go to {@code files} via {@link #rawPayloadFileId} rather than
 * into a JSONB column: 200 tenants x 3 branches x 3 shifts is 1,800 payloads a day.
 */
@Entity
@Table(schema = "integration", name = "sync_log")
@Getter
@Setter
@NoArgsConstructor
public class SyncLog {

    @Id
    @Column(name = "sync_id")
    private UUID syncId;

    @Column(name = "business_id", nullable = false)   private UUID businessId;
    @Column(name = "connection_id", nullable = false) private UUID connectionId;

    /** SHIFT_SALES | MENU | INVENTORY */
    @Column(name = "sync_type", nullable = false)     private String syncType;

    @Column(name = "period_from", nullable = false)   private Instant periodFrom;
    @Column(name = "period_to", nullable = false)     private Instant periodTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "shared.sync_status")
    private SyncStatus status;

    @Column(name = "rows_ingested", nullable = false) private int rowsIngested;
    @Column(name = "raw_payload_file_id")             private UUID rawPayloadFileId;
    @Column(name = "error_message")                   private String errorMessage;
    @Column(name = "retry_count", nullable = false)   private int retryCount;

    @Setter(AccessLevel.NONE)
    @Column(name = "started_at", insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at") private Instant finishedAt;
}
