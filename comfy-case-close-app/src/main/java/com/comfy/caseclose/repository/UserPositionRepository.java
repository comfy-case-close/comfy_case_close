package com.comfy.caseclose.repository;

import com.comfy.caseclose.entity.UserPosition;
import com.comfy.caseclose.entity.UserPositionId;
import com.comfy.caseclose.utils.enums.StaffPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPositionRepository extends JpaRepository<UserPosition, UserPositionId> {

    @Query("""
        SELECT up.staffPosition.code FROM UserPosition up
        WHERE up.user.id = :userId
        ORDER BY up.staffPosition.displayTitle
        """)
    List<StaffPosition> findPositionCodesByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT up.staffPosition.displayTitle FROM UserPosition up
        WHERE up.user.id = :userId
        ORDER BY up.staffPosition.displayTitle
        """)
    List<String> findPositionDisplayTitlesByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserPosition up WHERE up.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
