package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.LatePenaltyRuleRequest;
import com.fnbx.hrm.dto.response.LatePenaltyRuleResponse;
import java.util.List;

public interface LatePenaltyRuleService {

    List<LatePenaltyRuleResponse> list();

    /** Creates a new effective version; existing versions are never edited in place. */
    LatePenaltyRuleResponse create(LatePenaltyRuleRequest request);
}
