package com.example.lateMng.service;

import com.example.lateMng.entity.Department;
import com.example.lateMng.entity.User;
import com.example.lateMng.repository.DepartmentRepository;
import com.example.lateMng.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addOrUpdateUser(Long userId, String username, String fullName) {
        userRepository.findById(userId)
                .map(u -> {
                    u.setUsername(username);
                    u.setFullName(fullName);
                    return userRepository.save(u);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .userId(userId)
                        .username(username)
                        .fullName(fullName)
                        .role("new")
                        .status("pending")
                        .build()));
    }

    public Optional<User> getUser(Long userId) {
        return userRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserWithDepartment(Long userId) {
        return userRepository.findByIdWithDepartment(userId);
    }

    public List<User> getPendingUsers() {
        return userRepository.findByStatusOrderByFullNameAsc("pending");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUserRoleDept(Long userId, String role, Integer deptId) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setRole(role);
            u.setStatus("active");
            u.setDepartment(deptId != null ? departmentRepository.findById(deptId).orElse(null) : null);
            userRepository.save(u);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void toggleVacation(Long userId, boolean isVacation) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setIsOnVacation(isVacation);
            userRepository.save(u);
        });
    }

    public List<User> getManagersForDepartment(Integer departmentId) {
        return userRepository.findManagersByDepartment(departmentId);
    }

    public Optional<Department> getDepartment(Integer id) {
        return departmentRepository.findById(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createDepartment(String name) {
        if (departmentRepository.existsByName(name)) return false;
        departmentRepository.save(Department.builder().name(name).build());
        return true;
    }

    public List<Department> getDepartments() {
        return departmentRepository.findAllByOrderByIdAsc();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renameDepartment(Integer deptId, String newName) {
        if (departmentRepository.existsByName(newName)) return false;
        departmentRepository.findById(deptId).ifPresent(d -> {
            d.setName(newName);
            departmentRepository.save(d);
        });
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteDepartment(Integer deptId) {
        for (User u : userRepository.findByStatusAndDepartment_IdOrderByFullNameAsc("active", deptId)) {
            u.setDepartment(null);
            userRepository.save(u);
        }
        departmentRepository.deleteById(deptId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteUser(Long userId) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setStatus("removed");
            u.setDepartment(null);
            userRepository.save(u);
        });
    }

    public List<User> getEmployeesInDepartment(Integer departmentId) {
        return userRepository.findByStatusAndDepartment_IdOrderByFullNameAsc("active", departmentId);
    }

    public List<User> getUsersWithoutDepartment() {
        return userRepository.findByStatusAndDepartmentIsNullOrderByFullNameAsc("active");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUserName(Long userId, String fullName) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setFullName(fullName);
            userRepository.save(u);
        });
    }

    public List<User> getAllActiveUsers() {
        return userRepository.findByStatusOrderByFullNameAsc("active");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUserRole(Long userId, String role) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setRole(role);
            userRepository.save(u);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setAdmin(Long userId, boolean isAdmin) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setIsAdmin(isAdmin);
            userRepository.save(u);
        });
    }

    public List<User> getAdmins() {
        return userRepository.findAdmins();
    }

    public List<User> getSupervisors(boolean excludeOnVacation) {
        return excludeOnVacation ? userRepository.findSupervisorsExcludingOnVacation() : userRepository.findSupervisors();
    }

    public Set<Long> getReportRecipientIds(Long excludeUserId, Integer departmentId) {
        Set<Long> ids = new HashSet<>();
        if (departmentId != null) {
            for (User m : getManagersForDepartment(departmentId)) {
                ids.add(m.getUserId());
            }
        }
        for (User s : getSupervisors(true)) {
            ids.add(s.getUserId());
        }
        ids.remove(excludeUserId);
        return ids;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setSupervisor(Long userId, boolean isSupervisor) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setIsSupervisor(isSupervisor);
            userRepository.save(u);
        });
    }
}