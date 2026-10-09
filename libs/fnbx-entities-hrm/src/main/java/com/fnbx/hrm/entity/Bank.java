package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "payroll", name = "bank")
@Getter
@Setter
@NoArgsConstructor
public class Bank {

    @Id
    @Column(name = "bank_code")
    private String bankCode;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "short_name", nullable = false)
    private String shortName;

    @Column(name = "bin")
    private String bin;

    @Column(name = "is_active")
    private boolean active;
}
