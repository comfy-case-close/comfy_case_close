package com.comfy.caseclose.service;

import com.comfy.caseclose.dto.request.CreateUserRequest;
import com.comfy.caseclose.dto.request.UpdateUserRequest;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.dto.response.UserResponseDTO;

public interface UserService {

    PagedResponse<UserResponseDTO> findUsers(int page, int size);

    UserResponseDTO createUser(CreateUserRequest request);

    UserResponseDTO updateUser(Long id, UpdateUserRequest request);

    void deactivateUser(Long id);
}
