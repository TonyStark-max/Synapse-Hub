package com.ideamanagement.platform.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class DBSecurityContext {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Explicitly sets the tenant and user context in both Java's ThreadLocal and PostgreSQL session.
     * This is crucial when tenant context changes during a transaction (e.g. creating/joining an org).
     */
    public void setContext(String orgId, String userId) {
        TenantContext.setCurrentOrgId(orgId);
        TenantContext.setCurrentUserId(userId);

        String orgVal = orgId != null ? orgId : "";
        entityManager.createNativeQuery("SELECT set_config('app.current_org_id', :orgId, true)")
                .setParameter("orgId", orgVal)
                .getSingleResult();

        String userVal = userId != null ? userId : "";
        entityManager.createNativeQuery("SELECT set_config('app.current_user_id', :userId, true)")
                .setParameter("userId", userVal)
                .getSingleResult();
    }

    /**
     * Set only the role parameter.
     */
    public void setRole(String role) {
        TenantContext.setCurrentUserRole(role);
    }
}
