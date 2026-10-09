package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.response.BankResponse;
import com.fnbx.hrm.dto.response.ProvinceResponse;
import com.fnbx.hrm.repository.BankRepository;
import com.fnbx.hrm.repository.ProvinceRepository;
import com.fnbx.hrm.service.ReferenceDataService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferenceDataServiceImpl implements ReferenceDataService {

    private final BankRepository bankRepository;
    private final ProvinceRepository provinceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BankResponse> banks() {
        return bankRepository.findByActiveTrueOrderByShortName().stream()
                .map(bank -> new BankResponse(bank.getBankCode(), bank.getBankName(), bank.getShortName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvinceResponse> provinces() {
        return provinceRepository.findAllByOrderByProvinceName().stream()
                .map(province -> new ProvinceResponse(province.getProvinceCode(), province.getProvinceName()))
                .toList();
    }
}
