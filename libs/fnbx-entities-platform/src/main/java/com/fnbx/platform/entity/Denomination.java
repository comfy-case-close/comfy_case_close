package com.fnbx.platform.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A bank note or coin. Global, not tenant-scoped, so no business_id and no RLS. */
@Entity
@Table(schema = "platform", name = "denomination")
@Getter
@Setter
@NoArgsConstructor
public class Denomination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "denomination_id")
    private Short denominationId;

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

    @Column(name = "face_value", nullable = false)
    private BigDecimal faceValue;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
