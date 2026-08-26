package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByCashCloseId(Long cashCloseId);

    /**
     * Projection instead of {@code findAll()} — OrphanAttachmentCleanupJob only needs the URLs to
     * cross-reference against bucket contents, not full entities with their {@code CashClose}
     * association loaded.
     */
    @Query("SELECT a.fileUrl FROM Attachment a")
    List<String> findAllFileUrls();
}
