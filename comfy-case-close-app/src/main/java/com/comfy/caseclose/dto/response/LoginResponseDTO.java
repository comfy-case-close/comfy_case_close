package com.comfy.caseclose.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LoginResponseDTO {

    private String token;
    private String tokenType;
    private Instant expiresAt;
    private AuthUserDTO user;
}
