package com.comfy.caseclose.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "shift_types")
public class ShiftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_type_code", nullable = false, unique = true, length = 50)
    private String shiftTypeCode;

    @Column(name = "shift_name", nullable = false, length = 100)
    private String shiftName;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    @Column(name = "suggested_start_time")
    private LocalTime suggestedStartTime;

    @Column(name = "suggested_end_time")
    private LocalTime suggestedEndTime;

    @Column(name = "submit_deadline")
    private LocalTime submitDeadline;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
