package com.solarshield.database;

import com.solarshield.model.AlertItem;
import com.solarshield.model.Severity;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository handling database operations for real-time monitoring alerts.
 */
public class AlertRepository {

    public boolean save(AlertItem alert) {
        String sql = """
            INSERT INTO alerts (panel_id, fault_report_id, severity, message, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, alert.getPanelId());
            if (alert.getFaultReportId() != null) {
                ps.setInt(2, alert.getFaultReportId());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, alert.getSeverity().name());
            ps.setString(4, alert.getMessage());
            ps.setString(5, alert.getStatus());
            ps.setString(6, alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : LocalDateTime.now().toString());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        alert.setId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[AlertRepository] Error saving alert: " + e.getMessage());
        }
        return false;
    }

    public List<AlertItem> findActive() {
        List<AlertItem> list = new ArrayList<>();
        String sql = "SELECT * FROM alerts WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT 10";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToAlert(rs));
            }
        } catch (SQLException e) {
            System.err.println("[AlertRepository] Error fetching active alerts: " + e.getMessage());
        }
        return list;
    }

    public List<AlertItem> findAll() {
        List<AlertItem> list = new ArrayList<>();
        String sql = "SELECT * FROM alerts ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToAlert(rs));
            }
        } catch (SQLException e) {
            System.err.println("[AlertRepository] Error fetching all alerts: " + e.getMessage());
        }
        return list;
    }

    public boolean updateStatus(int id, String status) {
        String sql = "UPDATE alerts SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[AlertRepository] Error updating alert status: " + e.getMessage());
            return false;
        }
    }

    public int countActive() {
        String sql = "SELECT COUNT(*) FROM alerts WHERE status = 'ACTIVE'";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[AlertRepository] Error counting active alerts: " + e.getMessage());
        }
        return 0;
    }

    private AlertItem mapResultSetToAlert(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String panelId = rs.getString("panel_id");
        int frIdVal = rs.getInt("fault_report_id");
        Integer faultReportId = rs.wasNull() ? null : frIdVal;
        Severity severity = Severity.fromString(rs.getString("severity"));
        String msg = rs.getString("message");
        String status = rs.getString("status");
        LocalDateTime created = LocalDateTime.now();
        String cStr = rs.getString("created_at");
        if (cStr != null) {
            try { created = LocalDateTime.parse(cStr); } catch (Exception ignored) {}
        }
        return new AlertItem(id, panelId, faultReportId, severity, msg, status, created);
    }
}
