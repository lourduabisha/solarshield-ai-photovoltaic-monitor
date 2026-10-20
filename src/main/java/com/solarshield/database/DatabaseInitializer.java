package com.solarshield.database;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Initializes the SQLite database schema and provides initial seeding for default users and sample panels.
 */
public class DatabaseInitializer {

    public static void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Users Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    role TEXT DEFAULT 'ENGINEER',
                    created_at TEXT NOT NULL
                );
            """);

            // 2. Solar Panels Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS solar_panels (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    panel_id TEXT UNIQUE NOT NULL,
                    name TEXT NOT NULL,
                    location TEXT,
                    installation_date TEXT,
                    panel_type TEXT,
                    rated_power REAL DEFAULT 400.0,
                    latitude REAL,
                    longitude REAL,
                    status TEXT DEFAULT 'HEALTHY',
                    last_inspection_date TEXT,
                    current_fault TEXT DEFAULT 'HEALTHY',
                    severity TEXT DEFAULT 'LOW',
                    maintenance_priority TEXT DEFAULT 'LOW',
                    created_at TEXT NOT NULL
                );
            """);

            // 3. Fault Reports Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fault_reports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    panel_id TEXT NOT NULL,
                    fault_type TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    severity TEXT NOT NULL,
                    maintenance_priority TEXT NOT NULL,
                    affected_x INTEGER DEFAULT 0,
                    affected_y INTEGER DEFAULT 0,
                    affected_width INTEGER DEFAULT 0,
                    affected_height INTEGER DEFAULT 0,
                    affected_region_label TEXT,
                    estimated_energy_loss_watts REAL DEFAULT 0.0,
                    estimated_energy_loss_percent REAL DEFAULT 0.0,
                    image_path TEXT,
                    analyzed_image_path TEXT,
                    analysis_notes TEXT,
                    temperature REAL,
                    voltage REAL,
                    current REAL,
                    irradiance REAL,
                    humidity REAL,
                    created_at TEXT NOT NULL
                );
            """);

            // 4. Maintenance Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS maintenance (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    panel_id TEXT NOT NULL,
                    fault_report_id INTEGER,
                    technician TEXT,
                    maintenance_type TEXT NOT NULL,
                    scheduled_date TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    status TEXT NOT NULL,
                    notes TEXT,
                    created_at TEXT NOT NULL,
                    completed_at TEXT
                );
            """);

            // 5. Alerts Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS alerts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    panel_id TEXT NOT NULL,
                    fault_report_id INTEGER,
                    severity TEXT NOT NULL,
                    message TEXT NOT NULL,
                    status TEXT DEFAULT 'ACTIVE',
                    created_at TEXT NOT NULL
                );
            """);

            // 6. Infrared Thermography (IRT) Inspections Table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS thermal_inspections (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    panel_id TEXT NOT NULL,
                    panel_name TEXT,
                    image_path TEXT NOT NULL,
                    analyzed_image_path TEXT,
                    colormap TEXT DEFAULT 'IRONBOW',
                    is_calibrated INTEGER DEFAULT 1,
                    calibration_source TEXT DEFAULT 'RADIOMETRIC_MATRIX',
                    t_min REAL,
                    t_max REAL,
                    t_avg REAL,
                    t_hotspot REAL,
                    t_nominal REAL,
                    delta_t REAL,
                    emissivity REAL DEFAULT 0.95,
                    ambient_temp REAL DEFAULT 25.0,
                    iec_class TEXT,
                    severity TEXT,
                    anomaly_type TEXT,
                    hotspot_x INTEGER,
                    hotspot_y INTEGER,
                    hotspot_w INTEGER,
                    hotspot_h INTEGER,
                    why_flagged TEXT,
                    recommended_action TEXT,
                    matrix_path TEXT,
                    created_at TEXT NOT NULL
                );
            """);

            // Create Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_panels_id ON solar_panels(panel_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faults_panel ON fault_reports(panel_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faults_date ON fault_reports(created_at);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_maintenance_status ON maintenance(status);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_thermal_panel ON thermal_inspections(panel_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_thermal_date ON thermal_inspections(created_at DESC);");

            // Seed default admin user if not present
            seedDefaultUser(conn);

            // Seed sample panels if database is brand new
            seedSamplePanelsIfEmpty(conn);

            System.out.println("[DatabaseInitializer] Database schema initialized successfully.");

        } catch (SQLException e) {
            System.err.println("[DatabaseInitializer] Error initializing database schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void seedDefaultUser(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users;")) {
            if (rs.next() && rs.getInt(1) == 0) {
                String hash = BCrypt.hashpw("admin123", BCrypt.gensalt(10));
                String now = LocalDateTime.now().toString();
                String sql = String.format(
                        "INSERT INTO users (name, email, password_hash, role, created_at) VALUES ('Admin Engineer', 'admin@solarshield.com', '%s', 'ADMIN', '%s');",
                        hash, now
                );
                stmt.execute(sql);
                System.out.println("[DatabaseInitializer] Default user created: admin@solarshield.com (Password: admin123)");
            }
        }
    }

    public static void seedSamplePanelsIfEmpty(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM solar_panels;")) {
            if (rs.next() && rs.getInt(1) == 0) {
                seedSampleData(conn);
            }
        }
    }

    public static void seedSampleData(Connection conn) throws SQLException {
        String now = LocalDateTime.now().toString();
        String today = LocalDate.now().toString();

        try (Statement stmt = conn.createStatement()) {
            // Panels
            stmt.execute(String.format("""
                INSERT OR IGNORE INTO solar_panels 
                (panel_id, name, location, installation_date, panel_type, rated_power, latitude, longitude, status, last_inspection_date, current_fault, severity, maintenance_priority, created_at)
                VALUES 
                ('SP-001', 'Array Alpha 01', 'Zone A - East Sector', '%s', 'Monocrystalline PERC', 420.0, 34.0522, -118.2437, 'HEALTHY', '%s', 'HEALTHY', 'LOW', 'LOW', '%s'),
                ('SP-002', 'Array Alpha 02', 'Zone A - East Sector', '%s', 'Monocrystalline PERC', 420.0, 34.0524, -118.2435, 'WARNING', '%s', 'SOILING', 'MEDIUM', 'MEDIUM', '%s'),
                ('SP-003', 'Array Beta 01', 'Zone B - Central Grid', '%s', 'Polycrystalline High-Yield', 380.0, 34.0530, -118.2440, 'FAULTY', '%s', 'HOTSPOT', 'HIGH', 'HIGH', '%s'),
                ('SP-004', 'Array Beta 02', 'Zone B - Central Grid', '%s', 'Polycrystalline High-Yield', 380.0, 34.0532, -118.2442, 'CRITICAL', '%s', 'PHYSICAL_DAMAGE', 'CRITICAL', 'URGENT', '%s'),
                ('SP-005', 'Array Gamma 01', 'Zone C - West Substation', '%s', 'Bifacial Dual-Glass', 450.0, 34.0540, -118.2450, 'HEALTHY', '%s', 'HEALTHY', 'LOW', 'LOW', '%s');
            """, today, now, now, today, now, now, today, now, now, today, now, now, today, now, now));

            // Demo Historical Fault Reports
            stmt.execute(String.format("""
                INSERT INTO fault_reports
                (panel_id, fault_type, confidence, severity, maintenance_priority, affected_x, affected_y, affected_width, affected_height, affected_region_label, estimated_energy_loss_watts, estimated_energy_loss_percent, image_path, analyzed_image_path, analysis_notes, temperature, voltage, current, irradiance, humidity, created_at)
                VALUES
                ('SP-002', 'SOILING', 89.5, 'MEDIUM', 'MEDIUM', 120, 80, 240, 160, 'Center-Left Grid Area', 42.0, 10.0, '', '', 'DEMO DATA: Accumulated dust film attenuating solar transmittance.', 46.2, 38.4, 8.2, 850.0, 32.0, '%s'),
                ('SP-003', 'HOTSPOT', 94.8, 'HIGH', 'HIGH', 210, 150, 95, 85, 'Upper-Right Cell Block', 87.4, 23.0, '', '', 'DEMO DATA: Localized thermal anomaly; potential defective bypass diode.', 68.5, 34.1, 7.1, 910.0, 28.0, '%s'),
                ('SP-004', 'PHYSICAL_DAMAGE', 97.2, 'CRITICAL', 'URGENT', 60, 40, 380, 290, 'Upper Left Module Quadrant', 266.0, 70.0, '', '', 'DEMO DATA: Severe micro-fracture spidering across front protective glass.', 52.0, 18.2, 3.4, 890.0, 30.0, '%s');
            """, LocalDateTime.now().minusDays(3).toString(),
                 LocalDateTime.now().minusDays(1).toString(),
                 LocalDateTime.now().minusHours(4).toString()));

            // Demo Maintenance Tasks
            stmt.execute(String.format("""
                INSERT INTO maintenance 
                (panel_id, fault_report_id, technician, maintenance_type, scheduled_date, priority, status, notes, created_at)
                VALUES
                ('SP-004', 3, 'Alex Vance (Lead Tech)', 'Module Replacement', '%s', 'URGENT', 'SCHEDULED', 'DEMO DATA: Replacement of shattered front glass module.', '%s'),
                ('SP-003', 2, 'Elena Rostova', 'Thermal Bypass Diode Service', '%s', 'HIGH', 'PENDING', 'DEMO DATA: Bypass diode thermal diagnostic and solder reflow.', '%s'),
                ('SP-002', 1, 'Field Cleaning Crew B', 'Surface De-soiling & Wash', '%s', 'MEDIUM', 'COMPLETED', 'DEMO DATA: Automated wash cycle completed.', '%s');
            """, LocalDate.now().plusDays(1).toString(), now,
                 LocalDate.now().plusDays(2).toString(), now,
                 LocalDate.now().minusDays(1).toString(), now));

            // Demo Alerts
            stmt.execute(String.format("""
                INSERT INTO alerts
                (panel_id, fault_report_id, severity, message, status, created_at)
                VALUES
                ('SP-004', 3, 'CRITICAL', 'CRITICAL: Severe physical glass damage detected on SP-004. Urgent module replacement advised.', 'ACTIVE', '%s'),
                ('SP-003', 2, 'HIGH', 'HIGH: Thermal hotspot detected on SP-003 cell block. Power loss 23%%.', 'ACTIVE', '%s');
            """, LocalDateTime.now().minusHours(4).toString(),
                 LocalDateTime.now().minusDays(1).toString()));

            System.out.println("[DatabaseInitializer] Sample demo panels and history populated.");
        }
    }
}
