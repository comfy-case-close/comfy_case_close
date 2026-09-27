package com.fnbx.identity.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RevokedTokenRepository {
    private final JdbcTemplate jdbc;
    public RevokedTokenRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Instant> revokedAt(UUID jti) {
        return jdbc.query("SELECT revoked_at FROM identity.revoked_token WHERE jti = ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), jti).stream().findFirst();
    }

    /** A duplicate must not abort the transaction or permit another token pair to be minted. */
    public boolean revoke(UUID jti, Instant expiresAt, Instant now) {
        return jdbc.update("""
            INSERT INTO identity.revoked_token (jti, expires_at, revoked_at) VALUES (?, ?, ?)
            ON CONFLICT (jti) DO NOTHING
            """, jti, Timestamp.from(expiresAt), Timestamp.from(now)) == 1;
    }

    public int deleteExpiredBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM identity.revoked_token WHERE expires_at < ?", Timestamp.from(cutoff));
    }
}
