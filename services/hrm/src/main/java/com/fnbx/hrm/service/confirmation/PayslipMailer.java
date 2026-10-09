package com.fnbx.hrm.service.confirmation;

import com.fnbx.hrm.config.PayslipConfirmationProperties;
import com.fnbx.hrm.dto.response.PayslipComponentTotalResponse;
import com.fnbx.hrm.entity.PayrollConfig;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.Payslip;
import com.fnbx.hrm.entity.PayslipEmailLog;
import com.fnbx.hrm.enums.EmailStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.PayrollConfigRepository;
import com.fnbx.hrm.repository.PayrollLineItemRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.repository.PayslipEmailLogRepository;
import com.fnbx.identity.entity.Staff;
import com.fnbx.mail.EmailService;
import com.fnbx.mail.MailDeliveryException;
import jakarta.persistence.EntityManager;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayslipMailer {

    private static final String DEFAULT_SUBJECT_TEMPLATE = "Phiếu lương {{period}}";
    private static final String DEFAULT_BODY_TEMPLATE = "Xin chào {{name}}, thực lĩnh tháng {{period}} của bạn là {{net}}.";
    private static final String EARNING = "EARNING";
    private static final String DEDUCTION = "DEDUCTION";

    private final PayrollPeriodRepository periodRepository;
    private final PayrollConfigRepository configRepository;
    private final PayrollLineItemRepository itemRepository;
    private final PayslipEmailLogRepository emailLogRepository;
    private final PayslipConfirmationProperties properties;
    private final EmailService emailService;
    private final EntityManager entityManager;

    /** Sends the payslip with a confirm link built from {@code token}, and always leaves an email log row. */
    public void deliver(Payslip payslip, String token) {
        Staff staff = entityManager.find(Staff.class, payslip.getStaffId());
        PayrollPeriod period = periodRepository.findById(payslip.getPeriodId()).orElseThrow(PayrollExceptions::periodNotFound);
        PayrollConfig config = configRepository.findById(period.getConfigId()).orElseThrow(PayrollExceptions::resourceNotFound);
        String confirmUrl = properties.publicUrl() + "/" + token;
        String periodLabel = "%02d/%d".formatted(period.getPeriodMonth(), period.getPeriodYear());
        String name = staff.getFirstName() + " " + staff.getLastName();
        String net = formatMoney(payslip.getNetTotal());
        Map<String, String> values = Map.of("period", periodLabel, "name", name, "net", net);
        String subject = render(config.getPayslipEmailSubjectTemplate(), DEFAULT_SUBJECT_TEMPLATE, values);
        String body = render(config.getPayslipEmailBodyTemplate(), DEFAULT_BODY_TEMPLATE, values);

        PayslipEmailLog log = newLog(payslip, staff.getEmail(), subject, body);
        send(log, subject, body + "\n\nXác nhận đã nhận lương: " + confirmUrl,
                templateVariables(payslip, name, periodLabel, confirmUrl));
        emailLogRepository.save(log);
    }

    private void send(PayslipEmailLog log, String subject, String text, Map<String, Object> variables) {
        String recipient = log.getRecipientEmail();
        if (recipient == null || recipient.isBlank()) {
            fail(log, "Employee has no email on file");
        } else if (!emailService.available()) {
            fail(log, "Mail delivery is not configured");
        } else {
            try {
                emailService.sendTemplate(recipient, subject, text, "email/payslip", variables);
                log.setStatus(EmailStatus.SENT);
                log.setSentAt(Instant.now());
            } catch (MailDeliveryException ex) {
                fail(log, ex.getMessage());
            }
        }
    }

    private void fail(PayslipEmailLog log, String message) {
        log.setStatus(EmailStatus.FAILED);
        log.setErrorMessage(message);
    }

    private PayslipEmailLog newLog(Payslip payslip, String recipient, String subject, String body) {
        PayslipEmailLog log = new PayslipEmailLog();
        log.setPayslipEmailLogId(UUID.randomUUID());
        log.setBusinessId(payslip.getBusinessId());
        log.setPayslipId(payslip.getPayslipId());
        log.setRecipientEmail(recipient);
        log.setSubject(subject);
        log.setBody(body);
        return log;
    }

    private Map<String, Object> templateVariables(Payslip payslip, String name, String periodLabel, String confirmUrl) {
        List<PayslipComponentTotalResponse> components =
                itemRepository.sumComponentsForEmployee(payslip.getPeriodId(), payslip.getStaffId());
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", name);
        variables.put("period", periodLabel);
        variables.put("earnings", linesOf(components, EARNING));
        variables.put("deductions", deductionLines(components, payslip));
        variables.put("gross", formatMoney(payslip.getGrossTotal()));
        variables.put("net", formatMoney(payslip.getNetTotal()));
        variables.put("lateShifts", (int) payslip.getLateShiftTotal());
        variables.put("confirmUrl", confirmUrl);
        return variables;
    }

    private List<Map<String, String>> linesOf(List<PayslipComponentTotalResponse> components, String type) {
        return components.stream()
                .filter(component -> type.equals(component.getComponentType()))
                .map(component -> Map.of("name", component.getComponentName(), "amount", formatMoney(component.getAmount())))
                .toList();
    }

    private List<Map<String, String>> deductionLines(List<PayslipComponentTotalResponse> components, Payslip payslip) {
        List<Map<String, String>> lines = new java.util.ArrayList<>(linesOf(components, DEDUCTION));
        if (payslip.getEmployeeInsuranceTotal().signum() > 0) {
            lines.add(Map.of("name", "Bảo hiểm", "amount", formatMoney(payslip.getEmployeeInsuranceTotal())));
        }
        return lines;
    }

    private String formatMoney(java.math.BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.GERMANY).format(amount) + " đ";
    }

    private String render(String template, String defaultTemplate, Map<String, String> values) {
        String rendered = template == null || template.isBlank() ? defaultTemplate : template;
        for (Map.Entry<String, String> value : values.entrySet()) {
            rendered = rendered.replace("{{" + value.getKey() + "}}", value.getValue());
        }
        return rendered;
    }
}
