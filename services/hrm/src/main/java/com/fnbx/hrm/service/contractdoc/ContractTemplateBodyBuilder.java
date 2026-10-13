package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.service.contractdoc.docx.DocxToXhtmlConverter;
import java.io.InputStream;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContractTemplateBodyBuilder {

    private static final Pattern STYLE_BLOCK = Pattern.compile("<style>.*?</style>", Pattern.DOTALL);
    private static final Pattern UNFILLED_BLANK = Pattern.compile("\\[[^\\]<>]{1,60}]");

    private final TemplateBodyValidator bodyValidator;

    public String fromDocx(InputStream docx, UnaryOperator<String> blankRewriter, String sourceName) {
        String body = AllowanceRows.addAfterLunchRow(convert(docx, sourceName, blankRewriter));
        requireNoUnfilledBlank(sourceName, body);
        bodyValidator.requireWellFormed(body);
        bodyValidator.requireKnownPlaceholders(body);
        return body;
    }

    private String convert(InputStream docx, String sourceName, UnaryOperator<String> blankRewriter) {
        try {
            return new DocxToXhtmlConverter(blankRewriter).convert(docx);
        } catch (IllegalArgumentException ex) {
            throw PayrollExceptions.invalidField(sourceName + ": " + ex.getMessage());
        }
    }

    private void requireNoUnfilledBlank(String sourceName, String body) {
        Matcher unfilled = UNFILLED_BLANK.matcher(STYLE_BLOCK.matcher(body).replaceAll(""));
        if (unfilled.find()) {
            throw PayrollExceptions.invalidField(sourceName + " has a blank that is not a known placeholder: " + unfilled.group()
                    + ". Write {{key}} for the values the system fills in.");
        }
    }
}
