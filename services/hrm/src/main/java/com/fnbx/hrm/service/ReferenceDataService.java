package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.response.BankResponse;
import com.fnbx.hrm.dto.response.ProvinceResponse;
import java.util.List;

/** Lookup lists shared by every business, which the HR forms choose from. */
public interface ReferenceDataService {

    List<BankResponse> banks();

    List<ProvinceResponse> provinces();
}
