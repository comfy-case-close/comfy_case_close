package com.fnbx.integration.entity;

import com.fnbx.integration.enums.PosVendor;
import com.fnbx.integration.enums.SyncStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Connection to a branch's POS system.
 *
 * <h2>Security: credentialRef is never the secret itself</h2>
 * This is customer data. One database leak would expose every customer's POS
 * credentials, and they could lose real money.
 *
 * <p>The column stores a reference such as
 * {@code secretsmanager:fnbx/pos/<uuid>} or {@code vault:secret/fnbx/pos/<uuid>}.
 * A CHECK constraint enforces that format, so a raw token cannot be pasted in.
 */
@Entity
@Table(schema = "integration", name = "pos_connection")
@Getter
@Setter
@NoArgsConstructor
public class PosConnection {

    @Id
    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor", nullable = false, columnDefinition = "shared.pos_vendor")
    private PosVendor vendor;

    @Column(name = "external_store_id")
    private String externalStoreId;

    /** Secret-manager reference, not a secret. See the class javadoc. */
    @Column(name = "credential_ref", nullable = false)
    private String credentialRef;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_sync_status")
    private SyncStatus lastSyncStatus;
}
