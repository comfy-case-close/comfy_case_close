package com.fnbx.hrm.service.contractimport;

import com.fnbx.hrm.entity.Bank;
import com.fnbx.hrm.entity.Province;
import com.fnbx.hrm.repository.BankRepository;
import com.fnbx.hrm.repository.ProvinceRepository;
import com.fnbx.identity.entity.Branch;
import com.fnbx.identity.entity.Staff;
import com.fnbx.identity.entity.StaffPosition;
import jakarta.persistence.EntityManager;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the codes and names typed in the workbook to the records they mean. */
@Component
@RequiredArgsConstructor
public class ImportLookups {

    private final EntityManager entityManager;
    private final BankRepository bankRepository;
    private final ProvinceRepository provinceRepository;

    public Optional<Staff> staffByEmployeeCode(String code) {
        return entityManager.createQuery("SELECT s FROM Staff s WHERE upper(s.employeeCode) = :code", Staff.class)
                .setParameter("code", code.trim().toUpperCase(Locale.ROOT)).getResultStream().findFirst();
    }

    public Optional<StaffPosition> positionByCode(String code) {
        return entityManager.createQuery("SELECT p FROM StaffPosition p WHERE upper(p.positionCode) = :code AND p.active = true", StaffPosition.class)
                .setParameter("code", code.trim().toUpperCase(Locale.ROOT)).getResultStream().findFirst();
    }

    public Optional<Branch> branchByCode(String code) {
        return entityManager.createQuery("SELECT b FROM Branch b WHERE upper(b.branchCode) = :code AND b.active = true", Branch.class)
                .setParameter("code", code.trim().toUpperCase(Locale.ROOT)).getResultStream().findFirst();
    }

    public Optional<Bank> bankByText(String text) {
        String wanted = normalize(text);
        return bankRepository.findByActiveTrueOrderByShortName().stream()
                .filter(bank -> normalize(bank.getBankCode()).equals(wanted)
                        || normalize(bank.getShortName()).equals(wanted)
                        || normalize(bank.getBankName()).equals(wanted))
                .findFirst();
    }

    public Optional<Province> provinceByText(String text) {
        String wanted = normalize(text);
        return provinceRepository.findAll().stream()
                .filter(province -> normalize(province.getProvinceName()).equals(wanted)
                        || normalize(province.getProvinceCode()).equals(wanted))
                .findFirst();
    }

    private String normalize(String text) {
        String ascii = Normalizer.normalize(text.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
