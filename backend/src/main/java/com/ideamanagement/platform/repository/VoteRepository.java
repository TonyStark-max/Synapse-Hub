package com.ideamanagement.platform.repository;

import com.ideamanagement.platform.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findByIdeaIdAndUserIdAndOrgId(Long ideaId, String userId, String orgId);
    List<Vote> findByIdeaIdAndOrgId(Long ideaId, String orgId);
}
