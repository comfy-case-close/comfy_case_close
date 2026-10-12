package com.fnbx.hrm.entity;

import com.fnbx.hrm.enums.RegistrationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "payroll", name = "registration_window")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationWindow {

    @Id
    @Column(name = "registration_window_id")
    private UUID registrationWindowId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payroll.registration_status")
    private RegistrationStatus status;

    @Column(name = "opened_by")
    private UUID openedBy;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;
}
