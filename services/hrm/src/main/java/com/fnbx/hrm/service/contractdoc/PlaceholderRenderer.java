package com.fnbx.hrm.service.contractdoc;

import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class PlaceholderRenderer {

    private static final String ENCODING = "UTF-8";

    public String render(String body, Map<ContractPlaceholder, String> values) {
        String rendered = AllowanceRows.withoutEmptyRows(body, key -> ContractPlaceholder.byKey(key).map(values::get).orElse(null));
        for (ContractPlaceholder placeholder : ContractPlaceholder.values()) {
            String value = Objects.requireNonNullElse(values.get(placeholder), "");
            rendered = rendered.replace("{{" + placeholder.key() + "}}", HtmlUtils.htmlEscape(value, ENCODING));
        }
        return rendered;
    }
}
