package com.solarshield.service;

import com.solarshield.ai.AIInferenceService;
import com.solarshield.ai.ModelPrediction;
import com.solarshield.database.FaultReportRepository;
import com.solarshield.database.SolarPanelRepository;
import com.solarshield.model.*;
import com.solarshield.util.ImageUtils;
import org.opencv.core.Mat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * High-level orchestration service tying together image preprocessing,
 * AI/CV inference, localization rendering, severity assessment, energy loss estimation,
 * alert generation, and database persistence.
 */
public class FaultDetectionService {

    private final ImagePreprocessingService preprocessingService;
    private final AIInferenceService aiInferenceService;
    private final LocalizationService localizationService;
    private final SeverityService severityService;
    private final EnergyLossService energyLossService;
    private final AlertService alertService;
    private final FaultReportRepository reportRepository;
    private final SolarPanelRepository panelRepository;

    public FaultDetectionService() {
        this.preprocessingService = new ImagePreprocessingService();
        this.aiInferenceService = new AIInferenceService();
        this.localizationService = new LocalizationService();
        this.severityService = new SeverityService();
        this.energyLossService = new EnergyLossService();
        this.alertService = new AlertService();
        this.reportRepository = new FaultReportRepository();
        this.panelRepository = new SolarPanelRepository();
    }

    public static class DetectionResult {
        private final boolean success;
        private final String errorMessage;
        private final SolarPanel panel;
        private final ModelPrediction prediction;
        private final Severity severity;
        private final MaintenancePriority maintenancePriority;
        private final EnergyLossService.EnergyLossEstimate energyLossEstimate;
        private final LocalizationService.LocalizationResult localizationResult;
        private final String originalImagePath;
        private final Mat originalMat;
        private final Mat analyzedMat;
        private final String actionableRecommendation;
        private final SensorData sensorData;

        public DetectionResult(boolean success, String errorMessage, SolarPanel panel,
                               ModelPrediction prediction, Severity severity,
                               MaintenancePriority maintenancePriority,
                               EnergyLossService.EnergyLossEstimate energyLossEstimate,
                               LocalizationService.LocalizationResult localizationResult,
                               String originalImagePath, Mat originalMat, Mat analyzedMat,
                               String actionableRecommendation, SensorData sensorData) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.panel = panel;
            this.prediction = prediction;
            this.severity = severity;
            this.maintenancePriority = maintenancePriority;
            this.energyLossEstimate = energyLossEstimate;
            this.localizationResult = localizationResult;
            this.originalImagePath = originalImagePath;
            this.originalMat = originalMat;
            this.analyzedMat = analyzedMat;
            this.actionableRecommendation = actionableRecommendation;
            this.sensorData = sensorData;
        }

        public static DetectionResult failure(String error) {
            return new DetectionResult(false, error, null, null, null, null, null, null, null, null, null, null, null);
        }

        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public SolarPanel getPanel() { return panel; }
        public ModelPrediction getPrediction() { return prediction; }
        public Severity getSeverity() { return severity; }
        public MaintenancePriority getMaintenancePriority() { return maintenancePriority; }
        public EnergyLossService.EnergyLossEstimate getEnergyLossEstimate() { return energyLossEstimate; }
        public LocalizationService.LocalizationResult getLocalizationResult() { return localizationResult; }
        public String getOriginalImagePath() { return originalImagePath; }
        public Mat getOriginalMat() { return originalMat; }
        public Mat getAnalyzedMat() { return analyzedMat; }
        public String getActionableRecommendation() { return actionableRecommendation; }
        public SensorData getSensorData() { return sensorData; }
    }

    public DetectionResult analyze(SolarPanel panel, File imageFile, SensorData sensorData) {
        if (panel == null) {
            return DetectionResult.failure("No solar panel selected. Please select a panel before analysis.");
        }
        if (imageFile == null || !imageFile.exists()) {
            return DetectionResult.failure("Invalid image file. Please provide a solar panel photo.");
        }

        // 1. Copy original image to data/images/
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String cleanPanelId = panel.getPanelId().replaceAll("[^A-Za-z0-9]", "");
        String ext = imageFile.getName().substring(imageFile.getName().lastIndexOf('.'));
        File storedOriginal = new File("data/images", String.format("orig_%s_%s%s", cleanPanelId, timestamp, ext));
        try {
            Files.copy(imageFile.toPath(), storedOriginal.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[FaultDetectionService] Failed to copy image: " + e.getMessage());
        }

        // 2. Preprocess image
        ImagePreprocessingService.PreprocessResult prep = preprocessingService.preprocessImageFile(storedOriginal);
        if (!prep.isSuccess()) {
            return DetectionResult.failure(prep.getMessage());
        }

        return analyzePreparedMat(panel, prep.getOriginalMat(), prep.getProcessedMat(), storedOriginal.getAbsolutePath(), sensorData);
    }

    public DetectionResult analyzeMatDirect(SolarPanel panel, Mat capturedMat, SensorData sensorData) {
        if (panel == null) {
            return DetectionResult.failure("No solar panel selected.");
        }
        if (capturedMat == null || capturedMat.empty()) {
            return DetectionResult.failure("Camera capture buffer is empty.");
        }

        // Save raw captured mat to disk
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String cleanPanelId = panel.getPanelId().replaceAll("[^A-Za-z0-9]", "");
        File storedOriginal = new File("data/images", String.format("camera_%s_%s.jpg", cleanPanelId, timestamp));
        ImageUtils.saveMatToFile(capturedMat, storedOriginal);

        ImagePreprocessingService.PreprocessResult prep = preprocessingService.preprocessMat(capturedMat);
        if (!prep.isSuccess()) {
            return DetectionResult.failure(prep.getMessage());
        }

        return analyzePreparedMat(panel, prep.getOriginalMat(), prep.getProcessedMat(), storedOriginal.getAbsolutePath(), sensorData);
    }

    private DetectionResult analyzePreparedMat(SolarPanel panel, Mat originalMat, Mat processedMat, String originalPath, SensorData sensorData) {
        // 3. AI / Computer Vision Inference
        ModelPrediction prediction = aiInferenceService.runInference(processedMat, sensorData);

        // 4. Severity Assessment
        Severity severity = severityService.evaluateSeverity(
                prediction.getFaultType(),
                prediction.getConfidence(),
                prediction.getBoundingBox(),
                sensorData,
                panel.getRatedPower()
        );

        // 5. Energy Loss Estimation
        EnergyLossService.EnergyLossEstimate lossEstimate = energyLossService.estimateLoss(
                panel.getRatedPower(),
                prediction.getFaultType(),
                severity,
                sensorData
        );

        // 6. Maintenance Priority
        MaintenancePriority priority = energyLossService.determineMaintenancePriority(
                severity,
                prediction.getFaultType(),
                lossEstimate.getLossPercent()
        );

        String recommendation = energyLossService.getActionableRecommendation(priority, prediction.getFaultType());

        // 7. Spatial Localization and HUD overlay rendering
        LocalizationService.LocalizationResult locResult = localizationService.localizeAndRender(
                originalMat,
                prediction.getBoundingBox(),
                prediction.getFaultType(),
                prediction.getConfidence(),
                panel.getPanelId()
        );

        return new DetectionResult(
                true, null, panel, prediction, severity, priority, lossEstimate,
                locResult, originalPath, originalMat, locResult.getAnalyzedMat(),
                recommendation, sensorData
        );
    }

    /**
     * Persists the detection result into the database, updates panel status, and triggers alerts.
     */
    public FaultReport saveResult(DetectionResult result) {
        if (result == null || !result.isSuccess()) return null;

        SolarPanel panel = result.getPanel();
        ModelPrediction pred = result.getPrediction();
        BoundingBox box = pred.getBoundingBox();

        FaultReport report = new FaultReport();
        report.setPanelId(panel.getPanelId());
        report.setFaultType(pred.getFaultType());
        report.setConfidence(pred.getConfidence());
        report.setSeverity(result.getSeverity());
        report.setMaintenancePriority(result.getMaintenancePriority());
        report.setBoundingBox(box);
        report.setEstimatedEnergyLossWatts(result.getEnergyLossEstimate().getLossWatts());
        report.setEstimatedEnergyLossPercent(result.getEnergyLossEstimate().getLossPercent());
        report.setImagePath(result.getOriginalImagePath());
        report.setAnalyzedImagePath(result.getLocalizationResult().getAnalyzedImagePath());
        report.setAnalysisNotes(pred.getAnalysisNotes());

        if (result.getSensorData() != null) {
            report.setTemperature(result.getSensorData().getTemperature());
            report.setVoltage(result.getSensorData().getVoltage());
            report.setCurrent(result.getSensorData().getCurrent());
            report.setIrradiance(result.getSensorData().getIrradiance());
            report.setHumidity(result.getSensorData().getHumidity());
        }

        // Save report to database
        boolean saved = reportRepository.save(report);

        // Update solar panel state in database
        PanelStatus newStatus;
        if (pred.getFaultType() == FaultType.HEALTHY) {
            newStatus = PanelStatus.HEALTHY;
        } else if (result.getSeverity() == Severity.CRITICAL) {
            newStatus = PanelStatus.CRITICAL;
        } else if (result.getSeverity() == Severity.HIGH) {
            newStatus = PanelStatus.FAULTY;
        } else {
            newStatus = PanelStatus.WARNING;
        }

        panelRepository.updateStatusAndFault(
                panel.getPanelId(),
                newStatus,
                pred.getFaultType(),
                result.getSeverity(),
                result.getMaintenancePriority(),
                LocalDateTime.now()
        );

        // Trigger alert if high risk
        alertService.processFaultAlert(report, result.getActionableRecommendation());

        return report;
    }

    public AIInferenceService getAiInferenceService() {
        return aiInferenceService;
    }

    public ImagePreprocessingService getPreprocessingService() {
        return preprocessingService;
    }

    public Mat applyThermalFilter(Mat src, com.solarshield.model.ThermalColormap colormap) {
        return preprocessingService.applyThermalColormap(src, colormap);
    }
}
