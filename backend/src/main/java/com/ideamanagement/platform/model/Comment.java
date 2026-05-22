package com.ideamanagement.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idea_id", nullable = false)
    private Long ideaId;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private String userName;
}

// Threaded comment entity structure.
// Threaded comment entity structure.
// Threaded comment entity structure.
// Threaded comment entity structure.