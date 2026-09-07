package com.ideamanagement.platform.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_join_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceJoinRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orgId;
    private String userId;
    private String email;
    private String name;

    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
