package com.example.lateMng.repository;

import com.example.lateMng.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("SELECT r FROM Report r WHERE r.createdAt >= :from AND r.createdAt < :to ORDER BY r.createdAt DESC")
    List<Report> findByPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT r FROM Report r WHERE r.departmentId = :deptId AND r.createdAt >= :from AND r.createdAt < :to ORDER BY r.createdAt DESC")
    List<Report> findByDepartmentAndPeriod(@Param("deptId") Integer deptId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT r FROM Report r WHERE r.userId = :userId AND r.createdAt >= :from AND r.createdAt < :to ORDER BY r.createdAt DESC")
    List<Report> findByUserAndPeriod(@Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT DISTINCT r.userId FROM Report r WHERE r.createdAt >= :from AND r.createdAt < :to")
    List<Long> findReportedUserIdsByPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
