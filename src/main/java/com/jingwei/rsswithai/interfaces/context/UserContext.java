package com.jingwei.rsswithai.interfaces.context;

/**
 * Front-side authenticated user context stored via {@link java.lang.ScopedValue}.
 */
public final class UserContext {

    public static final ScopedValue<Long> USER_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> USERNAME = ScopedValue.newInstance();

    private UserContext() {
    }

    public static long currentUserId() {
        return USER_ID.orElseThrow(() -> new RuntimeException("Unauthorized"));
    }

    public static String currentUsername() {
        return USERNAME.orElseThrow(() -> new RuntimeException("Unauthorized"));
    }
}