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
            if (orgId != null && !orgId.equals(user.getOrgId())) {
                user.setOrgId(orgId);
                changed = true;
            }
            if (!mappedRole.equals(user.getRole())) {
                user.setRole(mappedRole);
                changed = true;
            }
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
}
