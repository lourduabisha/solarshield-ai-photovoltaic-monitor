package com.solarshield.database;

import com.solarshield.model.Severity;
import com.solarshield.model.ThermalInspection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite Repository for persisting and querying Infrared Thermography (IRT) inspection records.
 */
public class ThermalInspectionRepository {

    public boolean save(ThermalInspection inspection) {
        String sql = """
            INSERT INTO thermal_inspections 
            (panel_id, panel_name, image_path, analyzed_image_path, colormap, 
             is_calibrated, calibration_source, t_min, t_max, t_avg, t_hotspot, 
             t_nominal, delta_t, emissivity, ambient_temp, iec_class, severity, 
             anomaly_type, hotspot_x, hotspot_y, hotspot_w, hotspot_h, 
             why_flagged, recommended_action, matrix_path, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, inspection.getPanelId());
            ps.setString(2, inspection.getPanelName());
            ps.setString(3, inspection.getImagePath());
            ps.setString(4, inspection.getAnalyzedImagePath());
            ps.setString(5, inspection.getColormap());
            ps.setInt(6, inspection.isCalibrated() ? 1 : 0);
            ps.setString(7, inspection.getCalibrationSource());

            setNullableDouble(ps, 8, inspection.getTMin());
            setNullableDouble(ps, 9, inspection.getTMax());
            setNullableDouble(ps, 10, inspection.getTAvg());
            setNullableDouble(ps, 11, inspection.getTHotspot());
            setNullableDouble(ps, 12, inspection.getTNominal());
            setNullableDouble(ps, 13, inspection.getDeltaT());
            setNullableDouble(ps, 14, inspection.getEmissivity());
            setNullableDouble(ps, 15, inspection.getAmbientTemp());

            ps.setString(16, inspection.getIecClass());
            ps.setString(17, inspection.getSeverity() != null ? inspection.getSeverity().name() : Severity.LOW.name());
            ps.setString(18, inspection.getAnomalyType());
            ps.setInt(19, inspection.getHotspotX());
            ps.setInt(20, inspection.getHotspotY());
            ps.setInt(21, inspection.getHotspotWidth());
            ps.setInt(22, inspection.getHotspotHeight());
            ps.setString(23, inspection.getWhyFlagged());
            ps.setString(24, inspection.getRecommendedAction());
            ps.setString(25, inspection.getMatrixPath());
            ps.setString(26, inspection.getCreatedAt() != null ? inspection.getCreatedAt().toString() : LocalDateTime.now().toString());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        inspection.setId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[ThermalInspectionRepository] Save error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public Optional<ThermalInspection> findById(int id) {
        String sql = "SELECT * FROM thermal_inspections WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ThermalInspectionRepository] FindById error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<ThermalInspection> findRecent(int limit) {
        List<ThermalInspection> list = new ArrayList<>();
        String sql = "SELECT * FROM thermal_inspections ORDER BY id DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ThermalInspectionRepository] FindRecent error: " + e.getMessage());
        }
        return list;
    }

    public boolean clearAll() {
        String sql = "DELETE FROM thermal_inspections";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            return true;
        } catch (SQLException e) {
            System.err.println("[ThermalInspectionRepository] ClearAll error: " + e.getMessage());
            return false;
        }
    }

    private ThermalInspection mapRow(ResultSet rs) throws SQLException {
        ThermalInspection t = new ThermalInspection();
        t.setId(rs.getInt("id"));
        t.setPanelId(rs.getString("panel_id"));
        t.setPanelName(rs.getString("panel_name"));
        t.setImagePath(rs.getString("image_path"));
        t.setAnalyzedImagePath(rs.getString("analyzed_image_path"));
        t.setColormap(rs.getString("colormap"));
        t.setCalibrated(rs.getInt("is_calibrated") == 1);
        t.setCalibrationSource(rs.getString("calibration_source"));

        t.setTMin(getNullableDouble(rs, "t_min"));
        t.setTMax(getNullableDouble(rs, "t_max"));
        t.setTAvg(getNullableDouble(rs, "t_avg"));
        t.setTHotspot(getNullableDouble(rs, "t_hotspot"));
        t.setTNominal(getNullableDouble(rs, "t_nominal"));
        t.setDeltaT(getNullableDouble(rs, "delta_t"));
        t.setEmissivity(getNullableDouble(rs, "emissivity"));
        t.setAmbientTemp(getNullableDouble(rs, "ambient_temp"));

        t.setIecClass(rs.getString("iec_class"));
        try {
            t.setSeverity(Severity.valueOf(rs.getString("severity")));
        } catch (Exception e) {
            t.setSeverity(Severity.LOW);
        }
        t.setAnomalyType(rs.getString("anomaly_type"));
        t.setHotspotX(rs.getInt("hotspot_x"));
        t.setHotspotY(rs.getInt("hotspot_y"));
        t.setHotspotWidth(rs.getInt("hotspot_w"));
        t.setHotspotHeight(rs.getInt("hotspot_h"));
        t.setWhyFlagged(rs.getString("why_flagged"));
        t.setRecommendedAction(rs.getString("recommended_action"));
        t.setMatrixPath(rs.getString("matrix_path"));

        String dtStr = rs.getString("created_at");
        if (dtStr != null) {
            try {
                t.setCreatedAt(LocalDateTime.parse(dtStr.replace(" ", "T")));
            } catch (Exception ignored) {}
        }
        return t;
    }

    private void setNullableDouble(PreparedStatement ps, int index, Double val) throws SQLException {
        if (val == null) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, val);
        }
    }

    private Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}
