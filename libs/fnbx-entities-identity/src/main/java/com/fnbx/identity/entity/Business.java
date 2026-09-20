package com.fnbx.identity.entity;

import com.fnbx.shared.enums.BusinessType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A tenant. Comfy is one row here. */
@Entity
@Table(schema = "identity", name = "business")
@Getter
@Setter
@NoArgsConstructor
public class Business {

    @Id
    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "business_code", nullable = false, unique = true)
    private String businessCode;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, columnDefinition = "shared.business_type")
    private BusinessType businessType = BusinessType.CAFE;

    /**
     * ISO-4217. Cot trong DB la CHAR(3) (PostgreSQL bao cao la "bpchar",
     * JDBC type CHAR). Mac dinh Hibernate map String -> VARCHAR, nen
     * ddl-auto: validate se bao "found [bpchar (Types#CHAR)], but expecting
     * [varchar(3) (Types#VARCHAR)]". @JdbcTypeCode(CHAR) noi dung su that cho
     * Hibernate; KHONG doi schema.
     *
     * <p>columnDefinition = "char(3)" KHONG cuu duoc: no chi sinh DDL (o day
     * Liquibase lo) va so khop theo TEN kieu, ma "char(3)" khong bat dau bang
     * "bpchar". Chi @JdbcTypeCode doi duoc MA kieu.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "VND";

    /** Used to decide whether a close was submitted late, in local time. */
    @Column(name = "timezone", nullable = false)
    private String timezone = "Asia/Ho_Chi_Minh";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
