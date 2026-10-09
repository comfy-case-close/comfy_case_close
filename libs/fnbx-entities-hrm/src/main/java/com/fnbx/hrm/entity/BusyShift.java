package com.fnbx.hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "payroll", name = "busy_shift")
@IdClass(BusyShiftId.class)
@Getter
@Setter
@NoArgsConstructor
public class BusyShift {

    @Id
    @Column(name = "availability_submission_id")
    private UUID availabilitySubmissionId;

    @Id
    @Column(name = "work_date")
    private LocalDate workDate;

    @Id
    @Column(name = "shift_slot_id")
    private UUID shiftSlotId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;
}
