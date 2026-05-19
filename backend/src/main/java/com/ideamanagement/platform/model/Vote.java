package com.ideamanagement.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "votes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"idea_id", "user_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idea_id", nullable = false)
    private Long ideaId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "vote_type", nullable = false)
    private String voteType; // 'UP', 'DOWN'

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
