package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.InsuranceSchemeRequest;
import com.fnbx.hrm.dto.response.InsuranceSchemeResponse;
import java.util.List;

public interface InsuranceSchemeService {

    List<InsuranceSchemeResponse> list();

    /** Creates a new effective version; a statutory rate change never restates a past period. */
    InsuranceSchemeResponse create(InsuranceSchemeRequest request);
}
