package com.fnbx.identity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A login session.
 *
 * <p>Not for "keeping the user logged in" - the JWT does that. This table answers
 * two questions the JWT cannot:
 *
 * <ol>
 *   <li><b>Immediate revocation.</b> Someone quits at 22:00 but their token is
 *       valid until 10:00 tomorrow. Without this row there is no way to stop them.</li>
 *   <li><b>Investigation.</b> "Which device and IP submitted the 15 Aug close?" -
 *       asked when one person is suspected of submitting on another's behalf.</li>
 * </ol>
 */
@Entity
@Table(schema = "identity", name = "staff_session")
@Getter
@Setter
@NoArgsConstructor
public class StaffSession {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    public boolean isLive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
