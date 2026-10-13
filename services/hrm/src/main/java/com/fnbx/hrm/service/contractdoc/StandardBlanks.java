package com.fnbx.hrm.service.contractdoc;

import com.fnbx.hrm.enums.ContractTemplateKind;
import java.util.List;
import java.util.function.UnaryOperator;

public final class StandardBlanks {

    private static final List<String[]> PHRASES = List.of(
            new String[] {"Từ ngày [ngày hiệu lực HĐLĐ] đến hết ngày [ngày kết thúc HĐLĐ]", "{{contractPeriod}}"},
            new String[] {"xác định thời hạn 12 (mười hai) tháng", "{{contractTermType}}"},
            new String[] {"60 (sáu mươi) ngày", "{{probationDays}} ngày"},
            new String[] {"5.600.000 đồng/tháng", "{{baseSalary}}/tháng"},
            new String[] {"Năm triệu sáu trăm nghìn đồng", "{{baseSalaryWords}}"});

    private static final List<String[]> BLANKS = List.of(
            new String[] {"[Số hợp đồng]", "{{contractNo}}"},
            new String[] {"[ngày ký]", "{{signDate}}"},
            new String[] {"[Ông/Bà]", "{{honorific}}"},
            new String[] {"[HỌ VÀ TÊN]", "{{fullNameUpper}}"},
            new String[] {"[Ngày sinh]", "{{dateOfBirth}}"},
            new String[] {"[Giới tính]", "{{genderText}}"},
            new String[] {"[Số CCCD]", "{{nationalIdNo}}"},
            new String[] {"[Ngày cấp]", "{{nationalIdIssuedOn}}"},
            new String[] {"[Nơi cấp]", "{{nationalIdIssuedPlace}}"},
            new String[] {"[Địa chỉ thường trú]", "{{address}}"},
            new String[] {"[Nơi ở hiện tại]", "{{address}}"},
            new String[] {"[Số điện thoại]", "{{phone}}"},
            new String[] {"[Tên cửa hàng]", "{{branch}}"},
            new String[] {"[Tên nơi làm việc]", "{{branch}}"},
            new String[] {"[Địa chỉ cửa hàng]", "{{branchAddress}}"},
            new String[] {"[Địa chỉ nơi làm việc]", "{{branchAddress}}"},
            new String[] {"[Tiền ăn giữa ca]", "{{lunchAllowance}}"},
            new String[] {"[Hỗ trợ ăn trưa]", "{{lunchAllowance}}"},
            new String[] {"[bằng chữ]", "{{lunchAllowanceWords}}"},
            new String[] {"[Tổng thu nhập cố định]", "{{fixedTotal}}"},
            new String[] {"[Tổng thu nhập]", "{{fixedTotal}}"},
            new String[] {"[ngày bắt đầu thử việc]", "{{effectiveFrom}}"},
            new String[] {"[ngày kết thúc thử việc]", "{{effectiveTo}}"});

    private static final String LABOR_CONTRACT_START_BLANK = "[ngày hiệu lực HĐLĐ]";

    private StandardBlanks() {
    }

    public static UnaryOperator<String> rewriterFor(ContractTemplateKind kind) {
        String laborContractStart = kind == ContractTemplateKind.PROBATION_AGREEMENT ? "{{laborContractStart}}" : "{{effectiveFrom}}";
        return text -> {
            String rewritten = replaceAll(text, PHRASES);
            rewritten = rewritten.replace(LABOR_CONTRACT_START_BLANK, laborContractStart);
            return replaceAll(rewritten, BLANKS);
        };
    }

    private static String replaceAll(String text, List<String[]> replacements) {
        String result = text;
        for (String[] replacement : replacements) {
            result = result.replace(replacement[0], replacement[1]);
        }
        return result;
    }
}
