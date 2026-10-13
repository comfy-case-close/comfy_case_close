package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.CreateContractTemplateRequest;
import com.fnbx.hrm.dto.request.ImportContractTemplateRequest;
import com.fnbx.hrm.dto.request.SetTemplateActiveRequest;
import com.fnbx.hrm.dto.response.ContractTemplateResponse;
import com.fnbx.hrm.dto.response.PlaceholderResponse;
import com.fnbx.hrm.dto.response.StandardTemplateInstallResponse;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public interface ContractTemplateService {

    List<ContractTemplateResponse> list();

    List<PlaceholderResponse> placeholders();

    ContractTemplateResponse create(CreateContractTemplateRequest request);

    ContractTemplateResponse importDocx(ImportContractTemplateRequest request, String fileName, InputStream docx);

    ContractTemplateResponse setActive(UUID templateId, SetTemplateActiveRequest request);

    byte[] preview(UUID templateId);

    List<StandardTemplateInstallResponse> installStandard();
}
