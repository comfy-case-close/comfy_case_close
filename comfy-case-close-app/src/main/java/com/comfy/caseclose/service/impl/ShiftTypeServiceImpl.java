package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.response.ShiftTypeResponseDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.entity.ShiftType;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.ShiftTypeRepository;
import com.comfy.caseclose.service.ShiftTypeService;
import com.comfy.caseclose.utils.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShiftTypeServiceImpl implements ShiftTypeService {

    private final ShiftTypeRepository shiftTypeRepository;

    @Override
    @Transactional(readOnly = true)
    public ShiftTypeResponseDTO getShiftTypeById(Long id) {
        return shiftTypeRepository.findById(id)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Shift type not found with id " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ShiftTypeResponseDTO> listShiftTypes(Pageable pageable) {
        return PaginationUtils.toPagedResponse(
                shiftTypeRepository.findAll(pageable),
                this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftTypeResponseDTO> listActiveShiftTypes() {
        return shiftTypeRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftTypeResponseDTO getShiftTypeByCode(String shiftTypeCode) {
        return shiftTypeRepository.findByShiftTypeCode(shiftTypeCode)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Shift type not found with code " + shiftTypeCode));
    }

    private ShiftTypeResponseDTO toResponseDTO(ShiftType shiftType) {
        return ShiftTypeResponseDTO.builder()
                .id(shiftType.getId())
                .shiftTypeCode(shiftType.getShiftTypeCode())
                .shiftTypeName(shiftType.getShiftName())
                .startTime(shiftType.getSuggestedStartTime() != null ? (long) shiftType.getSuggestedStartTime().toSecondOfDay() : null)
                .endTime(shiftType.getSuggestedEndTime() != null ? (long) shiftType.getSuggestedEndTime().toSecondOfDay() : null)
                .branchId(null)  // ShiftType does not have a branch relationship in the current entity
                .isActive(shiftType.getIsActive())
                .build();
    }
}
