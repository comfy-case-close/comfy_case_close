package com.fnbx.hrm.service.contractimport;

import com.fnbx.hrm.config.HrmContractProperties;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractImportParser {

    private final HrmContractProperties properties;

    public List<ImportSheetRow> parse(InputStream file) {
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<Integer, ContractImportColumn> columns = readHeader(sheet.getRow(0));
            List<ImportSheetRow> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                Map<ContractImportColumn, String> cells = readCells(row, columns);
                if (!cells.isEmpty()) {
                    rows.add(new ImportSheetRow(index + 1, cells));
                }
            }
            if (rows.size() > properties.importMaxRows()) {
                throw new AppException(ErrorCode.IMPORT_TOO_MANY_ROWS);
            }
            return rows;
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof AppException appException) {
                throw appException;
            }
            throw new AppException(ErrorCode.IMPORT_FILE_INVALID);
        }
    }

    private Map<Integer, ContractImportColumn> readHeader(Row header) {
        Map<Integer, ContractImportColumn> columns = new java.util.HashMap<>();
        if (header == null) {
            throw new AppException(ErrorCode.IMPORT_FILE_INVALID);
        }
        for (Cell cell : header) {
            ContractImportColumn.byLabel(text(cell)).ifPresent(column -> columns.put(cell.getColumnIndex(), column));
        }
        if (!columns.containsValue(ContractImportColumn.EMPLOYEE_CODE)) {
            throw new AppException(ErrorCode.IMPORT_FILE_INVALID);
        }
        return columns;
    }

    private Map<ContractImportColumn, String> readCells(Row row, Map<Integer, ContractImportColumn> columns) {
        Map<ContractImportColumn, String> cells = new EnumMap<>(ContractImportColumn.class);
        if (row == null) {
            return cells;
        }
        columns.forEach((index, column) -> {
            String value = text(row.getCell(index));
            if (!value.isBlank()) {
                cells.put(column, value);
            }
        });
        return cells;
    }

    private String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> numeric(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC ? numeric(cell) : cell.getStringCellValue().trim();
            default -> "";
        };
    }

    private String numeric(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return date.toString();
        }
        return new DecimalFormat("0.##########").format(cell.getNumericCellValue());
    }
}
