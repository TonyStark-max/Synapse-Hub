package com.ideamanagement.platform.repository;

import com.ideamanagement.platform.model.OrganizationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRequestRepository extends JpaRepository<OrganizationRequest, Long> {
    List<OrganizationRequest> findByStatus(String status);
    Optional<OrganizationRequest> findByOrgId(String orgId);
    List<OrganizationRequest> findByRequesterId(String requesterId);
}
