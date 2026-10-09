package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.BranchSetting;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BranchSettingRepository extends JpaRepository<BranchSetting, UUID> {

    @Query("""
           SELECT bs.branchId FROM BranchSetting bs, Branch b
           WHERE bs.branchId = b.branchId AND bs.sellingStore = true AND b.active = true
           """)
    List<UUID> findActiveSellingStoreBranchIds();

    @Query("""
           SELECT bs.branchId FROM BranchSetting bs, Branch b
           WHERE bs.branchId = b.branchId AND bs.sharedPool = true AND b.active = true
           """)
    List<UUID> findActiveSharedPoolBranchIds();
}
