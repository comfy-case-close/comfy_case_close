package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.CreateUserRequest;
import com.comfy.caseclose.dto.request.UpdateUserRequest;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.dto.response.UserResponseDTO;
import com.comfy.caseclose.entity.Branch;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.entity.UserBranch;
import com.comfy.caseclose.exception.BadRequestException;
import com.comfy.caseclose.exception.ConflictException;
import com.comfy.caseclose.exception.ResourceNotFoundException;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.UserBranchRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.SecurityUtils;
import com.comfy.caseclose.service.UserService;
import com.comfy.caseclose.utils.InputNormalizer;
import com.comfy.caseclose.utils.PaginationUtils;
import com.comfy.caseclose.utils.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserBranchRepository userBranchRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponseDTO> findUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("employeeCode"));
        return PaginationUtils.toPagedResponse(userRepository.findAll(pageable), this::toResponseDTO);
    }

    @Override
    @Transactional
    public UserResponseDTO createUser(CreateUserRequest request) {
        String employeeCode = InputNormalizer.employeeCode(request.getEmployeeCode());
        String email = InputNormalizer.email(request.getEmail());

        if (userRepository.findByEmployeeCodeIgnoreCase(employeeCode).isPresent()) {
            throw new ConflictException("Employee code '" + employeeCode + "' is already in use");
        }

        if (email != null && userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("Email '" + email + "' is already in use");
        }

        User user = new User();
        user.setEmployeeCode(employeeCode);
        user.setFullName(InputNormalizer.name(request.getFullName()));
        user.setPasscodeHash(passwordEncoder.encode(request.getPasscode()));
        user.setRole(UserRole.valueOf(request.getRole().strip().toUpperCase()));
        user.setPosition(InputNormalizer.name(request.getPosition()));
        user.setEmail(email);
        user.setIsActive(request.getIsActive() == null || request.getIsActive());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        assignBranches(user, request.getBranchIds());
        return toResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));

        String email = InputNormalizer.email(request.getEmail());

        // If email changed, check uniqueness against other users.
        if (email != null && !email.equalsIgnoreCase(user.getEmail())) {
            if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
                throw new ConflictException("Email '" + email + "' is already in use");
            }
        }

        user.setFullName(InputNormalizer.name(request.getFullName()));
        user.setRole(UserRole.valueOf(request.getRole().strip().toUpperCase()));
        user.setPosition(InputNormalizer.name(request.getPosition()));
        user.setEmail(email);
        user.setIsActive(request.getIsActive() == null || request.getIsActive());
        if (request.getPasscode() != null && !request.getPasscode().isBlank()) {
            user.setPasscodeHash(passwordEncoder.encode(request.getPasscode()));
        }
        user.setUpdatedAt(OffsetDateTime.now());

        userBranchRepository.deleteByUserId(id);
        assignBranches(user, request.getBranchIds());
        return toResponseDTO(user);
    }

    @Override
    @Transactional
    public void deactivateUser(Long id) {
        if (id.equals(SecurityUtils.currentUserId())) {
            throw new BadRequestException("You cannot deactivate your own account");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
        user.setIsActive(false);
        user.setUpdatedAt(OffsetDateTime.now());
    }

    private void assignBranches(User user, List<Long> branchIds) {
        if (branchIds == null) {
            return;
        }
        for (Long branchId : branchIds) {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id " + branchId));
            UserBranch userBranch = new UserBranch();
            userBranch.setUser(user);
            userBranch.setBranch(branch);
            userBranchRepository.save(userBranch);
        }
    }

    private UserResponseDTO toResponseDTO(User user) {
        List<Long> branchIds = userBranchRepository.findBranchIdsByUserId(user.getId());
        List<String> branchCodes = branchIds.isEmpty()
                ? List.of()
                : branchRepository.findAllById(branchIds).stream().map(Branch::getBranchCode).toList();

        return UserResponseDTO.builder()
                .id(user.getId())
                .employeeCode(user.getEmployeeCode())
                .fullName(user.getFullName())
                .role(user.getRole())
                .position(user.getPosition())
                .email(user.getEmail())
                .isActive(user.getIsActive())
                .branchIds(branchIds)
                .branchCodes(branchCodes)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
