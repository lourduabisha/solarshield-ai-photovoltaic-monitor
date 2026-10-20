package com.solarshield.model;

import com.solarshield.service.FaultDetectionService;
import javafx.beans.property.*;
import javafx.scene.image.Image;

import java.io.File;

/**
 * Data model for a single image item processed in batch multi-panel inspection mode.
 */
public class BatchDetectionItem {

    private final File imageFile;
    private final StringProperty fileName = new SimpleStringProperty();
    private final StringProperty panelId = new SimpleStringProperty();
    private final StringProperty faultType = new SimpleStringProperty("Pending");
    private final StringProperty confidence = new SimpleStringProperty("-");
    private final StringProperty severity = new SimpleStringProperty("-");
    private final StringProperty powerLoss = new SimpleStringProperty("-");
    private final StringProperty status = new SimpleStringProperty("Ready");

    private final StringProperty missionTag = new SimpleStringProperty("Drone-Survey");
    private SolarPanel panel;
    private FaultDetectionService.DetectionResult detectionResult;
    private FaultReport savedReport;
    private Image thumbnailImage;
    private boolean isSaved = false;
    private long processingDurationMs = 0;
    private java.time.LocalDateTime analyzedAt;

    public BatchDetectionItem(File imageFile, SolarPanel panel) {
        this(imageFile, panel, "Drone-Survey");
    }

    public BatchDetectionItem(File imageFile, SolarPanel panel, String missionTag) {
        this.imageFile = imageFile;
        this.panel = panel;
        this.fileName.set(imageFile.getName());
        this.panelId.set(panel != null ? panel.getPanelId() : "Unassigned");
        this.missionTag.set(missionTag != null ? missionTag : "Drone-Survey");
        try {
            this.thumbnailImage = new Image(imageFile.toURI().toString(), 60, 45, true, true);
        } catch (Exception ignored) {}
    }

    public File getImageFile() { return imageFile; }

    public String getFileName() { return fileName.get(); }
    public StringProperty fileNameProperty() { return fileName; }

    public String getPanelId() { return panelId.get(); }
    public StringProperty panelIdProperty() { return panelId; }
    public void setPanel(SolarPanel panel) {
        this.panel = panel;
        this.panelId.set(panel != null ? panel.getPanelId() : "Unassigned");
    }
    public SolarPanel getPanel() { return panel; }

    public String getMissionTag() { return missionTag.get(); }
    public StringProperty missionTagProperty() { return missionTag; }
    public void setMissionTag(String tag) { this.missionTag.set(tag); }

    public String getFaultType() { return faultType.get(); }
    public StringProperty faultTypeProperty() { return faultType; }

    public String getConfidence() { return confidence.get(); }
    public StringProperty confidenceProperty() { return confidence; }

    public String getSeverity() { return severity.get(); }
    public StringProperty severityProperty() { return severity; }

    public String getPowerLoss() { return powerLoss.get(); }
    public StringProperty powerLossProperty() { return powerLoss; }

    public String getStatus() { return status.get(); }
    public StringProperty statusProperty() { return status; }
    public void setStatus(String val) { this.status.set(val); }

    public long getProcessingDurationMs() { return processingDurationMs; }
    public void setProcessingDurationMs(long ms) { this.processingDurationMs = ms; }

    public java.time.LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(java.time.LocalDateTime dt) { this.analyzedAt = dt; }

    public boolean isFault() {
        return detectionResult != null && detectionResult.isSuccess() &&
                detectionResult.getPrediction().getFaultType().isFault();
    }

    public boolean isCriticalOrHigh() {
        if (detectionResult == null || !detectionResult.isSuccess()) return false;
        Severity sev = detectionResult.getSeverity();
        return sev == Severity.CRITICAL || sev == Severity.HIGH;
    }

    public FaultDetectionService.DetectionResult getDetectionResult() { return detectionResult; }
    public void setDetectionResult(FaultDetectionService.DetectionResult detectionResult) {
        this.detectionResult = detectionResult;
        this.analyzedAt = java.time.LocalDateTime.now();
        if (detectionResult != null && detectionResult.isSuccess()) {
            this.faultType.set(detectionResult.getPrediction().getFaultType().getDisplayName());
            this.confidence.set(String.format("%.1f%%", detectionResult.getPrediction().getConfidence()));
            this.severity.set(detectionResult.getSeverity().name());
            this.powerLoss.set(String.format("%.1f W (%.1f%%)",
                    detectionResult.getEnergyLossEstimate().getLossWatts(),
                    detectionResult.getEnergyLossEstimate().getLossPercent()));
            this.status.set("Analyzed");
        } else if (detectionResult != null) {
            this.faultType.set("Failed");
            this.status.set("Error: " + detectionResult.getErrorMessage());
        }
    }

    public Image getThumbnailImage() { return thumbnailImage; }

    public boolean isSaved() { return isSaved; }
    public void setSaved(boolean saved) {
        this.isSaved = saved;
        if (saved) {
            this.status.set("Saved (DB)");
        }
    }

    public FaultReport getSavedReport() { return savedReport; }
    public void setSavedReport(FaultReport savedReport) { this.savedReport = savedReport; }
}
