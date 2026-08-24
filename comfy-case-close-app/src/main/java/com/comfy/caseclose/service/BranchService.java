package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.response.BranchResponseDTO;
import com.comfy.caseclose.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

public interface BranchService {

    /**
     * Get a branch by ID.
     */
    BranchResponseDTO getBranchById(Long id);

    /**
     * List all accessible branches.
     */
    PagedResponse<BranchResponseDTO> listBranches(Pageable pageable);

    /**
     * Get a branch by code.
     */
    BranchResponseDTO getBranchByCode(String branchCode);
}
