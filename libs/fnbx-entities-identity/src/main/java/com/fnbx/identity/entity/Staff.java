package com.fnbx.identity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A person who works here.
 *
 * <p><b>Fan-in 14</b> - the most referenced table in the system. A change here can
 * reach 7 of 9 services, which is why this module is CODEOWNERS-locked.
 *
 * <p>Named {@code staff}, not {@code user}: {@code user} is a RESERVED keyword in
 * PostgreSQL and would need double quoting in every statement, view and native
 * query, with one forgotten quote failing only at runtime. {@code staff} is also
 * the more honest name - the row carries an employee code, a full name and a job
 * position.
 *
 * <p><b>Do not add salary, contract or ID-document columns.</b> ADR-0003 decision
 * 24: sensitive HR data lives in its own {@code payroll} schema with its own DB
 * role. Split by sensitivity, not by subject.
 */
@Entity
@Table(schema = "identity", name = "staff")
@Getter
@Setter
@NoArgsConstructor
public class Staff {

    @Id
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "position_id")
    private UUID positionId;

    /**
     * <b>Security warning.</b> The {@code sha256-legacy} algorithm is inherited
     * from the old spreadsheet: a 4-digit passcode hashed with unsalted SHA-256.
     * A 10,000-value search space breaks in seconds.
     *
     * <p>Production must use argon2id or bcrypt. {@link #passcodeAlgo} exists so
     * users can be migrated one at a time on next login instead of forcing a
     * global password reset.
     */
    @Column(name = "passcode_hash", nullable = false)
    private String passcodeHash;

    @Column(name = "passcode_algo", nullable = false)
    private String passcodeAlgo = "bcrypt";

    /**
     * Avatar: {@code files} owns the bytes, {@code identity} keeps the reference
     * plus an immutable snapshot ({@link #avatarUrl}, {@link #avatarSha256}).
     *
     * <p>Safe because files are immutable once uploaded. The day file-service is
     * extracted, dropping the constraint is enough - no consumer, no re-sync job.
     * Contrast with branch names, which do change, so other modules must join.
     */
    @Column(name = "avatar_file_id")
    private UUID avatarFileId;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "avatar_sha256")
    private String avatarSha256;

    @Column(name = "note")
    private String note;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
