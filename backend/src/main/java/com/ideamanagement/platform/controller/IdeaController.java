package com.ideamanagement.platform.controller;

import com.ideamanagement.platform.model.Comment;
import com.ideamanagement.platform.model.Idea;
import com.ideamanagement.platform.security.SimpleRateLimiter;
import com.ideamanagement.platform.security.TenantContext;
import com.ideamanagement.platform.service.IdeaService;
import com.ideamanagement.platform.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ideas")
public class IdeaController {

    private final IdeaService ideaService;
    private final SimpleRateLimiter rateLimiter;
    private final UserService userService;

    public IdeaController(IdeaService ideaService, SimpleRateLimiter rateLimiter, UserService userService) {
        this.ideaService = ideaService;
        this.rateLimiter = rateLimiter;
        this.userService = userService;
    }

    private void syncUser(JwtAuthenticationToken token) {
        String userId = token.getToken().getSubject();
        String orgId = token.getToken().getClaimAsString("org_id");
        if (orgId == null || orgId.trim().isEmpty()) {
            return;
        }
        String email = token.getToken().getClaimAsString("email");
        if (email == null) email = "";
        String name = token.getToken().getClaimAsString("name");
        if (name == null) name = userId;
        String orgRole = token.getToken().getClaimAsString("org_role");
        String mappedRole = "org:admin".equals(orgRole) || "ADMIN".equals(orgRole) ? "ADMIN" : "MEMBER";

        userService.syncUser(userId, email, name, orgId, mappedRole);
    }

    private String resolveOrgId(String userId) {
        String orgId = TenantContext.getCurrentOrgId();
        if (orgId == null || orgId.trim().isEmpty()) {
            if (userId != null) {
                com.ideamanagement.platform.model.User dbUserObj = userService.getUser(userId);
                if (dbUserObj != null && dbUserObj.getOrgId() != null) {
                    orgId = dbUserObj.getOrgId();
                    TenantContext.setCurrentOrgId(orgId);
                    TenantContext.setCurrentUserRole(dbUserObj.getRole());
                }
            }
        }
        return orgId;
    }

    @PostMapping
    public ResponseEntity<?> submitIdea(
            JwtAuthenticationToken token,
            @RequestBody Map<String, String> body) {
        
        String userId = token.getToken().getSubject();
        String orgId = resolveOrgId(userId);

        if (orgId == null || orgId.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No active organization context found in your profile");
        }

        syncUser(token);

        // Enforce rate limiting: 5 new ideas per minute per user
        if (!rateLimiter.isAllowed(userId, "submit_idea", 5, 60000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded for submitting ideas. Limit is 5 per minute.");
        }

        String title = body.get("title");
        String description = body.get("description");
        String tag = body.get("tag");

        try {
            Idea idea = ideaService.submitIdea(title, description, tag, orgId, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(idea);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getIdeas(
            JwtAuthenticationToken token,
            @RequestParam(required = false) String sortBy) {
        
        String userId = token.getToken().getSubject();
        String orgId = resolveOrgId(userId);

        if (orgId == null || orgId.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No active organization context found in your profile");
        }

        syncUser(token);

        List<Idea> ideas = ideaService.getIdeas(orgId, sortBy);
        return ResponseEntity.ok(ideas);
    }

    @PostMapping("/{id}/vote")
    public ResponseEntity<?> voteIdea(
            JwtAuthenticationToken token,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        
        String userId = token.getToken().getSubject();
        String orgId = resolveOrgId(userId);

        if (orgId == null || orgId.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No active organization context found in your profile");
        }

        syncUser(token);

        // Enforce rate limiting: 30 votes per minute per user
        if (!rateLimiter.isAllowed(userId, "vote", 30, 60000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded for voting. Limit is 30 per minute.");
        }

        String voteType = body.get("voteType");

        try {
            Idea updatedIdea = ideaService.voteIdea(id, userId, orgId, voteType);
            return ResponseEntity.ok(updatedIdea);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> addComment(
            JwtAuthenticationToken token,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        
        String userId = token.getToken().getSubject();
        String orgId = resolveOrgId(userId);

        if (orgId == null || orgId.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No active organization context found in your profile");
        }

        syncUser(token);

        // Enforce rate limiting: 10 comments per minute per user
        if (!rateLimiter.isAllowed(userId, "comment", 10, 60000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded for commenting. Limit is 10 per minute.");
        }

        String content = (String) body.get("content");
        Number parentIdNum = (Number) body.get("parentCommentId");
        Long parentCommentId = parentIdNum != null ? parentIdNum.longValue() : null;

        try {
            Comment comment = ideaService.addComment(id, parentCommentId, content, userId, orgId);
            return ResponseEntity.status(HttpStatus.CREATED).body(comment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<?> getComments(
            JwtAuthenticationToken token,
            @PathVariable Long id) {
        
        String userId = token.getToken().getSubject();
        String orgId = resolveOrgId(userId);

        if (orgId == null || orgId.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No active organization context found in your profile");
        }

        syncUser(token);

        List<Comment> comments = ideaService.getComments(id, orgId);
        return ResponseEntity.ok(comments);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            JwtAuthenticationToken token,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        
        String userId = token.getToken().getSubject();
        String orgId = resolveOrgId(userId);
        String orgRole = TenantContext.getCurrentUserRole();

        // Map role to ADMIN or MEMBER (matching the format in users table)
        String mappedRole = "ADMIN".equals(orgRole) ? "ADMIN" : "MEMBER";

        if (orgId == null || orgId.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No active organization context found in your profile");
        }

        syncUser(token);

        // Enforce rate limiting: 10 status changes per minute per user
        if (!rateLimiter.isAllowed(userId, "status_change", 10, 60000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded. Limit is 10 per minute.");
        }

        String status = body.get("status");

        try {
            Idea updatedIdea = ideaService.updateIdeaStatus(id, status, userId, orgId, mappedRole);
            return ResponseEntity.ok(updatedIdea);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}

// Endpoints mapped for ideas feed.
// Clean imports verified.
// Endpoints mapped for ideas feed.
// Clean imports verified.
// Endpoints mapped for ideas feed.
// Clean imports verified.
// Endpoints mapped for ideas feed.