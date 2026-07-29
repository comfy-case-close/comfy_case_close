package com.comfy.caseclose.entity;

import com.comfy.caseclose.utils.enums.ApprovalAction;
import com.comfy.caseclose.utils.enums.CashCloseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_close_id", nullable = false)
    private CashClose cashClose;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ApprovalAction action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_by_user_id", nullable = false)
    private User reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 20)
    private CashCloseStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private CashCloseStatus newStatus;
}
