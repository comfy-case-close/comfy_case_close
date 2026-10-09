package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "payroll", name = "province")
@Getter
@Setter
@NoArgsConstructor
public class Province {

    @Id
    @Column(name = "province_code")
    private String provinceCode;

    @Column(name = "province_name", nullable = false)
    private String provinceName;
}
