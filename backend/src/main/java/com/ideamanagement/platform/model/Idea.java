package com.ideamanagement.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "ideas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String tag;

    @Column(nullable = false)
    private String status; // 'SUBMITTED', 'UNDER_REVIEW', 'PLANNED', 'IMPLEMENTED', 'REJECTED'

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "upvotes_count", nullable = false)
    private int upvotesCount;

    @Column(name = "downvotes_count", nullable = false)
    private int downvotesCount;

    @Column(name = "hot_score", nullable = false)
    private double hotScore;

    @Transient
    private String userName;
}

// Auditing timestamp variables declared.
// Auditing timestamp variables declared.
// Auditing timestamp variables declared.