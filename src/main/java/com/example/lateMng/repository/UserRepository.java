package com.example.lateMng.repository;

import com.example.lateMng.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT * FROM users WHERE status = :status ORDER BY full_name ASC", nativeQuery = true)
    List<User> findByStatusOrderByFullNameAsc(@Param("status") String status);

    @Query(value = "SELECT * FROM users WHERE status = :status AND department_id IS NULL ORDER BY full_name ASC", nativeQuery = true)
    List<User> findByStatusAndDepartmentIsNullOrderByFullNameAsc(@Param("status") String status);

    @Query(value = "SELECT * FROM users WHERE status = :status AND department_id = :departmentId ORDER BY full_name ASC", nativeQuery = true)
    List<User> findByStatusAndDepartment_IdOrderByFullNameAsc(@Param("status") String status, @Param("departmentId") Integer departmentId);

    @Query(value = "SELECT * FROM users WHERE role = 'manager' AND department_id = :deptId AND is_on_vacation = FALSE AND status = 'active'", nativeQuery = true)
    List<User> findManagersByDepartment(@Param("deptId") Integer deptId);

    @Query(value = "SELECT * FROM users WHERE is_supervisor = TRUE AND status = 'active'", nativeQuery = true)
    List<User> findSupervisors();

    @Query(value = "SELECT * FROM users WHERE is_supervisor = TRUE AND status = 'active' AND is_on_vacation = FALSE", nativeQuery = true)
    List<User> findSupervisorsExcludingOnVacation();

    @Query(value = "SELECT * FROM users WHERE is_admin = TRUE AND status = 'active'", nativeQuery = true)
    List<User> findAdmins();

    @Query(value = "SELECT * FROM users WHERE user_id = :userId", nativeQuery = true)
    Optional<User> findByIdWithDepartment(@Param("userId") Long userId);
}
