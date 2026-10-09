package com.fnbx.hrm.service.contractimport;

import com.fnbx.hrm.exception.PayrollExceptions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

@Component
public class ContractImportTemplateWriter {

    public static final String SHEET_NAME = "Hợp đồng";

    public byte[] build() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Row header = sheet.createRow(0);
            ContractImportColumn[] columns = ContractImportColumn.values();
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index].label() + (columns[index].required() ? " *" : ""));
                header.getCell(index).setCellStyle(headerStyle(workbook, columns[index].required()));
                sheet.setColumnWidth(index, 22 * 256);
            }
            sheet.createFreezePane(1, 1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw PayrollExceptions.invalidField("The template could not be created");
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook, boolean required) {
        Font font = workbook.createFont();
        font.setBold(required);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }
}
