package com.fnbx.hrm.service.contractdoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AllowanceRowsTest {

    private static final String TABLE = "<table><tr><td>Khoản</td><td>Mức</td></tr>"
            + "<tr style=\"page-break-inside: avoid\"><td><p>Tiền ăn giữa ca</p></td><td>{{lunchAllowance}}</td><td>{{lunchAllowanceWords}}</td></tr></table>";

    private final PlaceholderRenderer renderer = new PlaceholderRenderer();

    @Test
    void addsOneConditionalRowPerOtherAllowanceRightAfterTheLunchRow() {
        String body = AllowanceRows.addAfterLunchRow(TABLE);

        assertThat(body).contains("Phụ cấp trách nhiệm", "Hỗ trợ thuê nhà", "Hỗ trợ điện thoại", "Hỗ trợ xăng xe", "Phụ cấp bù lương");
        assertThat(body).contains("{{responsibilityAllowance}}", "{{responsibilityAllowanceWords}}");
        assertThat(body.indexOf("Tiền ăn giữa ca")).isLessThan(body.indexOf("Phụ cấp trách nhiệm"));
        assertThat(body.split("Tiền ăn giữa ca", -1)).hasSize(2);
    }

    @Test
    void aTableWithoutALunchRowIsLeftAlone() {
        assertThat(AllowanceRows.addAfterLunchRow("<table><tr><td>x</td></tr></table>")).isEqualTo("<table><tr><td>x</td></tr></table>");
    }

    @Test
    void anAllowanceOfZeroDropsItsRowAndAnAmountKeepsIt() {
        Map<ContractPlaceholder, String> values = new EnumMap<>(ContractPlaceholder.class);
        values.put(ContractPlaceholder.LUNCH_ALLOWANCE, "0 đồng");
        values.put(ContractPlaceholder.LUNCH_ALLOWANCE_WORDS, "Không đồng");
        values.put(ContractPlaceholder.RESPONSIBILITY_ALLOWANCE, "1.000.000 đồng");
        values.put(ContractPlaceholder.RESPONSIBILITY_ALLOWANCE_WORDS, "Một triệu đồng");
        values.put(ContractPlaceholder.HOUSING_ALLOWANCE_WORDS, "");

        String html = renderer.render(AllowanceRows.addAfterLunchRow(TABLE), values);

        assertThat(html).contains("Tiền ăn giữa ca", "Phụ cấp trách nhiệm", "1.000.000 đồng", "Một triệu đồng");
        assertThat(html).doesNotContain("Hỗ trợ thuê nhà", "Hỗ trợ xăng xe", "Phụ cấp bù lương", "data-requires");
    }
}
