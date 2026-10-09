package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.response.ImportJobResponse;
import com.fnbx.hrm.service.contractimport.CommitMode;
import java.io.InputStream;
import java.util.UUID;

/** Bulk contract creation from a workbook: validate first, write only on an explicit commit. */
public interface ContractImportService {

    byte[] template();

    /** Validates every row and stores the outcome; nothing is written to employees or contracts. */
    ImportJobResponse validate(String fileName, InputStream workbook);

    ImportJobResponse get(UUID importJobId);

    /** Writes the committable rows in one transaction; committing a finished job returns its result again. */
    ImportJobResponse commit(UUID importJobId, CommitMode mode);
}
