package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.enums.ContractTemplateKind;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

public enum StandardContractTemplate {
    LABOR_BARISTA("HDLD-ISC-Mau-BA-Barista.docx", "BARISTA", ContractTemplateKind.LABOR_CONTRACT),
    LABOR_HEAD_OF_PRODUCT("HDLD-ISC-Mau-HP-HeadofProduct.docx", "HEAD_OF_PRODUCT", ContractTemplateKind.LABOR_CONTRACT),
    LABOR_PASTRY_CHEF("HDLD-ISC-Mau-PC-PastryChef.docx", "PASTRY_CHEF", ContractTemplateKind.LABOR_CONTRACT),
    LABOR_STORE_MANAGER("HDLD-ISC-Mau-SM-StoreManager.docx", "STORE_MANAGER", ContractTemplateKind.LABOR_CONTRACT),
    LABOR_SERVICE_STAFF("HDLD-ISC-Mau-ST-ServiceStaff.docx", "SERVICE_STAFF", ContractTemplateKind.LABOR_CONTRACT),
    PROBATION_BARISTA("HDTV-ISC-Mau-BA-Barista.docx", "BARISTA", ContractTemplateKind.PROBATION_AGREEMENT),
    PROBATION_HEAD_OF_PRODUCT("HDTV-ISC-Mau-HP-HeadofProduct.docx", "HEAD_OF_PRODUCT", ContractTemplateKind.PROBATION_AGREEMENT),
    PROBATION_PASTRY_CHEF("HDTV-ISC-Mau-PC-PastryChef.docx", "PASTRY_CHEF", ContractTemplateKind.PROBATION_AGREEMENT),
    PROBATION_STORE_MANAGER("HDTV-ISC-Mau-SM-StoreManager.docx", "STORE_MANAGER", ContractTemplateKind.PROBATION_AGREEMENT),
    PROBATION_SERVICE_STAFF("HDTV-ISC-Mau-ST-ServiceStaff.docx", "SERVICE_STAFF", ContractTemplateKind.PROBATION_AGREEMENT);

    private static final String RESOURCE_FOLDER = "/contract-templates/";
    private static final String WORD_EXTENSION = ".docx";

    private final String file;
    private final String positionCode;
    private final ContractTemplateKind kind;

    StandardContractTemplate(String file, String positionCode, ContractTemplateKind kind) {
        this.file = file;
        this.positionCode = positionCode;
        this.kind = kind;
    }

    public String file() {
        return file;
    }

    public String positionCode() {
        return positionCode;
    }

    public ContractTemplateKind kind() {
        return kind;
    }

    public String templateName() {
        return file.substring(0, file.length() - WORD_EXTENSION.length());
    }

    public InputStream open() {
        InputStream stream = StandardContractTemplate.class.getResourceAsStream(RESOURCE_FOLDER + file);
        if (stream == null) {
            throw new IllegalStateException("The bundled template " + file + " is missing from the classpath");
        }
        return stream;
    }

    public UnaryOperator<String> blankRewriter() {
        return StandardBlanks.rewriterFor(kind);
    }

    public static List<StandardContractTemplate> all() {
        return Arrays.asList(values());
    }
}
