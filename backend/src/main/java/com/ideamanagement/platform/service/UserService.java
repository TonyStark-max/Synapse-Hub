package com.ideamanagement.platform.service;

import com.ideamanagement.platform.model.Organization;
import com.ideamanagement.platform.model.User;
import com.ideamanagement.platform.repository.OrganizationRepository;
import com.ideamanagement.platform.repository.UserRepository;
import com.ideamanagement.platform.security.DBSecurityContext;
import com.ideamanagement.platform.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final DBSecurityContext dbSecurityContext;

    public UserService(UserRepository userRepository, OrganizationRepository organizationRepository, DBSecurityContext dbSecurityContext) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.dbSecurityContext = dbSecurityContext;
    }

    @Transactional
    public User syncUser(String userId, String email, String name, String orgId, String orgRole) {
        if (userId == null) {
            return null;
        }

        // Set local context for RLS in this transactional method in Postgres
        dbSecurityContext.setContext(orgId, userId);

        // Lazily create the organization if it doesn't exist yet
        if (orgId != null && !orgId.trim().isEmpty()) {
            if (!organizationRepository.existsById(orgId)) {
                String inviteCode = "INV-" + orgId.toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                Organization org = Organization.builder()
                        .id(orgId)
                        .name("Workspace " + orgId)
                        .inviteCode(inviteCode)
                        .build();
                organizationRepository.save(org);
            }
        }

        Optional<User> existingUserOpt = userRepository.findById(userId);
        User user;
        String mappedRole = "org:admin".equals(orgRole) || "ADMIN".equals(orgRole) ? "ADMIN" : "MEMBER";

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            boolean changed = false;
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
                changed = true;
            }
            if (name != null && !name.equals(user.getName())) {
                user.setName(name);
                changed = true;
            }
            // Prevent changing organizations once a user has joined one (Enforces strict Option 1)
            if (orgId != null && user.getOrgId() == null) {
                user.setOrgId(orgId);
                changed = true;
            } else if (orgId != null && user.getOrgId() != null && !orgId.equals(user.getOrgId())) {
                throw new SecurityException("User is already assigned to a different organization and cannot switch.");
            }
            // Do not overwrite user role from syncUser as it's managed internally now
            if (changed) {
                user = userRepository.save(user);
            }
        } else {
            user = User.builder()
                    .id(userId)
                    .email(email != null ? email : "")
                    .name(name)
                    .orgId(orgId)
                    .role(mappedRole)
                    .build();
            user = userRepository.save(user);
        }

        return user;
    }

    public User getUser(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    @Transactional
    public User setCompany(String userId, String companyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getCompanyId() != null) {
            throw new IllegalStateException("User already belongs to a company");
        }
        user.setCompanyId(companyId);
        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(String userId, String name, String profilePicUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (name != null && !name.trim().isEmpty()) {
            user.setName(name);
        }
        if (profilePicUrl != null) {
            user.setProfilePicUrl(profilePicUrl);
        }
        return userRepository.save(user);
    }
}
