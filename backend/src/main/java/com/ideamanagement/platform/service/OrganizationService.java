package com.ideamanagement.platform.service;

import com.ideamanagement.platform.model.Organization;
import com.ideamanagement.platform.model.OrganizationRequest;
import com.ideamanagement.platform.model.User;
import com.ideamanagement.platform.repository.OrganizationRepository;
import com.ideamanagement.platform.repository.OrganizationRequestRepository;
import com.ideamanagement.platform.repository.UserRepository;
import com.ideamanagement.platform.security.DBSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationRequestRepository organizationRequestRepository;
    private final DBSecurityContext dbSecurityContext;

    public OrganizationService(OrganizationRepository organizationRepository, 
                               UserRepository userRepository, 
                               OrganizationRequestRepository organizationRequestRepository,
                               DBSecurityContext dbSecurityContext) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.organizationRequestRepository = organizationRequestRepository;
        this.dbSecurityContext = dbSecurityContext;
    }

    @Transactional
    public Organization createOrganization(String orgId, String name, String userId, String email, String userName) {
        // Generate a clean, readable 8-character invite code
        String inviteCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 1. Explicitly set context in Postgres session for RLS prior to saving
        dbSecurityContext.setContext(orgId, userId);

        Organization org = Organization.builder()
                .id(orgId)
                .name(name)
                .inviteCode(inviteCode)
                .build();
        
        Organization savedOrg = organizationRepository.save(org);

        // 2. Register/update user as the ADMIN of the new organization
        User user = userRepository.findById(userId)
                .orElse(User.builder().id(userId).build());
        user.setEmail(email);
        user.setName(userName);
        user.setOrgId(orgId);
        user.setRole("ADMIN"); // The creator is the admin
        
        userRepository.save(user);

        return savedOrg;
    }

    @Transactional
    public Organization joinOrganization(String inviteCode, String userId, String email, String userName) {
        // Find organization by invite code
        Organization org = organizationRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));

        // 1. Explicitly set context in Postgres session for RLS prior to updating user
        dbSecurityContext.setContext(org.getId(), userId);

        User user = userRepository.findById(userId)
                .orElse(User.builder().id(userId).build());
        user.setEmail(email);
        user.setName(userName);
        user.setOrgId(org.getId());
        user.setRole("MEMBER"); // Standard role is MEMBER
        
        userRepository.save(user);

        return org;
    }

    public Organization getOrganization(String orgId) {
        return organizationRepository.findById(orgId).orElse(null);
    }

    public Organization findByInviteCode(String inviteCode) {
        return organizationRepository.findByInviteCode(inviteCode).orElse(null);
    }

    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    @Transactional
    public OrganizationRequest createOrgRequest(String orgId, String name, String description, String userId, String email, String userName) {
        dbSecurityContext.setContext("", userId);
        if (organizationRepository.existsById(orgId) || organizationRequestRepository.findByOrgId(orgId).isPresent()) {
            throw new IllegalArgumentException("Organization ID is already taken");
        }
        OrganizationRequest req = OrganizationRequest.builder()
                .orgId(orgId)
                .name(name)
                .description(description)
                .requesterId(userId)
                .requesterEmail(email)
                .requesterName(userName)
                .status("PENDING")
                .build();
        return organizationRequestRepository.save(req);
    }

    @Transactional
    public Organization approveOrgRequest(Long requestId, String approverId) {
        dbSecurityContext.setContext("", approverId);
        OrganizationRequest req = organizationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalArgumentException("Request is already processed");
        }
        
        // Create the actual organization. Requester becomes the ADMIN of this org.
        Organization org = createOrganization(req.getOrgId(), req.getName(), req.getRequesterId(), req.getRequesterEmail(), req.getRequesterName());
        
        // Re-set context to the approver's context to save the request status change
        dbSecurityContext.setContext("", approverId);
        req.setStatus("APPROVED");
        organizationRequestRepository.save(req);
        
        return org;
    }

    @Transactional
    public void rejectOrgRequest(Long requestId, String approverId) {
        dbSecurityContext.setContext("", approverId);
        OrganizationRequest req = organizationRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalArgumentException("Request is already processed");
        }
        req.setStatus("REJECTED");
        organizationRequestRepository.save(req);
    }

    @Transactional(readOnly = true)
    public List<OrganizationRequest> getPendingRequests(String userId) {
        dbSecurityContext.setContext("", userId);
        return organizationRequestRepository.findByStatus("PENDING");
    }

    @Transactional
    public User getOrCreateUser(String userId, String email, String name) {
        return userRepository.findById(userId)
                .orElseGet(() -> {
                    String role = "lostg826@gmail.com".equals(email) ? "ADMIN" : "MEMBER";
                    User newUser = User.builder()
                            .id(userId)
                            .email(email != null ? email : "")
                            .name(name != null ? name : "User")
                            .role(role)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    @Transactional
    public void leaveWorkspace(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setOrgId(null);
        userRepository.save(user);
    }
}
