package com.ideamanagement.platform.repository;

import com.ideamanagement.platform.model.WorkspaceJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceJoinRequestRepository extends JpaRepository<WorkspaceJoinRequest, Long> {
    List<WorkspaceJoinRequest> findByOrgIdAndStatus(String orgId, String status);
    Optional<WorkspaceJoinRequest> findByOrgIdAndUserId(String orgId, String userId);
}
