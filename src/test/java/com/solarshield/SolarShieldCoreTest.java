package com.solarshield;

import com.solarshield.ai.MockAIModel;
import com.solarshield.ai.ModelPrediction;
import com.solarshield.database.*;
import com.solarshield.model.*;
import com.solarshield.service.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite verifying core application logic:
 * - Authentication & BCrypt hashing
 * - Fault classification & AI mock fallback
 * - Severity calculation
 * - Energy loss estimation
 * - Database CRUD operations
 */
public class SolarShieldCoreTest {

    @BeforeAll
    public static void setupDatabase() {
        DatabaseInitializer.initialize();
        com.solarshield.util.OpenCVLoader.load();
    }

    @Test
    public void testPasswordHashing() {
        String rawPassword = "securePassword123";
        String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));

        assertNotNull(hash);
        assertNotEquals(rawPassword, hash);
        assertTrue(BCrypt.checkpw(rawPassword, hash));
        assertFalse(BCrypt.checkpw("wrongPassword", hash));
    }

    @Test
    public void testUserAuthenticationService() {
        UserRepository repo = new UserRepository();
        AuthenticationService auth = new AuthenticationService(repo);

        String testEmail = "testuser_" + System.currentTimeMillis() + "@solarshield.test";
        AuthenticationService.AuthResult regResult = auth.register(
                "Jane Doe", testEmail, "solarPass123", "solarPass123"
        );
        assertTrue(regResult.isSuccess(), "User registration should succeed");

        // Duplicate registration should fail
        AuthenticationService.AuthResult dupResult = auth.register(
                "Jane Doe", testEmail, "solarPass123", "solarPass123"
        );
        assertFalse(dupResult.isSuccess(), "Duplicate email should be rejected");

        // Valid login
        AuthenticationService.AuthResult loginResult = auth.login(testEmail, "solarPass123");
        assertTrue(loginResult.isSuccess(), "Login with valid credentials must succeed");
        assertEquals("Jane Doe", loginResult.getUser().getName());

        // Invalid password login
        AuthenticationService.AuthResult badLogin = auth.login(testEmail, "wrongPass");
        assertFalse(badLogin.isSuccess(), "Login with wrong password must fail");
    }

    @Test
    public void testMockAIModelPrediction() {
        MockAIModel model = new MockAIModel();
        SensorData sensor = new SensorData(65.0, 32.0, 6.0, 900.0, 30.0);

        ModelPrediction pred = model.predict(null, sensor);
        assertNotNull(pred);
        assertTrue(pred.isDemoFallback(), "Mock model must be flagged as demo fallback");
        assertEquals(FaultType.HOTSPOT, pred.getFaultType(), "High temperature should simulate hotspot fault");
        assertTrue(pred.getConfidence() > 80.0);
    }

    @Test
    public void testSeverityService() {
        SeverityService severityService = new SeverityService();

        // Healthy panel should be LOW
        Severity sHealthy = severityService.evaluateSeverity(FaultType.HEALTHY, 99.0, null, null, 400.0);
        assertEquals(Severity.LOW, sHealthy);

        // Physical damage should be CRITICAL
        Severity sDamage = severityService.evaluateSeverity(FaultType.PHYSICAL_DAMAGE, 95.0, null, null, 400.0);
        assertEquals(Severity.CRITICAL, sDamage);

        // Extreme temperature should escalate to CRITICAL
        SensorData hotSensor = new SensorData(72.0, 30.0, 5.0, 800.0, 25.0);
        Severity sHotspot = severityService.evaluateSeverity(FaultType.HOTSPOT, 90.0, null, hotSensor, 400.0);
        assertEquals(Severity.CRITICAL, sHotspot);
    }

    @Test
    public void testEnergyLossService() {
        EnergyLossService lossService = new EnergyLossService();
        double ratedPower = 400.0;

        EnergyLossService.EnergyLossEstimate estimate = lossService.estimateLoss(
                ratedPower, FaultType.HOTSPOT, Severity.HIGH, null
        );

        assertTrue(estimate.getLossWatts() > 0, "Loss in Watts should be positive");
        assertTrue(estimate.getLossPercent() > 0, "Loss percentage should be positive");
        assertTrue(estimate.getLossWatts() <= ratedPower, "Loss cannot exceed rated power");

        MaintenancePriority priority = lossService.determineMaintenancePriority(
                Severity.CRITICAL, FaultType.HOTSPOT, 45.0
        );
        assertEquals(MaintenancePriority.URGENT, priority);
    }

    @Test
    public void testSolarPanelRepositoryCrud() {
        SolarPanelRepository repo = new SolarPanelRepository();
        String testId = "TEST-" + System.currentTimeMillis();

        SolarPanel panel = new SolarPanel(
                0, testId, "Test Array Unit", "Zone Test", LocalDate.now(),
                "Monocrystalline", 450.0, 34.0, -118.0, PanelStatus.HEALTHY,
                null, FaultType.HEALTHY, Severity.LOW, MaintenancePriority.LOW, LocalDateTime.now()
        );

        boolean saved = repo.save(panel);
        assertTrue(saved, "Panel must be saved to database");

        Optional<SolarPanel> retrieved = repo.findByPanelId(testId);
        assertTrue(retrieved.isPresent(), "Retrieved panel must exist");
        assertEquals("Test Array Unit", retrieved.get().getName());
        assertEquals(450.0, retrieved.get().getRatedPower());

        // Update status
        repo.updateStatusAndFault(testId, PanelStatus.FAULTY, FaultType.HOTSPOT, Severity.HIGH, MaintenancePriority.HIGH, LocalDateTime.now());
        Optional<SolarPanel> updated = repo.findByPanelId(testId);
        assertTrue(updated.isPresent());
        assertEquals(PanelStatus.FAULTY, updated.get().getStatus());
        assertEquals(FaultType.HOTSPOT, updated.get().getCurrentFault());
    }

    @Test
    public void testSampleImageGeneration() {
        com.solarshield.util.SampleImageGenerator.generateAllSamples();
        java.io.File dir = new java.io.File("sample_images");
        assertTrue(dir.exists());
        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        assertNotNull(files);
        assertTrue(files.length >= 6, "Must generate at least 6 sample images for demonstration");
    }

    @Test
    public void testBatchDetectionItem() {
        java.io.File testFile = new java.io.File("sample_images/1_healthy_panel.jpg");
        SolarPanel panel = new SolarPanel(
                1, "SP-BATCH-01", "Array Alpha", "Sector 1", LocalDate.now(),
                "Monocrystalline", 400.0, 0.0, 0.0, PanelStatus.HEALTHY,
                null, FaultType.HEALTHY, Severity.LOW, MaintenancePriority.LOW, null
        );

        BatchDetectionItem item = new BatchDetectionItem(testFile, panel);
        assertEquals("1_healthy_panel.jpg", item.getFileName());
        assertEquals("SP-BATCH-01", item.getPanelId());
        assertEquals("Pending", item.getFaultType());
        assertEquals("Ready", item.getStatus());
        assertFalse(item.isSaved());

        // Simulate attaching a detection result
        BoundingBox bbox = new BoundingBox(10, 10, 50, 50, "Upper Left");
        ModelPrediction pred = new ModelPrediction(
                FaultType.HOTSPOT, 92.5, Severity.HIGH, MaintenancePriority.HIGH,
                bbox, false, "TestModel", "AI Analysis"
        );
        EnergyLossService.EnergyLossEstimate loss = new EnergyLossService.EnergyLossEstimate(60.0, 15.0, "Localized thermal dissipation derating");
        LocalizationService.LocalizationResult loc =
                new LocalizationService.LocalizationResult(null, null, bbox, "Sector Top");

        FaultDetectionService.DetectionResult res =
                new FaultDetectionService.DetectionResult(
                        true, null, panel, pred, Severity.HIGH, MaintenancePriority.HIGH,
                        loss, loc, testFile.getAbsolutePath(), null, null, "Inspect bypass diodes", null
                );

        item.setDetectionResult(res);
        assertEquals("Hotspot", item.getFaultType());
        assertEquals("92.5%", item.getConfidence());
        assertEquals("HIGH", item.getSeverity());
        assertEquals("Analyzed", item.getStatus());
        item.setProcessingDurationMs(150);
        item.setMissionTag("Flight-Survey-01");
        assertEquals("Flight-Survey-01", item.getMissionTag());
        assertEquals(150, item.getProcessingDurationMs());
        assertTrue(item.isFault());
        assertTrue(item.isCriticalOrHigh());

        item.setSaved(true);
        assertTrue(item.isSaved());
        assertEquals("Saved (DB)", item.getStatus());
    }

    @Test
    public void testDroneBatchInspectionPdfExport() {
        ReportService reportService = new ReportService();

        java.io.File testFile = new java.io.File("sample_images/2_hotspot_panel.jpg");
        SolarPanel panel = new SolarPanel(
                1, "SP-DRONE-01", "Array Gamma", "Sector 3", LocalDate.now(),
                "Monocrystalline", 400.0, 0.0, 0.0, PanelStatus.FAULTY,
                null, FaultType.HOTSPOT, Severity.HIGH, MaintenancePriority.HIGH, null
        );

        BatchDetectionItem item1 = new BatchDetectionItem(testFile, panel, "Drone-Mission-Alpha");
        BoundingBox bbox = new BoundingBox(20, 20, 80, 80, "Center");
        ModelPrediction pred = new ModelPrediction(
                FaultType.HOTSPOT, 95.0, Severity.HIGH, MaintenancePriority.HIGH,
                bbox, false, "TestModel", "Hotspot anomaly detected"
        );
        EnergyLossService.EnergyLossEstimate loss = new EnergyLossService.EnergyLossEstimate(75.0, 18.75, "Thermal loss");
        LocalizationService.LocalizationResult loc = new LocalizationService.LocalizationResult(null, null, bbox, "Center");
        FaultDetectionService.DetectionResult res = new FaultDetectionService.DetectionResult(
                true, null, panel, pred, Severity.HIGH, MaintenancePriority.HIGH,
                loss, loc, testFile.getAbsolutePath(), null, null, "Inspect bypass diodes", null
        );
        item1.setDetectionResult(res);

        java.io.File healthyFile = new java.io.File("sample_images/1_healthy_panel.jpg");
        BatchDetectionItem item2 = new BatchDetectionItem(healthyFile, panel, "Drone-Mission-Alpha");
        ModelPrediction predHealthy = new ModelPrediction(
                FaultType.HEALTHY, 98.0, Severity.LOW, MaintenancePriority.LOW,
                null, false, "TestModel", "Normal operation"
        );
        FaultDetectionService.DetectionResult resHealthy = new FaultDetectionService.DetectionResult(
                true, null, panel, predHealthy, Severity.LOW, MaintenancePriority.LOW,
                new EnergyLossService.EnergyLossEstimate(0.0, 0.0, "Nominal"), null,
                healthyFile.getAbsolutePath(), null, null, "Nominal", null
        );
        item2.setDetectionResult(resHealthy);

        java.io.File targetPdf = new java.io.File("target/test_drone_batch_report.pdf");
        boolean exported = reportService.exportBatchInspectionPdf(
                java.util.List.of(item1, item2),
                "Flight-Survey-Mission-Alpha",
                targetPdf
        );

        assertTrue(exported, "Drone batch audit PDF report export must succeed");
        assertTrue(targetPdf.exists() && targetPdf.length() > 0, "PDF file must exist and have non-zero size");
    }

    @Test
    public void testThermalColormapAndMultiSpectralEdgeEnhancement() {
        ImagePreprocessingService prepService = new ImagePreprocessingService();
        java.io.File testImage = new java.io.File("sample_images/2_hotspot_panel.jpg");
        assertTrue(testImage.exists());

        ImagePreprocessingService.PreprocessResult prep = prepService.preprocessImageFile(testImage);
        assertTrue(prep.isSuccess());
        assertNotNull(prep.getProcessedMat());

        // Test false-color thermal palettes and edge enhancements
        for (ThermalColormap colormap : ThermalColormap.values()) {
            org.opencv.core.Mat thermal = prepService.applyThermalColormap(prep.getProcessedMat(), colormap);
            assertNotNull(thermal, "Thermal transformation must succeed for palette: " + colormap.getDisplayName());
            assertFalse(thermal.empty(), "Thermal matrix must not be empty for palette: " + colormap.getDisplayName());
            assertEquals(prep.getProcessedMat().rows(), thermal.rows());
            assertEquals(prep.getProcessedMat().cols(), thermal.cols());
            thermal.release();
        }
    }
}

