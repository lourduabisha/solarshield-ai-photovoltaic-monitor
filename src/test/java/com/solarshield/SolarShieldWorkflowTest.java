package com.solarshield;

import com.solarshield.database.*;
import com.solarshield.model.*;
import com.solarshield.service.*;
import com.solarshield.util.OpenCVLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration workflow test verifying the complete SolarShield operational pipeline:
 * Registration -> Login -> Panel Selection -> Preprocessing -> Inference -> Localization
 * -> Severity & Loss -> DB Persistence -> Alert Trigger -> Maintenance Work Order -> PDF/CSV Export.
 */
public class SolarShieldWorkflowTest {

    @BeforeAll
    public static void setup() {
        DatabaseInitializer.initialize();
        OpenCVLoader.load();
    }

    @Test
    public void testCompleteEndToEndSolarMonitoringWorkflow() {
        // 1. Authentication Service
        UserRepository userRepo = new UserRepository();
        AuthenticationService authService = new AuthenticationService(userRepo);

        String email = "field_engineer_" + System.currentTimeMillis() + "@solarshield.com";
        AuthenticationService.AuthResult reg = authService.register("Marcus Kane", email, "solarpower99", "solarpower99");
        assertTrue(reg.isSuccess(), "Registration must succeed");

        AuthenticationService.AuthResult login = authService.login(email, "solarpower99");
        assertTrue(login.isSuccess(), "Login must succeed");
        assertNotNull(login.getUser());

        // 2. Solar Panel Repository
        SolarPanelRepository panelRepo = new SolarPanelRepository();
        String panelId = "SP-TEST-" + (System.currentTimeMillis() % 10000);
        SolarPanel panel = new SolarPanel(
                0, panelId, "Test Alpha Array", "Sector 7G", LocalDate.now(),
                "Monocrystalline PERC", 400.0, 34.05, -118.25, PanelStatus.HEALTHY,
                null, FaultType.HEALTHY, Severity.LOW, MaintenancePriority.LOW, null
        );
        assertTrue(panelRepo.save(panel), "Panel must be saved to DB");

        // 3. Fault Detection & Inference Workflow
        FaultDetectionService detectionService = new FaultDetectionService();

        File sampleHotspot = new File("sample_images/2_hotspot_panel.jpg");
        assertTrue(sampleHotspot.exists(), "Sample test image must exist");

        SensorData sensorData = new SensorData(64.5, 34.2, 7.8, 920.0, 30.0);
        FaultDetectionService.DetectionResult result = detectionService.analyze(panel, sampleHotspot, sensorData);

        assertTrue(result.isSuccess(), "Analysis pipeline must succeed: " + result.getErrorMessage());
        assertNotNull(result.getPrediction());
        assertNotNull(result.getSeverity());
        assertNotNull(result.getMaintenancePriority());
        assertNotNull(result.getEnergyLossEstimate());
        assertTrue(result.getEnergyLossEstimate().getLossWatts() >= 0);

        // 4. Persistence to SQLite
        FaultReport report = detectionService.saveResult(result);
        assertNotNull(report, "Result must be saved into database");
        assertTrue(report.getId() > 0, "Saved report must have an auto-generated ID");

        // Verify panel status was updated
        SolarPanel updatedPanel = panelRepo.findByPanelId(panelId).orElse(null);
        assertNotNull(updatedPanel);
        assertNotEquals(PanelStatus.HEALTHY, updatedPanel.getStatus(), "Panel with detected defect should transition from HEALTHY");

        // 5. Alert Trigger Verification
        AlertRepository alertRepo = new AlertRepository();
        List<AlertItem> alerts = alertRepo.findActive();
        assertNotNull(alerts);

        // 6. Maintenance Dispatch
        MaintenanceRepository maintRepo = new MaintenanceRepository();
        MaintenanceService maintService = new MaintenanceService(maintRepo, panelRepo);

        boolean scheduled = maintService.createMaintenanceTask(
                panelId, report.getId(), "Sarah Connor (Thermal Specialist)",
                "Bypass Diode Thermal Service", LocalDate.now().plusDays(1),
                MaintenancePriority.HIGH, "Integration test maintenance work order"
        );
        assertTrue(scheduled, "Maintenance work order must be recorded");

        List<Maintenance> tasks = maintService.getFilteredTasks(panelId, null, null);
        assertFalse(tasks.isEmpty(), "Scheduled task must be queryable");
        int taskId = tasks.get(0).getId();

        // Complete maintenance task
        boolean completed = maintService.updateStatus(taskId, MaintenanceStatus.COMPLETED, panelId);
        assertTrue(completed, "Maintenance must be updated to COMPLETED");

        SolarPanel restoredPanel = panelRepo.findByPanelId(panelId).orElse(null);
        assertNotNull(restoredPanel);
        assertEquals(PanelStatus.HEALTHY, restoredPanel.getStatus(), "Completed maintenance must restore panel to HEALTHY");

        // 7. Report Export (PDF and CSV)
        ReportService reportService = new ReportService();
        FaultReportRepository reportRepo = new FaultReportRepository();
        List<FaultReport> allReports = reportRepo.findAll();
        assertFalse(allReports.isEmpty());

        File pdfOut = new File("target/test_audit_report.pdf");
        boolean pdfOk = reportService.exportFaultReportsPdf(allReports, pdfOut);
        assertTrue(pdfOk, "PDF export must succeed");
        assertTrue(pdfOut.exists() && pdfOut.length() > 0, "PDF file must exist and not be empty");

        File csvOut = new File("target/test_audit_report.csv");
        boolean csvOk = reportService.exportFaultReportsCsv(allReports, csvOut);
        assertTrue(csvOk, "CSV export must succeed");
        assertTrue(csvOut.exists() && csvOut.length() > 0, "CSV file must exist and not be empty");

        // 8. Single Report PDF
        File singlePdf = new File("target/test_single_report.pdf");
        boolean singleOk = reportService.exportSingleFaultReportPdf(report, panel, singlePdf);
        assertTrue(singleOk, "Single fault PDF report must succeed");
        assertTrue(singlePdf.exists() && singlePdf.length() > 0);

        // 9. Logout Verification
        authService.logout();
        assertFalse(authService.isLoggedIn(), "Session must be terminated after logout");
    }
}
