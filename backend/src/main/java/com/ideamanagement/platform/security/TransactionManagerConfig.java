package com.ideamanagement.platform.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

@Configuration
public class TransactionManagerConfig {

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory) {
            @Override
            protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
                super.prepareSynchronization(status, definition);
                if (status.isNewTransaction()) {
                    String orgId = TenantContext.getCurrentOrgId();
                    String userId = TenantContext.getCurrentUserId();
                    EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
                    if (em != null) {
                        // Set current organization locally in PostgreSQL transaction
                        String orgVal = orgId != null ? orgId : "";
                        em.createNativeQuery("SELECT set_config('app.current_org_id', :orgId, true)")
                          .setParameter("orgId", orgVal)
                          .getSingleResult();

                        // Set current user locally in PostgreSQL transaction
                        String userVal = userId != null ? userId : "";
                        em.createNativeQuery("SELECT set_config('app.current_user_id', :userId, true)")
                          .setParameter("userId", userVal)
                          .getSingleResult();
                    }
                }
            }
        };
    }
}

// Transaction context binding configuration completed.
// Transaction context binding configuration completed.
// Transaction context binding configuration completed.