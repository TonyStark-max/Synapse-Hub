package com.ideamanagement.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private final com.ideamanagement.platform.repository.UserRepository userRepository;
    private final String adminEmail;

    public TenantContextFilter(com.ideamanagement.platform.repository.UserRepository userRepository,
                               @Value("${system.admin.email:admin@example.com}") String adminEmail) {
        this.userRepository = userRepository;
        this.adminEmail = adminEmail;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            var jwt = jwtAuth.getToken();
            
            // sub -> user_id
            String userId = jwt.getSubject();

            if (userId != null) {
                TenantContext.setCurrentUserId(userId);
                // Load or automatically register user on their very first request
                var userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    var user = userOpt.get();
                    boolean changed = false;
                    
                    // Enforce that system administrator always has the ADMIN role
                    if (adminEmail.equals(user.getEmail()) && !"ADMIN".equals(user.getRole())) {
                        user.setRole("ADMIN");
                        changed = true;
                    }
                    
                    if (changed) {
                        userRepository.save(user);
                    }

                    TenantContext.setCurrentUserRole(user.getRole());
                    if (user.getOrgId() != null) {
                        TenantContext.setCurrentOrgId(user.getOrgId());
                    }
                } else {
                    String email = jwt.getClaimAsString("email");
                    String name = jwt.getClaimAsString("name");
                    String role = adminEmail.equals(email) ? "ADMIN" : "MEMBER";
                    
                    com.ideamanagement.platform.model.User newUser = com.ideamanagement.platform.model.User.builder()
                            .id(userId)
                            .email(email != null ? email : "")
                            .name(name != null ? name : "User")
                            .role(role)
                            .build();
                    userRepository.save(newUser);
                    
                    TenantContext.setCurrentUserRole(role);
                }
            }
        }
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Crucial: Clear the context after the request to prevent thread leakage
            TenantContext.clear();
        }
    }
}
