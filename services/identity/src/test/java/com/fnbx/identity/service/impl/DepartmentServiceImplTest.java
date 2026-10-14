package com.fnbx.identity.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import com.fnbx.identity.dto.request.DepartmentRequest;
import com.fnbx.identity.dto.response.DepartmentResponse;
import com.fnbx.identity.entity.DepartmentProfile;
import com.fnbx.identity.repository.DepartmentRepository;
import com.fnbx.identity.service.TenantTransactions;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;

/**
 * identity owns the writes of identity.department: DEPARTMENT_MANAGE changes it, the HR permissions only read it,
 * and the code is stored the way the table's CHECK demands (upper case, trimmed).
 */
class DepartmentServiceImplTest {

    private final UUID business = UUID.randomUUID();
    private final DepartmentRepository repository = mock(DepartmentRepository.class);
    private final BranchAccessGuard permissions = mock(BranchAccessGuard.class);
    private final TenantTransactions transactions = mock(TenantTransactions.class);
    private final DepartmentServiceImpl service = new DepartmentServiceImpl(repository, permissions, transactions);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void signIn() {
        TenantContext.set(TenantContext.of(business, UUID.randomUUID()));
        when(transactions.inCurrentTenant(any())).thenAnswer(call -> ((Supplier<Object>) call.getArgument(0)).get());
    }

    @AfterEach
    void signOut() {
        TenantContext.clear();
    }

    private DepartmentProfile stored(UUID id, String code, String name, int order, boolean active) {
        return new DepartmentProfile(id, business, code, name, order, active);
    }

    private static DepartmentRequest request(String code, String name) {
        return new DepartmentRequest(code, name, null, null);
    }

    @Test
    void createStoresTheCodeUpperCaseAndTrimmed() {
        UUID id = UUID.randomUUID();
        when(repository.insert(eq(business), eq("STORE_OPS"), eq("Store Operations"), eq(0), eq(true))).thenReturn(id);
        when(repository.find(id)).thenReturn(Optional.of(stored(id, "STORE_OPS", "Store Operations", 0, true)));

        DepartmentResponse created = service.create(request("  store_ops ", "  Store Operations "));

        assertEquals("STORE_OPS", created.departmentCode());
        verify(permissions).requireBusiness(Permission.DEPARTMENT_MANAGE);
    }

    @Test
    void createKeepsTheOrderAndActiveFlagItWasGiven() {
        UUID id = UUID.randomUUID();
        when(repository.insert(eq(business), anyString(), anyString(), eq(7), eq(false))).thenReturn(id);
        when(repository.find(id)).thenReturn(Optional.of(stored(id, "PRODUCT", "Product", 7, false)));

        DepartmentResponse created = service.create(new DepartmentRequest("Product", "Product", 7, false));

        assertEquals(7, created.displayOrder());
        assertEquals(false, created.active());
    }

    @Test
    void aCodeAlreadyUsedInTheBusinessIsRefusedWithAConflict() {
        when(repository.insert(any(), anyString(), anyString(), anyInt(), anyBoolean()))
                .thenThrow(new DuplicateKeyException("department_business_id_department_code_key"));

        AppException refused = assertThrows(AppException.class, () -> service.create(request("product", "Product")));

        assertEquals(ErrorCode.DEPARTMENT_CODE_TAKEN, refused.getErrorCode());
    }

    @Test
    void updateStoresTheCodeUpperCase() {
        UUID id = UUID.randomUUID();
        when(repository.find(id)).thenReturn(Optional.of(stored(id, "OLD", "Old", 0, true)));

        service.update(id, request("Product", "Product"));

        verify(repository).update(id, "PRODUCT", "Product", 0, true);
    }

    @Test
    void updatingADepartmentThatDoesNotExistIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.find(id)).thenReturn(Optional.empty());

        AppException missing = assertThrows(AppException.class, () -> service.update(id, request("A", "A")));

        assertEquals(ErrorCode.DEPARTMENT_NOT_FOUND, missing.getErrorCode());
        verify(repository, never()).update(any(), anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void deactivateSwitchesTheDepartmentOff() {
        UUID id = UUID.randomUUID();
        when(repository.find(id)).thenReturn(Optional.of(stored(id, "PRODUCT", "Product", 0, true)));

        service.deactivate(id);

        verify(repository).deactivate(id);
    }

    @Test
    void writingNeedsDepartmentManage() {
        doThrow(new AccessDeniedException("denied")).when(permissions).requireBusiness(Permission.DEPARTMENT_MANAGE);
        UUID id = UUID.randomUUID();

        assertThrows(AccessDeniedException.class, () -> service.create(request("A", "A")));
        assertThrows(AccessDeniedException.class, () -> service.update(id, request("A", "A")));
        assertThrows(AccessDeniedException.class, () -> service.deactivate(id));

        verify(repository, never()).insert(any(), anyString(), anyString(), anyInt(), anyBoolean());
        verify(repository, never()).deactivate(any());
    }

    @Test
    void anyHrOrPayrollSetUpPermissionMayRead() {
        UUID id = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(stored(id, "PRODUCT", "Product", 1, true)));

        for (Permission reader : List.of(Permission.DEPARTMENT_MANAGE, Permission.HR_RECORD_READ,
                Permission.PAYROLL_PROCESS, Permission.PAYROLL_CONFIG_WRITE)) {
            when(permissions.effective(null)).thenReturn(Set.of(reader));
            assertEquals(1, service.list().size(), reader + " should be able to read");
        }
    }

    @Test
    void aPermissionThatHasNothingToDoWithHrCannotRead() {
        when(permissions.effective(null)).thenReturn(Set.of(Permission.BRANCH_CREATE));

        assertThrows(AccessDeniedException.class, service::list);
        assertTrue(permissions.effective(null).contains(Permission.BRANCH_CREATE));
    }
}
