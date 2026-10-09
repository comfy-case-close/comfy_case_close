package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.EducationGrade;
import com.fnbx.hrm.enums.EducationLevel;
import com.fnbx.hrm.enums.Gender;
import com.fnbx.hrm.enums.MaritalStatus;
import com.fnbx.hrm.enums.RecruitmentSource;
import com.fnbx.hrm.enums.TerminationReason;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Extension of {@code identity.staff} (the ERD's {@code EMPLOYEE}). Holds
 * exactly what {@code identity.staff}'s own Javadoc says must NOT live there:
 *
 * <blockquote>"Do not add salary, contract or ID-document columns. ADR-0003
 * decision 24: sensitive HR data lives in its own {@code payroll} schema with
 * its own DB role."</blockquote>
 *
 * <p>Bank details are financial data; {@link #hiredOn} / {@link #terminatedOn}
 * are payroll's employment-lifecycle view, distinct from identity's
 * login-account {@code isActive}. R10 keeps termination a date, never folded
 * into a status enum that a contract-type filter could accidentally skip.
 */
@Entity
@Table(schema = "payroll", name = "employee_profile")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeProfile {

    /** Same value as {@code identity.staff.staff_id}. */
    @Id
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** Text, never numeric - leading zeros are significant (e.g. {@code 0421 000 500 486}). */
    @Column(name = "bank_account_no")
    private String bankAccountNo;

    @Column(name = "bank_code")
    private String bankCode;

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "hired_on")
    private LocalDate hiredOn;

    /** NULL = still employed. */
    @Column(name = "terminated_on")
    private LocalDate terminatedOn;

    @Setter(AccessLevel.NONE)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "is_active", insertable = false, updatable = false)
    private boolean active;

    @Column(name = "note")
    private String note;

    /** Optimistic lock - spec section 11 lists {@code employee} among the concurrency-checked entities. */
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", columnDefinition = "payroll.gender")
    private Gender gender;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", columnDefinition = "payroll.marital_status")
    private MaritalStatus maritalStatus;

    @Column(name = "children_count")
    private Short childrenCount;

    @Column(name = "ethnicity")
    private String ethnicity;

    @Column(name = "religion")
    private String religion;

    @Column(name = "nationality", nullable = false)
    private String nationality = "VN";

    @Column(name = "national_id_no")
    private String nationalIdNo;

    @Column(name = "national_id_issued_on")
    private LocalDate nationalIdIssuedOn;

    @Column(name = "national_id_issued_place")
    private String nationalIdIssuedPlace;

    @Column(name = "social_insurance_no")
    private String socialInsuranceNo;

    @Column(name = "tax_code")
    private String taxCode;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "education_level", columnDefinition = "payroll.education_level")
    private EducationLevel educationLevel;

    @Column(name = "education_school")
    private String educationSchool;

    @Column(name = "education_major")
    private String educationMajor;

    @Column(name = "graduation_year")
    private Short graduationYear;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "education_grade", columnDefinition = "payroll.education_grade")
    private EducationGrade educationGrade;

    @Column(name = "personal_email")
    private String personalEmail;

    @Column(name = "address_street")
    private String addressStreet;

    @Column(name = "address_ward")
    private String addressWard;

    @Column(name = "address_province_code")
    private String addressProvinceCode;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "recruitment_source", columnDefinition = "payroll.recruitment_source")
    private RecruitmentSource recruitmentSource;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "termination_reason", columnDefinition = "payroll.termination_reason")
    private TerminationReason terminationReason;
}
