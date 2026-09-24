package com.fnbx.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Job title (barista, server, shift lead).
 *
 * <p>Distinct from what a person may DO: the title is what is printed on the
 * badge, and authority is the set of permissions the title carries at one branch
 * ({@code identity.position_permission}). The same title can be worth different
 * permissions in two businesses, because the mapping is per tenant.
 */
@Entity
@Table(schema = "identity", name = "staff_position")
@Getter
@Setter
@NoArgsConstructor
public class StaffPosition {

    @Id
    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "position_code", nullable = false)
    private String positionCode;

    @Column(name = "position_name", nullable = false)
    private String positionName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
