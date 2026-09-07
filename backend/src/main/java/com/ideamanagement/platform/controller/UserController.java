package com.ideamanagement.platform.controller;

import com.ideamanagement.platform.model.User;
import com.ideamanagement.platform.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/company")
    public ResponseEntity<User> setCompany(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> payload) {
        String userId = jwt.getClaimAsString("sub");
        String companyId = payload.get("companyId");
        if (companyId == null || companyId.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        User updatedUser = userService.setCompany(userId, companyId);
        return ResponseEntity.ok(updatedUser);
    }

    @GetMapping("/profile")
    public ResponseEntity<User> getProfile(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        User user = userService.getUser(userId);
        if (user == null) {
            user = User.builder().id(userId).build();
        }
        return ResponseEntity.ok(user);
    }

    @PutMapping("/profile")
    public ResponseEntity<User> updateProfile(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> payload) {
        String userId = jwt.getClaimAsString("sub");
        String name = payload.get("name");
        String profilePicUrl = payload.get("profilePicUrl");
        User updatedUser = userService.updateProfile(userId, name, profilePicUrl);
        return ResponseEntity.ok(updatedUser);
    }
}
