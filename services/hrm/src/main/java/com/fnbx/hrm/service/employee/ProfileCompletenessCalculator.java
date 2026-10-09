package com.fnbx.hrm.service.employee;

import com.fnbx.hrm.entity.EmployeeProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class ProfileCompletenessCalculator {

    private static final Map<String, Function<EmployeeProfile, Object>> TRACKED_FIELDS = trackedFields();

    public ProfileCompleteness calculate(EmployeeProfile profile) {
        List<String> missing = TRACKED_FIELDS.entrySet().stream()
                .filter(field -> isBlank(field.getValue().apply(profile)))
                .map(Map.Entry::getKey)
                .toList();
        int filled = TRACKED_FIELDS.size() - missing.size();
        return new ProfileCompleteness(filled * 100 / TRACKED_FIELDS.size(), missing);
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }

    private static Map<String, Function<EmployeeProfile, Object>> trackedFields() {
        Map<String, Function<EmployeeProfile, Object>> fields = new LinkedHashMap<>();
        fields.put("dateOfBirth", EmployeeProfile::getDateOfBirth);
        fields.put("gender", EmployeeProfile::getGender);
        fields.put("maritalStatus", EmployeeProfile::getMaritalStatus);
        fields.put("nationalIdNo", EmployeeProfile::getNationalIdNo);
        fields.put("nationalIdIssuedOn", EmployeeProfile::getNationalIdIssuedOn);
        fields.put("nationalIdIssuedPlace", EmployeeProfile::getNationalIdIssuedPlace);
        fields.put("taxCode", EmployeeProfile::getTaxCode);
        fields.put("socialInsuranceNo", EmployeeProfile::getSocialInsuranceNo);
        fields.put("educationLevel", EmployeeProfile::getEducationLevel);
        fields.put("personalEmail", EmployeeProfile::getPersonalEmail);
        fields.put("addressStreet", EmployeeProfile::getAddressStreet);
        fields.put("addressWard", EmployeeProfile::getAddressWard);
        fields.put("addressProvinceCode", EmployeeProfile::getAddressProvinceCode);
        fields.put("bankCode", EmployeeProfile::getBankCode);
        fields.put("bankAccountNo", EmployeeProfile::getBankAccountNo);
        fields.put("bankAccountName", EmployeeProfile::getBankAccountName);
        fields.put("hiredOn", EmployeeProfile::getHiredOn);
        fields.put("recruitmentSource", EmployeeProfile::getRecruitmentSource);
        return fields;
    }
}
