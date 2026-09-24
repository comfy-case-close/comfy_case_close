package com.fnbx.cashclose.entity;

import com.fnbx.shared.enums.FileKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Links a close to a file in {@code files}.
 *
 * <p>{@code cashclose} stores no bytes - only the reference plus the file's
 * BUSINESS meaning ({@link #fileKind}). That is the right boundary: files knows
 * "this is a 2MB image, sha256 abc"; cashclose knows "this is the POS report for
 * CC-20260815-TX-EVENING".
 */
@Entity
@Table(schema = "cashclose", name = "close_attachment")
@Getter
@Setter
@NoArgsConstructor
public class CloseAttachment {

    @Id
    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Column(name = "cash_close_id", nullable = false) private UUID cashCloseId;
    @Column(name = "business_id", nullable = false)   private UUID businessId;
    @Column(name = "file_id", nullable = false)       private UUID fileId;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "file_kind", nullable = false, columnDefinition = "shared.file_kind")
    private FileKind fileKind;

    @Column(name = "attached_by") private UUID attachedBy;
}
