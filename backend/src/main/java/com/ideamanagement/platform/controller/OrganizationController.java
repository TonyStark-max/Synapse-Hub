package com.ideamanagement.platform.controller;

import com.ideamanagement.platform.model.Organization;
import com.ideamanagement.platform.model.OrganizationRequest;
import com.ideamanagement.platform.model.User;
import com.ideamanagement.platform.service.OrganizationService;
import com.ideamanagement.platform.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final UserService userService;
    private final String adminEmail;

    public OrganizationController(OrganizationService organizationService, 
                                  UserService userService,
                                  @Value("${system.admin.email:admin@example.com}") String adminEmail) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.adminEmail = adminEmail;
    }

    @PostMapping
    public ResponseEntity<?> createOrganization(
            JwtAuthenticationToken token,
            @RequestBody Map<String, String> body) {
        
        String orgId = body.get("orgId");
        String name = body.get("name");

        if (orgId == null || orgId.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Organization ID and Name are required");
        }

        // Derive user identity from verified JWT claims
        String userId = token.getToken().getSubject();
        String email = token.getToken().getClaimAsString("email");
        String userName = token.getToken().getClaimAsString("name");
        
        if (email == null) email = "";
        if (userName == null) userName = "";

        try {
            Organization org = organizationService.createOrganization(orgId, name, userId, email, userName);
            return ResponseEntity.status(HttpStatus.CREATED).body(org);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinOrganization(
            JwtAuthenticationToken token,
            @RequestBody Map<String, String> body) {
        
        String inviteCode = body.get("inviteCode");

        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Invite code is required");
        }

        // Derive user identity from verified JWT claims
        String userId = token.getToken().getSubject();
        String email = token.getToken().getClaimAsString("email");
        String userName = token.getToken().getClaimAsString("name");

        if (email == null) email = "";
        if (userName == null) userName = "";

        try {
            Organization org = organizationService.joinOrganization(inviteCode, userId, email, userName);
            return ResponseEntity.ok(org);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Public endpoint for looking up an organization by invite code before joining
    @GetMapping("/invite/{inviteCode}")
    public ResponseEntity<?> lookupInvite(@PathVariable String inviteCode) {
        Organization org = organizationService.findByInviteCode(inviteCode);
        return org != null 
            ? ResponseEntity.ok(Map.of("id", org.getId(), "name", org.getName()))
            : ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invalid invite code");
    }

    // Endpoint to get all active organizations
    @GetMapping("/active")
    public ResponseEntity<?> getActiveOrganizations() {
        return ResponseEntity.ok(organizationService.getAllOrganizations());
    }

    // Submit a request to create a workspace
    @PostMapping("/requests")
    public ResponseEntity<?> submitWorkspaceRequest(
            JwtAuthenticationToken token,
            @RequestBody Map<String, String> body) {
        
        String orgId = body.get("orgId");
        String name = body.get("name");
        String description = body.get("description");

        if (orgId == null || orgId.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Workspace ID and Name are required");
        }

        String userId = token.getToken().getSubject();
        String email = token.getToken().getClaimAsString("email");
        String userName = token.getToken().getClaimAsString("name");

        if (email == null) email = "";
        if (userName == null) userName = userId;

        try {
            OrganizationRequest request = organizationService.createOrgRequest(orgId, name, description, userId, email, userName);
            return ResponseEntity.status(HttpStatus.CREATED).body(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // List all pending organization requests (restricted to system admin)
    @GetMapping("/requests")
    public ResponseEntity<?> getPendingWorkspaceRequests(JwtAuthenticationToken token) {
        String email = token.getToken().getClaimAsString("email");
        System.out.println("DEBUG: Incoming request from email=" + email + ", claims=" + token.getToken().getClaims());
        if (!adminEmail.equals(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only the system administrator (" + adminEmail + ") can view organization requests");
        }
        String userId = token.getToken().getSubject();
        return ResponseEntity.ok(organizationService.getPendingRequests(userId));
    }

    // Approve an organization request (restricted to system admin)
    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<?> approveWorkspaceRequest(
            JwtAuthenticationToken token,
            @PathVariable Long id) {
        
        String email = token.getToken().getClaimAsString("email");
        if (!adminEmail.equals(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only the system administrator (" + adminEmail + ") can approve organization requests");
        }

        try {
            String userId = token.getToken().getSubject();
            Organization org = organizationService.approveOrgRequest(id, userId);
            return ResponseEntity.ok(org);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Reject an organization request (restricted to system admin)
    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<?> rejectWorkspaceRequest(
            JwtAuthenticationToken token,
            @PathVariable Long id) {
        
        String email = token.getToken().getClaimAsString("email");
        if (!adminEmail.equals(email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only the system administrator (" + adminEmail + ") can reject organization requests");
        }

        try {
            organizationService.rejectOrgRequest(id, token.getToken().getSubject());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/users/me")
    public ResponseEntity<?> getCurrentUserProfile(JwtAuthenticationToken token) {
        String userId = token.getToken().getSubject();
        String email = token.getToken().getClaimAsString("email");
        String name = token.getToken().getClaimAsString("name");
        
        return ResponseEntity.ok(organizationService.getOrCreateUser(userId, email, name));
    }

    @PostMapping("/leave")
    public ResponseEntity<?> leaveWorkspace(JwtAuthenticationToken token) {
        String userId = token.getToken().getSubject();
        try {
            organizationService.leaveWorkspace(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}

// Workspace management endpoints.
// Workspace management endpoints.
// Workspace management endpoints.
// Workspace management endpoints.