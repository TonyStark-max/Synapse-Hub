package com.ideamanagement.platform.repository;

import com.ideamanagement.platform.model.Idea;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IdeaRepository extends JpaRepository<Idea, Long> {
    List<Idea> findByOrgId(String orgId, Sort sort);
    Optional<Idea> findByIdAndOrgId(Long id, String orgId);
}
