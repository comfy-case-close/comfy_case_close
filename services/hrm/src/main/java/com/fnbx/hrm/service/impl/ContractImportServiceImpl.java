package com.fnbx.hrm.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnbx.hrm.dto.response.ImportJobResponse;
import com.fnbx.hrm.dto.response.ImportJobResponse.Counts;
import com.fnbx.hrm.dto.response.ImportJobResponse.ImportRowResponse;
import com.fnbx.hrm.dto.response.ImportJobResponse.IssueResponse;
import com.fnbx.hrm.entity.ImportJob;
import com.fnbx.hrm.entity.ImportJobRow;
import com.fnbx.hrm.enums.ImportJobType;
import com.fnbx.hrm.enums.ImportMode;
import com.fnbx.hrm.enums.ImportRowStatus;
import com.fnbx.hrm.enums.RunStatus;
import com.fnbx.hrm.repository.ImportJobRepository;
import com.fnbx.hrm.repository.ImportJobRowRepository;
import com.fnbx.hrm.service.ContractImportService;
import com.fnbx.hrm.service.contractimport.CommitMode;
import com.fnbx.hrm.service.contractimport.ContractImportColumn;
import com.fnbx.hrm.service.contractimport.ContractImportParser;
import com.fnbx.hrm.service.contractimport.ContractImportTemplateWriter;
import com.fnbx.hrm.service.contractimport.ContractRowCommitter;
import com.fnbx.hrm.service.contractimport.ContractRowValidator;
import com.fnbx.hrm.service.contractimport.ImportIssue;
import com.fnbx.hrm.service.contractimport.ImportSheetRow;
import com.fnbx.hrm.service.contractimport.ValidatedRow;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractImportServiceImpl implements ContractImportService {

    private static final TypeReference<Map<String, String>> RAW_TYPE = new TypeReference<>() {};

    private final ImportJobRepository jobRepository;
    private final ImportJobRowRepository rowRepository;
    private final ContractImportTemplateWriter templateWriter;
    private final ContractImportParser parser;
    private final ContractRowValidator validator;
    private final ContractRowCommitter committer;
    private final ObjectMapper objectMapper;
    private final BranchAccessGuard branchAccess;

    @Override
    public byte[] template() {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        return templateWriter.build();
    }

    @Override
    @Transactional
    public ImportJobResponse validate(String fileName, InputStream workbook) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        List<ImportSheetRow> sheetRows = parser.parse(workbook);
        ImportJob job = newJob(fileName);
        List<ValidatedRow> validated = new ArrayList<>();
        List<ImportJobRow> rows = new ArrayList<>();
        for (ImportSheetRow sheetRow : sheetRows) {
            ValidatedRow row = validator.validate(sheetRow, validated);
            validated.add(row);
            rows.add(newRow(job, sheetRow, row));
        }
        jobRepository.save(job);
        rowRepository.saveAll(rows);
        return respond(job, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportJobResponse get(UUID importJobId) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        return respond(requireJob(importJobId), rowRepository.findByImportJobIdOrderByRowNo(importJobId));
    }

    @Override
    @Transactional
    public ImportJobResponse commit(UUID importJobId, CommitMode mode) {
        branchAccess.requireBusiness(Permission.HR_RECORD_WRITE);
        ImportJob job = requireJob(importJobId);
        List<ImportJobRow> rows = rowRepository.findByImportJobIdOrderByRowNo(importJobId);
        if (job.getMode() == ImportMode.COMMIT) {
            return respond(job, rows);
        }
        List<ValidatedRow> validated = revalidate(rows);
        if (mode == CommitMode.ALL_OR_NOTHING && validated.stream().anyMatch(row -> !row.committable())) {
            throw new AppException(ErrorCode.IMPORT_NOT_COMMITTABLE);
        }
        for (int index = 0; index < rows.size(); index++) {
            apply(rows.get(index), validated.get(index));
        }
        job.setMode(ImportMode.COMMIT);
        job.setSummary(toJson(countsOf(rows)));
        jobRepository.save(job);
        rowRepository.saveAll(rows);
        return respond(job, rows);
    }

    private void apply(ImportJobRow stored, ValidatedRow row) {
        stored.setErrors(toJson(issuesOf(row)));
        if (!row.committable()) {
            stored.setStatus(ImportRowStatus.SKIPPED);
            return;
        }
        stored.setEmploymentAssignmentId(committer.commit(row));
        stored.setStatus(ImportRowStatus.COMMITTED);
    }

    private List<ValidatedRow> revalidate(List<ImportJobRow> rows) {
        List<ValidatedRow> validated = new ArrayList<>();
        for (ImportJobRow stored : rows) {
            validated.add(validator.validate(new ImportSheetRow(stored.getRowNo(), cellsOf(stored)), validated));
        }
        return validated;
    }

    private ImportJob newJob(String fileName) {
        ImportJob job = new ImportJob();
        job.setImportJobId(UUID.randomUUID());
        job.setBusinessId(TenantContext.current().businessId());
        job.setJobType(ImportJobType.CONTRACT_BULK);
        job.setMode(ImportMode.DRY_RUN);
        job.setStatus(RunStatus.SUCCEEDED);
        job.setFileName(fileName);
        job.setCreatedBy(TenantContext.current().userId());
        job.setCreatedAt(Instant.now());
        return job;
    }

    private ImportJobRow newRow(ImportJob job, ImportSheetRow sheetRow, ValidatedRow row) {
        ImportJobRow stored = new ImportJobRow();
        stored.setImportJobRowId(UUID.randomUUID());
        stored.setBusinessId(job.getBusinessId());
        stored.setImportJobId(job.getImportJobId());
        stored.setRowNo(sheetRow.rowNo());
        stored.setStatus(row.status());
        stored.setRaw(toJson(rawOf(sheetRow)));
        stored.setErrors(toJson(issuesOf(row)));
        return stored;
    }

    private Map<String, String> rawOf(ImportSheetRow sheetRow) {
        Map<String, String> raw = new java.util.LinkedHashMap<>();
        sheetRow.cells().forEach((column, value) -> raw.put(column.name(), value));
        return raw;
    }

    private Map<ContractImportColumn, String> cellsOf(ImportJobRow stored) {
        Map<ContractImportColumn, String> cells = new EnumMap<>(ContractImportColumn.class);
        fromJson(stored.getRaw(), RAW_TYPE).forEach((name, value) -> cells.put(ContractImportColumn.valueOf(name), value));
        return cells;
    }

    private List<IssueResponse> issuesOf(ValidatedRow row) {
        return row.issues().stream().map(this::toResponse).toList();
    }

    private IssueResponse toResponse(ImportIssue issue) {
        return new IssueResponse(issue.severity().name(), issue.code(), issue.message());
    }

    private ImportJobResponse respond(ImportJob job, List<ImportJobRow> rows) {
        List<ImportRowResponse> responses = rows.stream().map(this::toRowResponse).toList();
        return new ImportJobResponse(job.getImportJobId(), job.getFileName(), job.getMode().name(), job.getStatus().name(),
                countsOf(rows), responses);
    }

    private ImportRowResponse toRowResponse(ImportJobRow row) {
        List<IssueResponse> issues = fromJson(row.getErrors(), new TypeReference<List<IssueResponse>>() {});
        String code = fromJson(row.getRaw(), RAW_TYPE).get(ContractImportColumn.EMPLOYEE_CODE.name());
        return new ImportRowResponse(row.getRowNo(), row.getStatus().name(), code, issues, row.getEmploymentAssignmentId());
    }

    private Counts countsOf(List<ImportJobRow> rows) {
        return new Counts(rows.size(), count(rows, ImportRowStatus.VALID), count(rows, ImportRowStatus.WARNING),
                count(rows, ImportRowStatus.ERROR), count(rows, ImportRowStatus.COMMITTED));
    }

    private int count(List<ImportJobRow> rows, ImportRowStatus status) {
        return (int) rows.stream().filter(row -> row.getStatus() == status).count();
    }

    private ImportJob requireJob(UUID importJobId) {
        return jobRepository.findById(importJobId).orElseThrow(() -> new AppException(ErrorCode.IMPORT_JOB_NOT_FOUND));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize import data", ex);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot read stored import data", ex);
        }
    }
}
