package com.ideamanagement.platform.security;

public class TenantContext {
    private static final ThreadLocal<String> currentOrgId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserRole = new ThreadLocal<>();

    public static void setCurrentOrgId(String orgId) {
        currentOrgId.set(orgId);
    }

    public static String getCurrentOrgId() {
        return currentOrgId.get();
    }

    public static void setCurrentUserId(String userId) {
        currentUserId.set(userId);
    }

    public static String getCurrentUserId() {
        return currentUserId.get();
    }

    public static void setCurrentUserRole(String role) {
        currentUserRole.set(role);
    }

    public static String getCurrentUserRole() {
        return currentUserRole.get();
    }

    public static void clear() {
        currentOrgId.remove();
        currentUserId.remove();
        currentUserRole.remove();
    }
}

// ThreadLocal context manager.
// ThreadLocal context manager.
// ThreadLocal context manager.