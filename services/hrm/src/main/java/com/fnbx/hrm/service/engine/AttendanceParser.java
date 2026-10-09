package com.fnbx.hrm.service.engine;

import com.fnbx.hrm.entity.AttendanceCode;
import com.fnbx.hrm.entity.LatePenaltyRule;
import com.fnbx.hrm.enums.EmploymentType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns one timesheet cell's raw text into hours (spec section 5.3, formula
 * reference section 4). Pure: no repository access, so every branch is a unit
 * test against a literal string.
 *
 * <p>{@code allowanceHours} is deliberately computed before any late penalty -
 * allowances are prorated by days attended, not by hours paid, so a late
 * arrival must not lose that day's allowance twice (formula reference 4.3).
 */
@Component
public class AttendanceParser {

    private static final Pattern LATE_CODE = Pattern.compile("^T([123])-([0-9]+(?:[.,][0-9]+)?)$");

    public record Result(BigDecimal declaredHours, BigDecimal paidHours, BigDecimal allowanceHours,
                          UUID attendanceCodeId, UUID lateRuleId, boolean invalid) {

        static Result empty() {
            return new Result(null, BigDecimal.ZERO, BigDecimal.ZERO, null, null, false);
        }

        static Result invalid(BigDecimal declaredHours) {
            return new Result(declaredHours, BigDecimal.ZERO, BigDecimal.ZERO, null, null, true);
        }
    }

    public Result parse(String rawValue, EmploymentType employmentType, BigDecimal standardHoursPerDay,
            Map<String, AttendanceCode> attendanceCodesByCode, Map<String, LatePenaltyRule> lateRulesByCode) {
        String value = rawValue == null ? "" : rawValue.strip();
        if (value.isEmpty()) {
            return Result.empty();
        }

        BigDecimal numeric = parseNumber(value);
        if (numeric != null) {
            if (numeric.signum() < 0) {
                return Result.invalid(numeric);
            }
            return new Result(numeric, numeric, numeric.min(standardHoursPerDay), null, null, false);
        }

        if (value.equals("KL") || value.equals("CP")) {
            if (employmentType == EmploymentType.PARTTIME) {
                return Result.invalid(null);
            }
            AttendanceCode code = attendanceCodesByCode.get(value);
            UUID codeId = code == null ? null : code.getAttendanceCodeId();
            return value.equals("CP")
                    ? new Result(standardHoursPerDay, standardHoursPerDay, standardHoursPerDay, codeId, null, false)
                    : new Result(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, codeId, null, false);
        }

        Matcher lateMatch = LATE_CODE.matcher(value);
        if (lateMatch.matches()) {
            String tier = "T" + lateMatch.group(1);
            BigDecimal declaredHours = parseNumber(lateMatch.group(2));
            LatePenaltyRule rule = lateRulesByCode.get(tier);
            if (rule == null || declaredHours == null) {
                return Result.invalid(declaredHours);
            }
            BigDecimal paidHours = applyLatePenalty(declaredHours, rule);
            BigDecimal allowanceHours = declaredHours.min(standardHoursPerDay);
            return new Result(declaredHours, paidHours, allowanceHours, null, rule.getLatePenaltyRuleId(), false);
        }

        return Result.invalid(null);
    }

    private BigDecimal applyLatePenalty(BigDecimal declaredHours, LatePenaltyRule rule) {
        if (rule.isVoidsShift()) {
            return BigDecimal.ZERO;
        }
        if (rule.getDeductHours() != null) {
            return declaredHours.subtract(rule.getDeductHours()).max(BigDecimal.ZERO);
        }
        if (rule.getDeductRatio() != null) {
            return declaredHours.multiply(BigDecimal.ONE.subtract(rule.getDeductRatio()));
        }
        return declaredHours;
    }

    private BigDecimal parseNumber(String value) {
        try {
            return new BigDecimal(value.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
