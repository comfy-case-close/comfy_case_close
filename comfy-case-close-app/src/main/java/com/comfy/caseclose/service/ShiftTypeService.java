package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.response.ShiftTypeResponseDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ShiftTypeService {

    ShiftTypeResponseDTO getShiftTypeById(Long id);

    PagedResponse<ShiftTypeResponseDTO> listShiftTypes(Pageable pageable);

    List<ShiftTypeResponseDTO> listActiveShiftTypes();

    ShiftTypeResponseDTO getShiftTypeByCode(String shiftTypeCode);
}
