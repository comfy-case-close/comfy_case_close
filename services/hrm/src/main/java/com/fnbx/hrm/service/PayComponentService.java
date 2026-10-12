package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.PayComponentUpdateRequest;
import com.fnbx.hrm.dto.response.PayComponentResponse;
import java.util.List;
import java.util.UUID;

public interface PayComponentService {

    List<PayComponentResponse> list();

    PayComponentResponse update(UUID payComponentId, PayComponentUpdateRequest request);
}
