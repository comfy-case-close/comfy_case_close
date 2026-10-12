package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.Bank;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, String> {

    List<Bank> findByActiveTrueOrderByShortName();

    Optional<Bank> findByBankCodeAndActiveTrue(String bankCode);
}
