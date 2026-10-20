package com.solarshield.database;

import com.solarshield.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository handling database operations for Maintenance tasks and service work orders.
 */
public class MaintenanceRepository {

    public boolean save(Maintenance m) {
        String sql = """
            INSERT INTO maintenance 
            (panel_id, fault_report_id, technician, maintenance_type, scheduled_date, priority, status, notes, created_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, m.getPanelId());
            if (m.getFaultReportId() != null) {
                ps.setInt(2, m.getFaultReportId());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, m.getTechnician());
            ps.setString(4, m.getMaintenanceType());
            ps.setString(5, m.getScheduledDate() != null ? m.getScheduledDate().toString() : LocalDate.now().toString());
            ps.setString(6, m.getPriority().name());
            ps.setString(7, m.getStatus().name());
            ps.setString(8, m.getNotes());
            ps.setString(9, m.getCreatedAt() != null ? m.getCreatedAt().toString() : LocalDateTime.now().toString());
            ps.setString(10, m.getCompletedAt() != null ? m.getCompletedAt().toString() : null);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        m.setId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[MaintenanceRepository] Error saving maintenance task: " + e.getMessage());
        }
        return false;
    }

    public boolean update(Maintenance m) {
        String sql = """
            UPDATE maintenance 
            SET technician = ?, maintenance_type = ?, scheduled_date = ?, priority = ?, status = ?, notes = ?, completed_at = ?
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, m.getTechnician());
            ps.setString(2, m.getMaintenanceType());
            ps.setString(3, m.getScheduledDate() != null ? m.getScheduledDate().toString() : null);
            ps.setString(4, m.getPriority().name());
            ps.setString(5, m.getStatus().name());
            ps.setString(6, m.getNotes());
            ps.setString(7, m.getCompletedAt() != null ? m.getCompletedAt().toString() : null);
            ps.setInt(8, m.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MaintenanceRepository] Error updating maintenance: " + e.getMessage());
            return false;
        }
    }

    public boolean updateStatus(int id, MaintenanceStatus status) {
        String sql = "UPDATE maintenance SET status = ?, completed_at = ? WHERE id = ?";
        String completedStr = status == MaintenanceStatus.COMPLETED ? LocalDateTime.now().toString() : null;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setString(2, completedStr);
            ps.setInt(3, id);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MaintenanceRepository] Error updating status: " + e.getMessage());
            return false;
        }
    }

    public Optional<Maintenance> findById(int id) {
        String sql = "SELECT * FROM maintenance WHERE id = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToMaintenance(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MaintenanceRepository] Error finding maintenance: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<Maintenance> findAll() {
        List<Maintenance> list = new ArrayList<>();
        String sql = "SELECT * FROM maintenance ORDER BY scheduled_date ASC, priority DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToMaintenance(rs));
            }
        } catch (SQLException e) {
            System.err.println("[MaintenanceRepository] Error listing all maintenance: " + e.getMessage());
        }
        return list;
    }

    public List<Maintenance> findFiltered(String panelId, MaintenanceStatus status, MaintenancePriority priority) {
        StringBuilder sql = new StringBuilder("SELECT * FROM maintenance WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (panelId != null && !panelId.trim().isEmpty() && !panelId.equalsIgnoreCase("ALL")) {
            sql.append(" AND panel_id = ?");
            params.add(panelId.trim());
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        if (priority != null) {
            sql.append(" AND priority = ?");
            params.add(priority.name());
        }

        sql.append(" ORDER BY scheduled_date ASC");

        List<Maintenance> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToMaintenance(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MaintenanceRepository] Error querying filtered maintenance: " + e.getMessage());
        }
        return list;
    }

    public int countPending() {
        String sql = "SELECT COUNT(*) FROM maintenance WHERE status IN ('PENDING', 'SCHEDULED', 'IN_PROGRESS')";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[MaintenanceRepository] Error counting pending: " + e.getMessage());
        }
        return 0;
    }

    private Maintenance mapResultSetToMaintenance(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String panelId = rs.getString("panel_id");
        int faultReportIdVal = rs.getInt("fault_report_id");
        Integer faultReportId = rs.wasNull() ? null : faultReportIdVal;
        String tech = rs.getString("technician");
        String type = rs.getString("maintenance_type");

        LocalDate scheduled = parseDate(rs.getString("scheduled_date"));
        MaintenancePriority priority = MaintenancePriority.fromString(rs.getString("priority"));
        MaintenanceStatus status = MaintenanceStatus.fromString(rs.getString("status"));
        String notes = rs.getString("notes");
        LocalDateTime created = parseDateTime(rs.getString("created_at"));
        LocalDateTime completed = parseDateTime(rs.getString("completed_at"));

        return new Maintenance(id, panelId, faultReportId, tech, type, scheduled, priority, status, notes, created, completed);
    }

    private LocalDate parseDate(String s) {
        if (s == null) return LocalDate.now();
        try { return LocalDate.parse(s); } catch (Exception e) { return LocalDate.now(); }
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null) return null;
        try { return LocalDateTime.parse(s); } catch (Exception e) { return null; }
    }
}
