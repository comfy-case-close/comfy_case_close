package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.request.LoginRequest;
import com.comfy.caseclose.dto.response.LoginResponseDTO;

public interface AuthService {

    LoginResponseDTO login(LoginRequest request);
}
