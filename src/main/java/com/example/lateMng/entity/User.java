package com.example.lateMng.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String username;

    @Column(name = "full_name", length = 50)
    private String fullName;

    @Column(length = 20)
    @Builder.Default
    private String role = "new";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "is_on_vacation", nullable = false)
    @Builder.Default
    private Boolean isOnVacation = Boolean.FALSE;

    @Column(length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "is_admin", nullable = false)
    @Builder.Default
    private Boolean isAdmin = Boolean.FALSE;

    @Column(name = "is_supervisor", nullable = false)
    @Builder.Default
    private Boolean isSupervisor = Boolean.FALSE;

    public boolean isActive() {
        return "active".equals(status);
    }

    public boolean isPending() {
        return "pending".equals(status);
    }

    public boolean isRemoved() {
        return "removed".equals(status);
    }
}
