package com.solarshield.model;

import java.time.LocalDateTime;

/**
 * Model class representing an authenticated system user (Engineer, Technician, Administrator).
 * Demonstrates proper encapsulation with private fields, defensive getters/setters.
 */
public class User {
    private int id;
    private String name;
    private String email;
    private String passwordHash;
    private String role;
    private LocalDateTime createdAt;

    public User() {
        this.createdAt = LocalDateTime.now();
        this.role = "TECHNICIAN";
    }

    public User(int id, String name, String email, String passwordHash, String role, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : "TECHNICIAN";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public User(String name, String email, String passwordHash, String role) {
        this(0, name, email, passwordHash, role, LocalDateTime.now());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return name + " (" + email + ")";
    }
}
