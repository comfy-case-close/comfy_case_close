package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollAuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollAuditLogRepository extends JpaRepository<PayrollAuditLog, Long> {

    @Query("""
           SELECT a FROM PayrollAuditLog a
           WHERE (:anyEntityType = TRUE OR a.entityType = :entityType)
             AND (:anyEntityId = TRUE OR a.entityId = :entityId)
             AND (:anyFrom = TRUE OR a.occurredAt >= :from)
             AND (:anyTo = TRUE OR a.occurredAt <= :to)
           ORDER BY a.occurredAt DESC
           """)
    List<PayrollAuditLog> searchAuditLogsFiltered(@Param("anyEntityType") boolean anyEntityType,
            @Param("entityType") String entityType, @Param("anyEntityId") boolean anyEntityId,
            @Param("entityId") String entityId, @Param("anyFrom") boolean anyFrom, @Param("from") Instant from,
            @Param("anyTo") boolean anyTo, @Param("to") Instant to);

    default List<PayrollAuditLog> searchAuditLogs(String entityType, String entityId, Instant from, Instant to) {
        return searchAuditLogsFiltered(entityType == null, entityType, entityId == null, entityId,
                from == null, from, to == null, to);
    }
}
