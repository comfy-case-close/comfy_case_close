package com.comfy.caseclose.controller;

import com.comfy.caseclose.dto.request.CreateUserRequest;
import com.comfy.caseclose.dto.request.UpdateUserRequest;
import com.comfy.caseclose.dto.response.PagedResponse;
import com.comfy.caseclose.dto.response.UserResponseDTO;
import com.comfy.caseclose.service.UserService;
import com.comfy.caseclose.utils.I18nMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<PagedResponse<UserResponseDTO>> findUsers(
        @RequestParam(value = "pageNumber", defaultValue = "0")
        @Min(value = 0, message = I18nMessage.WRONG_PAGE_NUMBER) int pageNumber,
        @RequestParam(value = "pageSize", defaultValue = "10")
        @Min(value = 1, message = I18nMessage.WRONG_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(userService.findUsers(pageNumber, pageSize));
    }

    @PostMapping()
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }
}
