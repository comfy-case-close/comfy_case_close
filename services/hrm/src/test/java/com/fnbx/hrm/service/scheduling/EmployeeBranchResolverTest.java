package com.fnbx.hrm.service.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fnbx.hrm.entity.BranchSetting;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.enums.EmploymentType;
import com.fnbx.hrm.repository.BranchSettingRepository;
import com.fnbx.hrm.entity.StaffSchedulingProfile;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.StaffSchedulingProfileRepository;
import com.fnbx.hrm.service.scheduling.EmployeeBranchResolver.EmployeeBranch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeBranchResolverTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 12);

    private final EmploymentAssignmentRepository assignments = mock(EmploymentAssignmentRepository.class);
    private final BranchSettingRepository settings = mock(BranchSettingRepository.class);
    private final StaffSchedulingProfileRepository profiles = mock(StaffSchedulingProfileRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final EmployeeBranchResolver resolver = new EmployeeBranchResolver(assignments, settings, profiles, entityManager);

    private final UUID staffId = UUID.randomUUID();
    private final UUID pool = UUID.randomUUID();
    private final UUID thuDuc = UUID.randomUUID();
    private final UUID tuXuong = UUID.randomUUID();

    @Test
    void defaultSchedulingBranchWinsOverTheContract() {
        contract(pool, EmploymentType.PARTTIME);
        StaffSchedulingProfile profile = new StaffSchedulingProfile();
        profile.setHomeBranchId(tuXuong);
        when(profiles.findById(staffId)).thenReturn(Optional.of(profile));

        EmployeeBranch branch = resolver.resolve(staffId, WEEK);

        assertEquals(tuXuong, branch.branchId());
        assertEquals(List.of(tuXuong), branch.branchIds());
    }

    @Test
    void contractBranchIsUsedWhenItIsASellingStore() {
        contract(thuDuc, EmploymentType.PARTTIME);
        when(settings.findActiveSharedPoolBranchIds()).thenReturn(List.of(pool));

        EmployeeBranch branch = resolver.resolve(staffId, WEEK);

        assertEquals(thuDuc, branch.branchId());
        assertEquals(List.of(thuDuc), branch.branchIds());
    }

    @Test
    void sharedPoolContractRegistersAtTheAssignedSellingStoresInDisplayOrder() {
        contract(pool, EmploymentType.FULLTIME);
        when(settings.findActiveSharedPoolBranchIds()).thenReturn(List.of(pool));
        assignedTo(List.of(tuXuong, pool, thuDuc));
        when(settings.findAllById(any())).thenReturn(List.of(setting(tuXuong, 3), setting(thuDuc, 2)));

        EmployeeBranch branch = resolver.resolve(staffId, WEEK);

        assertEquals(thuDuc, branch.branchId());
        assertEquals(List.of(thuDuc, tuXuong), branch.branchIds());
        assertEquals(EmploymentType.FULLTIME, branch.employmentType());
    }

    @Test
    void sharedPoolContractWithoutAnyAssignmentKeepsTheContractBranch() {
        contract(pool, EmploymentType.PARTTIME);
        when(settings.findActiveSharedPoolBranchIds()).thenReturn(List.of(pool));
        assignedTo(List.of());
        when(settings.findAllById(any())).thenReturn(List.of());

        assertEquals(List.of(pool), resolver.resolve(staffId, WEEK).branchIds());
    }

    private void contract(UUID defaultBranch, EmploymentType type) {
        EmploymentAssignment contract = new EmploymentAssignment();
        contract.setDefaultBranchId(defaultBranch);
        contract.setEmploymentType(type);
        contract.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        when(assignments.findEffectiveForStaff(any(), any())).thenReturn(List.of(contract));
    }

    @SuppressWarnings("unchecked")
    private void assignedTo(List<UUID> branches) {
        TypedQuery<UUID> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), any(Class.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(branches);
    }

    private static BranchSetting setting(UUID branchId, int displayOrder) {
        BranchSetting setting = new BranchSetting();
        setting.setBranchId(branchId);
        setting.setSellingStore(true);
        setting.setDisplayOrder((short) displayOrder);
        return setting;
    }
}
