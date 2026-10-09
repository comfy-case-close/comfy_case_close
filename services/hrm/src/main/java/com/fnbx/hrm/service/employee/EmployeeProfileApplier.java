package com.fnbx.hrm.service.employee;

import com.fnbx.hrm.dto.request.EmployeeProfileInput;
import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.BankRepository;
import com.fnbx.hrm.repository.ProvinceRepository;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmployeeProfileApplier {

    private final BankRepository bankRepository;
    private final ProvinceRepository provinceRepository;

    public void apply(EmployeeProfile profile, EmployeeProfileInput input) {
        requireKnownBank(input.bankCode());
        requireKnownProvince(input.addressProvinceCode());
        setIfPresent(input.dateOfBirth(), profile::setDateOfBirth);
        setIfPresent(input.gender(), profile::setGender);
        setIfPresent(input.maritalStatus(), profile::setMaritalStatus);
        setIfPresent(input.childrenCount(), profile::setChildrenCount);
        setIfPresent(input.ethnicity(), profile::setEthnicity);
        setIfPresent(input.religion(), profile::setReligion);
        setIfPresent(input.nationality(), profile::setNationality);
        setIfPresent(input.nationalIdNo(), profile::setNationalIdNo);
        setIfPresent(input.nationalIdIssuedOn(), profile::setNationalIdIssuedOn);
        setIfPresent(input.nationalIdIssuedPlace(), profile::setNationalIdIssuedPlace);
        setIfPresent(input.socialInsuranceNo(), profile::setSocialInsuranceNo);
        setIfPresent(input.taxCode(), profile::setTaxCode);
        setIfPresent(input.educationLevel(), profile::setEducationLevel);
        setIfPresent(input.educationSchool(), profile::setEducationSchool);
        setIfPresent(input.educationMajor(), profile::setEducationMajor);
        setIfPresent(input.graduationYear(), profile::setGraduationYear);
        setIfPresent(input.educationGrade(), profile::setEducationGrade);
        setIfPresent(input.personalEmail(), profile::setPersonalEmail);
        setIfPresent(input.addressStreet(), profile::setAddressStreet);
        setIfPresent(input.addressWard(), profile::setAddressWard);
        setIfPresent(input.addressProvinceCode(), profile::setAddressProvinceCode);
        setIfPresent(input.recruitmentSource(), profile::setRecruitmentSource);
        setIfPresent(input.bankCode(), profile::setBankCode);
        setIfPresent(input.bankAccountNo(), profile::setBankAccountNo);
        setIfPresent(input.bankAccountName(), profile::setBankAccountName);
        setIfPresent(input.hiredOn(), profile::setHiredOn);
        setIfPresent(input.note(), profile::setNote);
    }

    private <T> void setIfPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void requireKnownBank(String bankCode) {
        if (bankCode != null && bankRepository.findByBankCodeAndActiveTrue(bankCode).isEmpty()) {
            throw PayrollExceptions.invalidField("Unknown bank code " + bankCode);
        }
    }

    private void requireKnownProvince(String provinceCode) {
        if (provinceCode != null && !provinceRepository.existsById(provinceCode)) {
            throw PayrollExceptions.invalidField("Unknown province code " + provinceCode);
        }
    }
}
