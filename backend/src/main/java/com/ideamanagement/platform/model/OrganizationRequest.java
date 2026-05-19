package com.ideamanagement.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "organization_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, unique = true)
    private String orgId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    @Column(name = "requester_name")
    private String requesterName;

    @Column(nullable = false)
    private String status; // 'PENDING', 'APPROVED', 'REJECTED'

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
