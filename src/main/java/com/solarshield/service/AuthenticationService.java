package com.solarshield.service;

import com.solarshield.database.UserRepository;
import com.solarshield.model.User;
import com.solarshield.util.SessionManager;
import com.solarshield.util.ValidationUtils;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service managing user authentication, registration, password hashing (BCrypt),
 * and session state.
 */
public class AuthenticationService {

    private final UserRepository userRepository;

    public AuthenticationService() {
        this.userRepository = new UserRepository();
    }

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public static class AuthResult {
        private final boolean success;
        private final String message;
        private final User user;

        public AuthResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public User getUser() {
            return user;
        }
    }

    public AuthResult register(String name, String email, String password, String confirmPassword) {
        if (!ValidationUtils.isNotEmpty(name)) {
            return new AuthResult(false, "Full Name is required.", null);
        }
        if (!ValidationUtils.isValidEmail(email)) {
            return new AuthResult(false, "Please provide a valid email address.", null);
        }
        if (!ValidationUtils.isValidPassword(password)) {
            return new AuthResult(false, "Password must be at least 6 characters long.", null);
        }
        if (!password.equals(confirmPassword)) {
            return new AuthResult(false, "Passwords do not match.", null);
        }

        String normalizedEmail = email.toLowerCase().trim();
        if (userRepository.emailExists(normalizedEmail)) {
            return new AuthResult(false, "An account with this email address already exists.", null);
        }

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        User newUser = new User(0, name.trim(), normalizedEmail, passwordHash, "ENGINEER", LocalDateTime.now());

        boolean saved = userRepository.save(newUser);
        if (saved) {
            return new AuthResult(true, "Account created successfully. You may now log in.", newUser);
        } else {
            return new AuthResult(false, "Database error: unable to register user.", null);
        }
    }

    public AuthResult login(String email, String password) {
        if (!ValidationUtils.isNotEmpty(email) || !ValidationUtils.isNotEmpty(password)) {
            return new AuthResult(false, "Email and password are required.", null);
        }

        String normalizedEmail = email.toLowerCase().trim();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            return new AuthResult(false, "Invalid email or password.", null);
        }

        User user = userOpt.get();
        boolean passwordMatches = false;
        try {
            passwordMatches = BCrypt.checkpw(password, user.getPasswordHash());
        } catch (Exception e) {
            System.err.println("[AuthenticationService] Password verification error: " + e.getMessage());
        }

        if (!passwordMatches) {
            return new AuthResult(false, "Invalid email or password.", null);
        }

        // Establish session
        SessionManager.getInstance().login(user);
        return new AuthResult(true, "Login successful.", user);
    }

    public void logout() {
        SessionManager.getInstance().logout();
    }

    public boolean isLoggedIn() {
        return SessionManager.getInstance().isAuthenticated();
    }

    public User getCurrentUser() {
        return SessionManager.getInstance().getCurrentUser();
    }
}
