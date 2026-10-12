package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.ManualPayItemRequest;
import com.fnbx.hrm.dto.response.ManualPayItemResponse;
import java.util.List;
import java.util.UUID;

public interface ManualPayItemService {

    List<ManualPayItemResponse> list(UUID lineId);

    ManualPayItemResponse create(UUID lineId, ManualPayItemRequest request);

    ManualPayItemResponse update(UUID itemId, ManualPayItemRequest request);

    void delete(UUID itemId);
}
