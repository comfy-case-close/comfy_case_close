package com.fnbx.hrm.service.contractdoc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fnbx.hrm.enums.ContractTemplateKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StandardContractTemplateTest {

    private final StandardTemplateInstaller installer = new StandardTemplateInstaller(null, null, null, new ContractTemplateBodyBuilder(new TemplateBodyValidator()));
    private final PlaceholderRenderer placeholders = new PlaceholderRenderer();
    private final ContractPdfRenderer pdf = new ContractPdfRenderer();

    @ParameterizedTest
    @EnumSource(StandardContractTemplate.class)
    void everyBlankBecomesAPlaceholderAndTheBodyIsWellFormed(StandardContractTemplate standard) {
        String body = installer.bodyOf(standard);

        assertThat(body).contains("{{fullNameUpper}}", "{{contractNo}}", "{{signDate}}", "{{nationalIdNo}}", "{{baseSalary}}",
                "{{baseSalaryWords}}", "{{lunchAllowance}}", "{{responsibilityAllowance}}", "{{responsibilityAllowanceWords}}", "{{fixedTotal}}", "{{branch}}", "{{branchAddress}}");
        assertThat(body).doesNotContain("5.600.000", "Năm triệu sáu trăm nghìn", "highlight");
        if (standard.kind() == ContractTemplateKind.LABOR_CONTRACT) {
            assertThat(body).contains("{{contractPeriod}}", "{{contractTermType}}").doesNotContain("{{probationDays}}");
        } else {
            assertThat(body).contains("{{probationDays}}", "{{laborContractStart}}", "{{effectiveTo}}").doesNotContain("{{contractPeriod}}");
        }
    }

    @ParameterizedTest
    @EnumSource(StandardContractTemplate.class)
    void rendersToAPdfThatCarriesTheEmployeeData(StandardContractTemplate standard) throws IOException {
        Map<ContractPlaceholder, String> values = new EnumMap<>(ContractPlaceholder.class);
        ContractPlaceholder.all().forEach(placeholder -> values.put(placeholder, placeholder.sample()));
        byte[] bytes = pdf.render(placeholders.render(installer.bodyOf(standard), values));

        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isBetween(3, 14);
            assertThat(text).contains("NGUYỄN VĂN AN", "079200000001", "HỢP ĐỒNG").doesNotContain("{{", "[");
            assertThat(text).contains("Trang 1/" + document.getNumberOfPages());
        }
    }

    @Test
    void writesPreviewsWhenAskedTo() throws IOException {
        String directory = System.getProperty("contract.preview.dir");
        if (directory == null) {
            return;
        }
        Path folder = Path.of(directory);
        Files.createDirectories(folder);
        Map<ContractPlaceholder, String> values = new EnumMap<>(ContractPlaceholder.class);
        ContractPlaceholder.all().forEach(placeholder -> values.put(placeholder, placeholder.sample()));
        for (StandardContractTemplate standard : StandardContractTemplate.all()) {
            byte[] bytes = pdf.render(placeholders.render(installer.bodyOf(standard), values));
            Files.write(folder.resolve(standard.templateName() + ".pdf"), bytes);
            Files.writeString(folder.resolve(standard.templateName() + ".html"), installer.bodyOf(standard));
            try (PDDocument document = PDDocument.load(bytes)) {
                PDFRenderer renderer = new PDFRenderer(document);
                for (int page = 0; page < Math.min(2, document.getNumberOfPages()); page++) {
                    javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page, 80), "png",
                            folder.resolve(standard.templateName() + "-p" + (page + 1) + ".png").toFile());
                }
            }
        }
    }
}
