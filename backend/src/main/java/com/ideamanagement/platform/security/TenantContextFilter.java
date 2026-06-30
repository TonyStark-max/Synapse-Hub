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

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private final com.ideamanagement.platform.repository.UserRepository userRepository;

    public TenantContextFilter(com.ideamanagement.platform.repository.UserRepository userRepository) {
        this.userRepository = userRepository;
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
                    if (user.getOrgId() != null) {
                        TenantContext.setCurrentOrgId(user.getOrgId());
                        TenantContext.setCurrentUserRole(user.getRole());
                    }
                } else {
                    String email = jwt.getClaimAsString("email");
                    String name = jwt.getClaimAsString("name");
                    String role = "lostg826@gmail.com".equals(email) ? "ADMIN" : "MEMBER";
                    
                    com.ideamanagement.platform.model.User newUser = com.ideamanagement.platform.model.User.builder()
                            .id(userId)
                            .email(email != null ? email : "")
                            .name(name != null ? name : "User")
                            .role(role)
                            .build();
                    userRepository.save(newUser);
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
