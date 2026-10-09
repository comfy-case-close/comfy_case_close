package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.ImportJobRow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRowRepository extends JpaRepository<ImportJobRow, UUID> {

    List<ImportJobRow> findByImportJobIdOrderByRowNo(UUID importJobId);
}
