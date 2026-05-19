package com.ideamanagement.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    @Column(nullable = false)
    private String email;

    private String name;

    @Column(nullable = false)
    private String role; // 'ADMIN', 'MEMBER'

    @Column(name = "org_id")
    private String orgId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}

// Entity model imports optimized.
// Entity model imports optimized.
// Entity model imports optimized.