package com.solarshield.database;

import com.solarshield.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository handling database operations for Solar Panels.
 */
public class SolarPanelRepository {

    public boolean save(SolarPanel panel) {
        String sql = """
            INSERT INTO solar_panels 
            (panel_id, name, location, installation_date, panel_type, rated_power, latitude, longitude, status, last_inspection_date, current_fault, severity, maintenance_priority, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, panel.getPanelId());
            ps.setString(2, panel.getName());
            ps.setString(3, panel.getLocation());
            ps.setString(4, panel.getInstallationDate() != null ? panel.getInstallationDate().toString() : LocalDate.now().toString());
            ps.setString(5, panel.getPanelType());
            ps.setDouble(6, panel.getRatedPower());
            ps.setDouble(7, panel.getLatitude());
            ps.setDouble(8, panel.getLongitude());
            ps.setString(9, panel.getStatus().name());
            ps.setString(10, panel.getLastInspectionDate() != null ? panel.getLastInspectionDate().toString() : null);
            ps.setString(11, panel.getCurrentFault().name());
            ps.setString(12, panel.getSeverity().name());
            ps.setString(13, panel.getMaintenancePriority().name());
            ps.setString(14, panel.getCreatedAt() != null ? panel.getCreatedAt().toString() : LocalDateTime.now().toString());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        panel.setId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[SolarPanelRepository] Error saving panel: " + e.getMessage());
        }
        return false;
    }

    public boolean update(SolarPanel panel) {
        String sql = """
            UPDATE solar_panels 
            SET name = ?, location = ?, installation_date = ?, panel_type = ?, rated_power = ?, 
                latitude = ?, longitude = ?, status = ?, last_inspection_date = ?, 
                current_fault = ?, severity = ?, maintenance_priority = ?
            WHERE panel_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, panel.getName());
            ps.setString(2, panel.getLocation());
            ps.setString(3, panel.getInstallationDate() != null ? panel.getInstallationDate().toString() : null);
            ps.setString(4, panel.getPanelType());
            ps.setDouble(5, panel.getRatedPower());
            ps.setDouble(6, panel.getLatitude());
            ps.setDouble(7, panel.getLongitude());
            ps.setString(8, panel.getStatus().name());
            ps.setString(9, panel.getLastInspectionDate() != null ? panel.getLastInspectionDate().toString() : null);
            ps.setString(10, panel.getCurrentFault().name());
            ps.setString(11, panel.getSeverity().name());
            ps.setString(12, panel.getMaintenancePriority().name());
            ps.setString(13, panel.getPanelId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SolarPanelRepository] Error updating panel: " + e.getMessage());
            return false;
        }
    }

    public boolean updateStatusAndFault(String panelId, PanelStatus status, FaultType fault,
                                        Severity severity, MaintenancePriority priority,
                                        LocalDateTime inspectionDate) {
        String sql = """
            UPDATE solar_panels 
            SET status = ?, current_fault = ?, severity = ?, maintenance_priority = ?, last_inspection_date = ?
            WHERE panel_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setString(2, fault.name());
            ps.setString(3, severity.name());
            ps.setString(4, priority.name());
            ps.setString(5, inspectionDate != null ? inspectionDate.toString() : LocalDateTime.now().toString());
            ps.setString(6, panelId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[SolarPanelRepository] Error updating panel status: " + e.getMessage());
            return false;
        }
    }

    public Optional<SolarPanel> findByPanelId(String panelId) {
        String sql = "SELECT * FROM solar_panels WHERE panel_id = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, panelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPanel(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[SolarPanelRepository] Error finding panel: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<SolarPanel> findAll() {
        List<SolarPanel> list = new ArrayList<>();
        String sql = "SELECT * FROM solar_panels ORDER BY panel_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToPanel(rs));
            }
        } catch (SQLException e) {
            System.err.println("[SolarPanelRepository] Error loading all panels: " + e.getMessage());
        }
        return list;
    }

    public int countTotal() {
        return queryCount("SELECT COUNT(*) FROM solar_panels");
    }

    public int countHealthy() {
        return queryCount("SELECT COUNT(*) FROM solar_panels WHERE status = 'HEALTHY'");
    }

    public int countFaulty() {
        return queryCount("SELECT COUNT(*) FROM solar_panels WHERE status IN ('FAULTY', 'WARNING', 'CRITICAL')");
    }

    public int countCritical() {
        return queryCount("SELECT COUNT(*) FROM solar_panels WHERE status = 'CRITICAL' OR severity = 'CRITICAL'");
    }

    private int queryCount(String sql) {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[SolarPanelRepository] Error querying count: " + e.getMessage());
        }
        return 0;
    }

    private SolarPanel mapResultSetToPanel(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String panelId = rs.getString("panel_id");
        String name = rs.getString("name");
        String location = rs.getString("location");
        LocalDate installDate = parseLocalDate(rs.getString("installation_date"));
        String panelType = rs.getString("panel_type");
        double ratedPower = rs.getDouble("rated_power");
        double lat = rs.getDouble("latitude");
        double lon = rs.getDouble("longitude");
        PanelStatus status = PanelStatus.fromString(rs.getString("status"));
        LocalDateTime lastInspect = parseLocalDateTime(rs.getString("last_inspection_date"));
        FaultType fault = FaultType.fromString(rs.getString("current_fault"));
        Severity severity = Severity.fromString(rs.getString("severity"));
        MaintenancePriority priority = MaintenancePriority.fromString(rs.getString("maintenance_priority"));
        LocalDateTime created = parseLocalDateTime(rs.getString("created_at"));

        return new SolarPanel(id, panelId, name, location, installDate, panelType, ratedPower,
                lat, lon, status, lastInspect, fault, severity, priority, created);
    }

    private LocalDate parseLocalDate(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(str.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(str.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
