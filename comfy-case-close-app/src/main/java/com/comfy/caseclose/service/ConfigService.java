package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.request.UpdateConfigRequest;

import java.util.Map;

public interface ConfigService {

    /** Current reconciliation thresholds, feature flags and bill-repayment details. */
    Map<String, Object> getConfig();

    /** ADMIN only — persists new values, effective immediately for every subsequent request. */
    Map<String, Object> updateConfig(UpdateConfigRequest request);
}
