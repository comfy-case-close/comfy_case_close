package com.comfy.caseclose.entity;

import com.comfy.caseclose.utils.enums.StaffPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "staff_positions")
public class StaffPositionEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 50)
    private StaffPosition code;

    @Column(name = "display_title", nullable = false, unique = true, length = 100)
    private String displayTitle;
}
