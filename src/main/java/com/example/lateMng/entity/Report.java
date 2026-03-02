package com.example.lateMng.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_full_name", length = 50)
    private String userFullName;

    @Column(name = "department_id")
    private Integer departmentId;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType;

    @Column(length = 200)
    private String reason;

    @Column(name = "time_val", length = 50)
    private String timeVal;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_manual", nullable = false)
    @Builder.Default
    private Boolean isManual = Boolean.FALSE;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_by_name", length = 50)
    private String createdByName;
}
