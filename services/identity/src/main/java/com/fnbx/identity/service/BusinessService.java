package com.fnbx.identity.service;

import java.util.UUID;
import com.fnbx.identity.dto.request.UpdateBusinessRequest;
import com.fnbx.identity.dto.response.BusinessResponse;
import com.fnbx.identity.dto.response.MessageResponse;

/** Management of existing tenants; creation requires registration approval. */
public interface BusinessService {
    MessageResponse deactivate(UUID businessId);
    BusinessResponse current();
    BusinessResponse update(UpdateBusinessRequest request);
}
