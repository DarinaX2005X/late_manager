package com.example.lateMng.repository;

import com.example.lateMng.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    @Query(value = "SELECT * FROM departments ORDER BY id ASC", nativeQuery = true)
    List<Department> findAllByOrderByIdAsc();

    @Query(value = "SELECT EXISTS(SELECT 1 FROM departments WHERE name = :name)", nativeQuery = true)
    boolean existsByName(@Param("name") String name);
}
