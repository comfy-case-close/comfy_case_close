package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "payroll", name = "contract_template")
@Getter
@Setter
@NoArgsConstructor
public class ContractTemplate {

    @Id
    @Column(name = "contract_template_id")
    private UUID contractTemplateId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "version")
    private int version;

    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    @Column(name = "is_active")
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
