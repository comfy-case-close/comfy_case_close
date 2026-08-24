package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.response.BranchResponseDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.service.BranchService;
import com.comfy.caseclose.utils.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;

    @Override
    @Transactional(readOnly = true)
    public BranchResponseDTO getBranchById(Long id) {
        return branchRepository.findById(id)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<BranchResponseDTO> listBranches(Pageable pageable) {
        return PaginationUtils.toPagedResponse(
                branchRepository.findAll(pageable),
                this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponseDTO getBranchByCode(String branchCode) {
        return branchRepository.findByBranchCode(branchCode)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with code " + branchCode));
    }

    private BranchResponseDTO toResponseDTO(Branch branch) {
        return BranchResponseDTO.builder()
                .id(branch.getId())
                .branchCode(branch.getBranchCode())
                .branchName(branch.getBranchName())
                .isActive(branch.getIsActive())
                .targetCashRemaining(branch.getTargetCashRemaining())
                .cashRemainingTolerance(branch.getCashRemainingTolerance())
                .managerEmail(null)  // Branch does not have managerEmail field in current entity
                .build();
    }
}
