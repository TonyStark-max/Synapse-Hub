package com.ideamanagement.platform.repository;

import com.ideamanagement.platform.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByIdeaIdAndOrgId(Long ideaId, String orgId);
}
