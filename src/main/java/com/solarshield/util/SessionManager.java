package com.solarshield.util;

import com.solarshield.model.User;

/**
 * Thread-safe singleton managing the active authenticated user session.
 * Prevents unauthorized dashboard access and safely handles user logout.
 */
public class SessionManager {
    private static SessionManager instance;
    private User currentUser;

    private SessionManager() {
        // Private constructor for singleton pattern
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public synchronized void login(User user) {
        this.currentUser = user;
    }

    public synchronized void logout() {
        this.currentUser = null;
    }

    public synchronized boolean isAuthenticated() {
        return this.currentUser != null;
    }

    public synchronized User getCurrentUser() {
        return this.currentUser;
    }

    public synchronized String getCurrentUserName() {
        return currentUser != null ? currentUser.getName() : "Guest";
    }

    public synchronized String getCurrentUserEmail() {
        return currentUser != null ? currentUser.getEmail() : "N/A";
    }
}
