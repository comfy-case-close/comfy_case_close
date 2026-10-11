package com.fnbx.cashclose.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Links a close to a file in {@code files}.
 *
 * <p>{@code cashclose} stores no bytes and no kind - only the reference. The
 * kind lives on {@code files.stored_file}; a second copy here could disagree.
 * Movement receipts reference {@code stored_file} directly, not this table.
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

    @Column(name = "attached_by") private UUID attachedBy;
}
