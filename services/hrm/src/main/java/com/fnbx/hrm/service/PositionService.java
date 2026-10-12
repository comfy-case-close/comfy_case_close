package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.PositionProfileRequest;
import com.fnbx.hrm.dto.response.PositionResponse;
import java.util.List;
import java.util.UUID;

public interface PositionService {

    List<PositionResponse> list();

    PositionResponse setProfile(UUID positionId, PositionProfileRequest request);
}
