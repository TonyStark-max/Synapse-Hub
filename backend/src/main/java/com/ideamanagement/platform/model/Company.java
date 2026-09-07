package com.ideamanagement.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "companies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
