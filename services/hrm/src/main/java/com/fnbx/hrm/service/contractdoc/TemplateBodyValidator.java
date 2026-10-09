package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.exception.PayrollExceptions;
import java.io.StringReader;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Component
public class TemplateBodyValidator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)}}");

    public void requireWellFormed(String bodyHtml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.newDocumentBuilder().parse(new InputSource(new StringReader("<root>" + bodyHtml + "</root>")));
        } catch (SAXException | java.io.IOException | ParserConfigurationException ex) {
            throw PayrollExceptions.invalidField("The template body must be well-formed XHTML: " + ex.getMessage());
        }
    }

    public void requireKnownPlaceholders(String bodyHtml) {
        Set<String> known = ContractPlaceholder.all().stream().map(ContractPlaceholder::key).collect(Collectors.toSet());
        String unknown = PLACEHOLDER.matcher(bodyHtml).results()
                .map(match -> match.group(1))
                .filter(key -> !known.contains(key))
                .distinct()
                .collect(Collectors.joining(", "));
        if (!unknown.isEmpty()) {
            throw PayrollExceptions.invalidField("The template uses unknown placeholders: " + unknown);
        }
    }
}
