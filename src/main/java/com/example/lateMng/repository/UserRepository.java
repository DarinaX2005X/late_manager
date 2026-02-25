package com.example.lateMng.repository;

import com.example.lateMng.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.status = :status ORDER BY u.fullName ASC")
    List<User> findByStatusOrderByFullNameAsc(@Param("status") String status);

    @Query("SELECT u FROM User u WHERE u.status = :status AND u.department IS NULL ORDER BY u.fullName ASC")
    List<User> findByStatusAndDepartmentIsNullOrderByFullNameAsc(@Param("status") String status);

    @Query("SELECT u FROM User u WHERE u.status = :status AND u.department.id = :departmentId ORDER BY u.fullName ASC")
    List<User> findByStatusAndDepartment_IdOrderByFullNameAsc(@Param("status") String status,
            @Param("departmentId") Integer departmentId);

    @Query("SELECT u FROM User u WHERE u.role = 'manager' AND u.department.id = :deptId AND u.isOnVacation = FALSE AND u.status = 'active'")
    List<User> findManagersByDepartment(@Param("deptId") Integer deptId);

    @Query("SELECT u FROM User u WHERE u.isSupervisor = TRUE AND u.status = 'active'")
    List<User> findSupervisors();

    @Query("SELECT u FROM User u WHERE u.isSupervisor = TRUE AND u.status = 'active' AND u.isOnVacation = FALSE")
    List<User> findSupervisorsExcludingOnVacation();

    @Query("SELECT u FROM User u WHERE u.isAdmin = TRUE AND u.status = 'active'")
    List<User> findAdmins();

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.department WHERE u.userId = :userId")
    Optional<User> findByIdWithDepartment(@Param("userId") Long userId);

    List<User> findAllByDepartmentId(Integer departmentId);
}
