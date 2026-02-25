package com.example.lateMng.repository;

import com.example.lateMng.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    @Query("SELECT d FROM Department d ORDER BY d.id ASC")
    List<Department> findAllByOrderByIdAsc();

    boolean existsByName(String name);
}
