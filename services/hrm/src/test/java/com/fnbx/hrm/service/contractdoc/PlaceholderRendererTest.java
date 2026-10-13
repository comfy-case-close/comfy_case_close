package com.fnbx.hrm.service.contractdoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceholderRendererTest {

    private final PlaceholderRenderer renderer = new PlaceholderRenderer();

    @Test
    void keepsVietnameseLettersAsTheyAreSoTheXmlParserAcceptsThem() {
        String out = renderer.render("<p>{{fullName}}</p>", Map.of(ContractPlaceholder.FULL_NAME, "Lê Thùy Phúc Quý Ngô Hà"));

        assertThat(out).isEqualTo("<p>Lê Thùy Phúc Quý Ngô Hà</p>");
        assertThat(out).doesNotContain("&");
    }

    @Test
    void stillEscapesMarkup() {
        String out = renderer.render("{{fullName}}", Map.of(ContractPlaceholder.FULL_NAME, "A & B <b>\"x\"</b>"));

        assertThat(out).doesNotContain("<b>").contains("&amp;").contains("&lt;b&gt;");
    }

    @Test
    void anUnknownValueRendersEmpty() {
        assertThat(renderer.render("[{{fullName}}]", Map.of())).isEqualTo("[]");
    }
}
