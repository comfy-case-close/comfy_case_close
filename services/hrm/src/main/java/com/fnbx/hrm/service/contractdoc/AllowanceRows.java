package com.fnbx.hrm.service.contractdoc;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AllowanceRows {

    static final String CONDITION_ATTRIBUTE = "data-requires";

    private static final Pattern LUNCH_ROW = Pattern.compile(
            "<tr[^>]*>(?:(?!</tr>).)*\\{\\{lunchAllowance}}(?:(?!</tr>).)*</tr>", Pattern.DOTALL);
    private static final Pattern CONDITIONAL_ROW = Pattern.compile(
            "<tr " + CONDITION_ATTRIBUTE + "=\"(\\w+)\"[^>]*>(?:(?!</tr>).)*</tr>", Pattern.DOTALL);
    private static final List<String> LUNCH_LABELS = List.of("Tiền ăn giữa ca", "Hỗ trợ ăn trưa");
    private static final List<ExtraAllowance> EXTRA_ALLOWANCES = List.of(
            new ExtraAllowance("housingAllowance", "Hỗ trợ thuê nhà"),
            new ExtraAllowance("phoneAllowance", "Hỗ trợ điện thoại"),
            new ExtraAllowance("fuelAllowance", "Hỗ trợ xăng xe"),
            new ExtraAllowance("supplementAllowance", "Phụ cấp bù lương"),
            new ExtraAllowance("responsibilityAllowance", "Phụ cấp trách nhiệm"));

    private AllowanceRows() {
    }

    public static String addAfterLunchRow(String body) {
        Matcher lunchRow = LUNCH_ROW.matcher(body);
        if (!lunchRow.find()) {
            return body;
        }
        StringBuilder rows = new StringBuilder(lunchRow.group());
        for (ExtraAllowance extra : EXTRA_ALLOWANCES) {
            rows.append(copyOf(lunchRow.group(), extra));
        }
        return body.substring(0, lunchRow.start()) + rows + body.substring(lunchRow.end());
    }

    public static String withoutEmptyRows(String body, Function<String, String> valueOf) {
        Matcher conditional = CONDITIONAL_ROW.matcher(body);
        StringBuilder kept = new StringBuilder();
        while (conditional.find()) {
            String value = valueOf.apply(conditional.group(1));
            String replacement = value == null || value.isBlank() ? "" : withoutCondition(conditional.group());
            conditional.appendReplacement(kept, Matcher.quoteReplacement(replacement));
        }
        conditional.appendTail(kept);
        return kept.toString();
    }

    private static String withoutCondition(String row) {
        return row.replaceFirst(" " + CONDITION_ATTRIBUTE + "=\"\\w+\"", "");
    }

    private static String copyOf(String lunchRow, ExtraAllowance extra) {
        String row = lunchRow.replaceFirst("<tr", "<tr " + CONDITION_ATTRIBUTE + "=\"" + extra.key() + "Words\"");
        for (String label : LUNCH_LABELS) {
            row = row.replace(label, extra.label());
        }
        return row.replace("{{lunchAllowanceWords}}", "{{" + extra.key() + "Words}}").replace("{{lunchAllowance}}", "{{" + extra.key() + "}}");
    }

    private record ExtraAllowance(String key, String label) {
    }
}
