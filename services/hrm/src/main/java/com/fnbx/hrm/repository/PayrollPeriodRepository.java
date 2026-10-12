package com.fnbx.hrm.repository;

import com.fnbx.hrm.entity.PayrollPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {

    Optional<PayrollPeriod> findByPeriodYearAndPeriodMonth(short periodYear, short periodMonth);

    boolean existsByPeriodYearAndPeriodMonth(short periodYear, short periodMonth);

    List<PayrollPeriod> findAllByOrderByPeriodYearDescPeriodMonthDesc();

    Optional<PayrollPeriod> findFirstByEndDateLessThanOrderByEndDateDesc(LocalDate date);

    @Query("""
           SELECT p FROM PayrollPeriod p
           WHERE (:year IS NULL OR p.periodYear = :year)
             AND (:status IS NULL OR cast(p.status as string) = :status)
           ORDER BY p.periodYear DESC, p.periodMonth DESC
           """)
    List<PayrollPeriod> search(@Param("year") Short year, @Param("status") String status);

    @Query("""
           SELECT p FROM PayrollPeriod p
           WHERE p.startDate <= :to AND p.endDate >= :from
           ORDER BY p.startDate
           """)
    List<PayrollPeriod> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT p FROM PayrollPeriod p
           WHERE (p.periodYear * 100 + p.periodMonth) BETWEEN :fromKey AND :toKey
           ORDER BY p.periodYear, p.periodMonth
           """)
    List<PayrollPeriod> findByMonthRange(@Param("fromKey") int fromKey, @Param("toKey") int toKey);
}
