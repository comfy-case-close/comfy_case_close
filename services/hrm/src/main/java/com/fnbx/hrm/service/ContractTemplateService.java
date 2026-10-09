package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.CreateContractTemplateRequest;
import com.fnbx.hrm.dto.request.SetTemplateActiveRequest;
import com.fnbx.hrm.dto.response.ContractTemplateResponse;
import com.fnbx.hrm.dto.response.PlaceholderResponse;
import java.util.List;
import java.util.UUID;

public interface ContractTemplateService {

    List<ContractTemplateResponse> list();

    List<PlaceholderResponse> placeholders();

    ContractTemplateResponse create(CreateContractTemplateRequest request);

    ContractTemplateResponse setActive(UUID templateId, SetTemplateActiveRequest request);

    /** Renders the template with sample values so its author can check the layout. */
    byte[] preview(UUID templateId);
}
