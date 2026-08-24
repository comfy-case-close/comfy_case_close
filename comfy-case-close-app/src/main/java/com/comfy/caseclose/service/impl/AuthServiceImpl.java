package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.dto.request.LoginRequest;
import com.comfy.caseclose.dto.response.AuthUserDTO;
import com.comfy.caseclose.dto.response.LoginResponseDTO;
import com.comfy.caseclose.entity.User;
import com.comfy.caseclose.repository.BranchRepository;
import com.comfy.caseclose.repository.UserBranchRepository;
import com.comfy.caseclose.repository.UserRepository;
import com.comfy.caseclose.security.CustomUserDetails;
import com.comfy.caseclose.security.jwt.JwtTokenProvider;
import com.comfy.caseclose.service.AuthService;
import com.comfy.caseclose.utils.InputNormalizer;
import com.comfy.caseclose.utils.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final UserBranchRepository userBranchRepository;
    private final BranchRepository branchRepository;

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequest request) {
        String employeeCode = normalizeEmployeeCode(request.getEmployeeCode());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(employeeCode, request.getPasscode()));
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished: " + employeeCode));
        user.setLastLoginAt(Instant.now().atOffset(ZoneOffset.UTC));

        Instant issuedAt = Instant.now();
        return LoginResponseDTO.builder()
                .token(tokenProvider.generateToken(principal))
                .tokenType(TOKEN_TYPE)
                .expiresAt(tokenProvider.expiryOf(issuedAt))
                .user(toAuthUser(user))
                .build();
    }

    private AuthUserDTO toAuthUser(User user) {
        return AuthUserDTO.builder()
                .id(user.getId())
                .employeeCode(user.getEmployeeCode())
                .fullName(user.getFullName())
                .role(user.getRole())
                .position(user.getPosition())
                .isActive(user.getIsActive())
                .branchNames(accessibleBranchNames(user))
                .build();
    }

    private List<String> accessibleBranchNames(User user) {
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.ACCOUNTANT) {
            return branchRepository.findActiveBranchNames();
        }
        return userBranchRepository.findBranchNamesByUserId(user.getId());
    }

    private String normalizeEmployeeCode(String employeeCode) {
        return InputNormalizer.employeeCode(employeeCode);
    }
}
