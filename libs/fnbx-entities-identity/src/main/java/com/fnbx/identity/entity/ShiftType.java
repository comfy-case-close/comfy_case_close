package com.fnbx.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A shift definition. Belongs to a tenant, not a global constant: a pub shift
 * (18:00-02:00) looks nothing like a cafe shift (06:00-14:00).
 */
@Entity
@Table(schema = "identity", name = "shift_type")
@Getter
@Setter
@NoArgsConstructor
public class ShiftType {

    @Id
    @Column(name = "shift_type_id")
    private UUID shiftTypeId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "shift_code", nullable = false)
    private String shiftCode;

    @Column(name = "shift_name", nullable = false)
    private String shiftName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "suggested_start_time")
    private LocalTime suggestedStartTime;

    @Column(name = "suggested_end_time")
    private LocalTime suggestedEndTime;

    /** Submitting after this local time flags the close as late. */
    @Column(name = "submit_deadline", nullable = false)
    private LocalTime submitDeadline;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
