package com.solarshield.database;

import com.solarshield.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Repository for storing, searching, and aggregating AI fault detection diagnostic reports.
 */
public class FaultReportRepository {

    public boolean save(FaultReport report) {
        String sql = """
            INSERT INTO fault_reports 
            (panel_id, fault_type, confidence, severity, maintenance_priority, 
             affected_x, affected_y, affected_width, affected_height, affected_region_label, 
             estimated_energy_loss_watts, estimated_energy_loss_percent, image_path, analyzed_image_path, 
             analysis_notes, temperature, voltage, current, irradiance, humidity, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, report.getPanelId());
            ps.setString(2, report.getFaultType().name());
            ps.setDouble(3, report.getConfidence());
            ps.setString(4, report.getSeverity().name());
            ps.setString(5, report.getMaintenancePriority().name());
            ps.setInt(6, report.getAffectedX());
            ps.setInt(7, report.getAffectedY());
            ps.setInt(8, report.getAffectedWidth());
            ps.setInt(9, report.getAffectedHeight());
            ps.setString(10, report.getAffectedRegionLabel());
            ps.setDouble(11, report.getEstimatedEnergyLossWatts());
            ps.setDouble(12, report.getEstimatedEnergyLossPercent());
            ps.setString(13, report.getImagePath());
            ps.setString(14, report.getAnalyzedImagePath());
            ps.setString(15, report.getAnalysisNotes());

            setNullableDouble(ps, 16, report.getTemperature());
            setNullableDouble(ps, 17, report.getVoltage());
            setNullableDouble(ps, 18, report.getCurrent());
            setNullableDouble(ps, 19, report.getIrradiance());
            setNullableDouble(ps, 20, report.getHumidity());

            ps.setString(21, report.getCreatedAt() != null ? report.getCreatedAt().toString() : LocalDateTime.now().toString());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        report.setId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[FaultReportRepository] Error saving report: " + e.getMessage());
        }
        return false;
    }

    public Optional<FaultReport> findById(int id) {
        String sql = "SELECT * FROM fault_reports WHERE id = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToReport(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[FaultReportRepository] Error finding report by id: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<FaultReport> findAll() {
        List<FaultReport> list = new ArrayList<>();
        String sql = "SELECT * FROM fault_reports ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapResultSetToReport(rs));
            }
        } catch (SQLException e) {
            System.err.println("[FaultReportRepository] Error loading all reports: " + e.getMessage());
        }
        return list;
    }

    public List<FaultReport> findFiltered(String panelId, FaultType faultType, Severity severity,
                                          LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder("SELECT * FROM fault_reports WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (panelId != null && !panelId.trim().isEmpty() && !panelId.equalsIgnoreCase("ALL")) {
            sql.append(" AND panel_id = ?");
            params.add(panelId.trim());
        }
        if (faultType != null) {
            sql.append(" AND fault_type = ?");
            params.add(faultType.name());
        }
        if (severity != null) {
            sql.append(" AND severity = ?");
            params.add(severity.name());
        }
        if (fromDate != null) {
            sql.append(" AND created_at >= ?");
            params.add(fromDate.toString() + "T00:00:00");
        }
        if (toDate != null) {
            sql.append(" AND created_at <= ?");
            params.add(toDate.toString() + "T23:59:59");
        }

        sql.append(" ORDER BY created_at DESC");

        List<FaultReport> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToReport(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[FaultReportRepository] Error querying filtered reports: " + e.getMessage());
        }
        return list;
    }

    public Map<FaultType, Integer> getFaultDistribution() {
        Map<FaultType, Integer> map = new LinkedHashMap<>();
        for (FaultType ft : FaultType.values()) {
            map.put(ft, 0);
        }

        String sql = "SELECT fault_type, COUNT(*) as cnt FROM fault_reports GROUP BY fault_type";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                FaultType ft = FaultType.fromString(rs.getString("fault_type"));
                map.put(ft, rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            System.err.println("[FaultReportRepository] Error getting fault distribution: " + e.getMessage());
        }
        return map;
    }

    public Map<Severity, Integer> getSeverityDistribution() {
        Map<Severity, Integer> map = new LinkedHashMap<>();
        for (Severity s : Severity.values()) {
            map.put(s, 0);
        }

        String sql = "SELECT severity, COUNT(*) as cnt FROM fault_reports GROUP BY severity";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Severity s = Severity.fromString(rs.getString("severity"));
                map.put(s, rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            System.err.println("[FaultReportRepository] Error getting severity distribution: " + e.getMessage());
        }
        return map;
    }

    public Map<String, Integer> getFaultTrend() {
        Map<String, Integer> map = new LinkedHashMap<>();
        // Group by date YYYY-MM-DD
        String sql = """
            SELECT substr(created_at, 1, 10) as report_date, COUNT(*) as cnt 
            FROM fault_reports 
            GROUP BY report_date 
            ORDER BY report_date ASC 
            LIMIT 14
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                map.put(rs.getString("report_date"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            System.err.println("[FaultReportRepository] Error getting fault trend: " + e.getMessage());
        }
        return map;
    }

    public int countTotal() {
        String sql = "SELECT COUNT(*) FROM fault_reports";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[FaultReportRepository] Error counting reports: " + e.getMessage());
        }
        return 0;
    }

    private void setNullableDouble(PreparedStatement ps, int paramIndex, Double value) throws SQLException {
        if (value != null) {
            ps.setDouble(paramIndex, value);
        } else {
            ps.setNull(paramIndex, Types.REAL);
        }
    }

    private FaultReport mapResultSetToReport(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String panelId = rs.getString("panel_id");
        FaultType fault = FaultType.fromString(rs.getString("fault_type"));
        double confidence = rs.getDouble("confidence");
        Severity severity = Severity.fromString(rs.getString("severity"));
        MaintenancePriority priority = MaintenancePriority.fromString(rs.getString("maintenance_priority"));
        int affX = rs.getInt("affected_x");
        int affY = rs.getInt("affected_y");
        int affW = rs.getInt("affected_width");
        int affH = rs.getInt("affected_height");
        String affLabel = rs.getString("affected_region_label");
        double lossW = rs.getDouble("estimated_energy_loss_watts");
        double lossPct = rs.getDouble("estimated_energy_loss_percent");
        String imgPath = rs.getString("image_path");
        String analyzedImgPath = rs.getString("analyzed_image_path");
        String notes = rs.getString("analysis_notes");

        Double temp = getNullableDouble(rs, "temperature");
        Double volt = getNullableDouble(rs, "voltage");
        Double curr = getNullableDouble(rs, "current");
        Double irr = getNullableDouble(rs, "irradiance");
        Double hum = getNullableDouble(rs, "humidity");

        LocalDateTime created = LocalDateTime.now();
        String cStr = rs.getString("created_at");
        if (cStr != null) {
            try {
                created = LocalDateTime.parse(cStr);
            } catch (Exception ignored) {}
        }

        return new FaultReport(id, panelId, fault, confidence, severity, priority,
                affX, affY, affW, affH, affLabel, lossW, lossPct, imgPath, analyzedImgPath,
                notes, temp, volt, curr, irr, hum, created);
    }

    private Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        double val = rs.getDouble(col);
        return rs.wasNull() ? null : val;
    }
}
