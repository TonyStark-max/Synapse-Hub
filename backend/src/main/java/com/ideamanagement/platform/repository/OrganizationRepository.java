package com.ideamanagement.platform.repository;

import com.ideamanagement.platform.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {
    Optional<Organization> findByInviteCode(String inviteCode);
}
