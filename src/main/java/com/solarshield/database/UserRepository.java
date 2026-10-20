package com.solarshield.database;

import com.solarshield.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository handling database persistence and retrieval of User accounts.
 * Utilizes PreparedStatements to prevent SQL injection vulnerabilities.
 */
public class UserRepository {

    public boolean save(User user) {
        String sql = "INSERT INTO users (name, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail().toLowerCase().trim());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole());
            ps.setString(5, user.getCreatedAt() != null ? user.getCreatedAt().toString() : LocalDateTime.now().toString());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        user.setId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[UserRepository] Error saving user: " + e.getMessage());
        }
        return false;
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String sql = "SELECT id, name, email, password_hash, role, created_at FROM users WHERE LOWER(email) = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email.toLowerCase().trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserRepository] Error finding user by email: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<User> findById(int id) {
        String sql = "SELECT id, name, email, password_hash, role, created_at FROM users WHERE id = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserRepository] Error finding user by id: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean emailExists(String email) {
        return findByEmail(email).isPresent();
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String hash = rs.getString("password_hash");
        String role = rs.getString("role");
        String createdStr = rs.getString("created_at");

        LocalDateTime createdAt = LocalDateTime.now();
        if (createdStr != null) {
            try {
                createdAt = LocalDateTime.parse(createdStr);
            } catch (Exception ignored) {}
        }

        return new User(id, name, email, hash, role, createdAt);
    }
}
