package com.comfy.caseclose.entity;

import com.comfy.caseclose.utils.enums.MovementCategory;
import com.comfy.caseclose.utils.enums.MovementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "cash_movements")
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_close_id", nullable = false)
    private CashClose cashClose;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private MovementCategory category;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "person_or_vendor", length = 200)
    private String personOrVendor;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_attachment_id")
    private Attachment receiptAttachment;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
