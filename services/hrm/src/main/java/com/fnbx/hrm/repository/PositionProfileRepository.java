package com.fnbx.hrm.repository;

import com.fnbx.hrm.dto.response.PositionResponse;
import com.fnbx.hrm.entity.PositionProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PositionProfileRepository extends JpaRepository<PositionProfile, UUID> {

    @Query("""
           SELECT new com.fnbx.hrm.dto.response.PositionResponse(
               sp.positionId, sp.positionCode, sp.positionName, sp.active,
               pp.departmentId, d.departmentName, coalesce(pp.trainee, false))
           FROM StaffPosition sp
           LEFT JOIN PositionProfile pp ON pp.positionId = sp.positionId
           LEFT JOIN Department d ON d.departmentId = pp.departmentId
           ORDER BY sp.positionName
           """)
    List<PositionResponse> findAllWithProfile();
}
