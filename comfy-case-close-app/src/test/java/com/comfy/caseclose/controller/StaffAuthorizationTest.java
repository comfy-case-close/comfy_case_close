package com.comfy.caseclose.controller;

import com.comfy.caseclose.service.AttachmentService;
import com.comfy.caseclose.service.CashCloseService;
import com.comfy.caseclose.utils.enums.AttachmentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StaffAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class SecurityConfiguration {}

    private AnnotationConfigApplicationContext context;
    private CashCloseService cashCloseService;
    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        cashCloseService = mock(CashCloseService.class);
        attachmentService = mock(AttachmentService.class);
        context = new AnnotationConfigApplicationContext();
        context.register(SecurityConfiguration.class);
        context.registerBean(CashCloseController.class, () -> new CashCloseController(cashCloseService));
        context.registerBean(AttachmentController.class, () -> new AttachmentController(attachmentService));
        context.refresh();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "MANAGER", "ADMIN"})
    void cashCloseRolesCanLoadCarryForwardAndUpload(String role) {
        authenticate(role);
        LocalDate date = LocalDate.of(2026, 9, 21);
        var file = new MockMultipartFile("file", new byte[]{1});
        context.getBean(CashCloseController.class).getCarryForward(1L, date, 2L);
        context.getBean(AttachmentController.class).uploadAttachment(file, AttachmentType.POS_RECEIPT, 1L);
        verify(cashCloseService).getCarryForward(1L, date, 2L);
        verify(attachmentService).uploadAttachment(file, AttachmentType.POS_RECEIPT, 1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCOUNTANT", "SHIFT_LEAD"})
    void otherRolesCannotLoadCarryForwardOrUpload(String role) {
        authenticate(role);
        assertThatThrownBy(() -> context.getBean(CashCloseController.class)
                .getCarryForward(1L, LocalDate.of(2026, 9, 21), 2L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> context.getBean(AttachmentController.class)
                .uploadAttachment(new MockMultipartFile("file", new byte[]{1}), AttachmentType.POS_RECEIPT, 1L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cashCloseService, attachmentService);
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-user", null,
                        AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }
}
