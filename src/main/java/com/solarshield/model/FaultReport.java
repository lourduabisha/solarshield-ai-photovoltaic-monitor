package com.solarshield.model;

import java.time.LocalDateTime;

/**
 * Model class encapsulating a complete AI inspection and fault detection diagnostic report.
 */
public class FaultReport {
    private int id;
    private String panelId;
    private FaultType faultType;
    private double confidence; // e.g. 94.3%
    private Severity severity;
    private MaintenancePriority maintenancePriority;
    private int affectedX;
    private int affectedY;
    private int affectedWidth;
    private int affectedHeight;
    private String affectedRegionLabel;
    private double estimatedEnergyLossWatts;
    private double estimatedEnergyLossPercent;
    private String imagePath;
    private String analyzedImagePath;
    private String analysisNotes;
    private Double temperature;
    private Double voltage;
    private Double current;
    private Double irradiance;
    private Double humidity;
    private LocalDateTime createdAt;

    public FaultReport() {
        this.faultType = FaultType.HEALTHY;
        this.confidence = 99.0;
        this.severity = Severity.LOW;
        this.maintenancePriority = MaintenancePriority.LOW;
        this.affectedRegionLabel = "None";
        this.createdAt = LocalDateTime.now();
    }

    public FaultReport(int id, String panelId, FaultType faultType, double confidence,
                       Severity severity, MaintenancePriority maintenancePriority,
                       int affectedX, int affectedY, int affectedWidth, int affectedHeight,
                       String affectedRegionLabel, double estimatedEnergyLossWatts,
                       double estimatedEnergyLossPercent, String imagePath, String analyzedImagePath,
                       String analysisNotes, Double temperature, Double voltage, Double current,
                       Double irradiance, Double humidity, LocalDateTime createdAt) {
        this.id = id;
        this.panelId = panelId;
        this.faultType = faultType != null ? faultType : FaultType.HEALTHY;
        this.confidence = confidence;
        this.severity = severity != null ? severity : Severity.LOW;
        this.maintenancePriority = maintenancePriority != null ? maintenancePriority : MaintenancePriority.LOW;
        this.affectedX = affectedX;
        this.affectedY = affectedY;
        this.affectedWidth = affectedWidth;
        this.affectedHeight = affectedHeight;
        this.affectedRegionLabel = affectedRegionLabel != null ? affectedRegionLabel : "Detected region";
        this.estimatedEnergyLossWatts = estimatedEnergyLossWatts;
        this.estimatedEnergyLossPercent = estimatedEnergyLossPercent;
        this.imagePath = imagePath;
        this.analyzedImagePath = analyzedImagePath;
        this.analysisNotes = analysisNotes;
        this.temperature = temperature;
        this.voltage = voltage;
        this.current = current;
        this.irradiance = irradiance;
        this.humidity = humidity;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public BoundingBox getBoundingBox() {
        return new BoundingBox(affectedX, affectedY, affectedWidth, affectedHeight, affectedRegionLabel);
    }

    public void setBoundingBox(BoundingBox box) {
        if (box != null) {
            this.affectedX = box.getX();
            this.affectedY = box.getY();
            this.affectedWidth = box.getWidth();
            this.affectedHeight = box.getHeight();
            this.affectedRegionLabel = box.getRegionLabel();
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPanelId() {
        return panelId;
    }

    public void setPanelId(String panelId) {
        this.panelId = panelId;
    }

    public FaultType getFaultType() {
        return faultType;
    }

    public void setFaultType(FaultType faultType) {
        this.faultType = faultType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public MaintenancePriority getMaintenancePriority() {
        return maintenancePriority;
    }

    public void setMaintenancePriority(MaintenancePriority maintenancePriority) {
        this.maintenancePriority = maintenancePriority;
    }

    public int getAffectedX() {
        return affectedX;
    }

    public void setAffectedX(int affectedX) {
        this.affectedX = affectedX;
    }

    public int getAffectedY() {
        return affectedY;
    }

    public void setAffectedY(int affectedY) {
        this.affectedY = affectedY;
    }

    public int getAffectedWidth() {
        return affectedWidth;
    }

    public void setAffectedWidth(int affectedWidth) {
        this.affectedWidth = affectedWidth;
    }

    public int getAffectedHeight() {
        return affectedHeight;
    }

    public void setAffectedHeight(int affectedHeight) {
        this.affectedHeight = affectedHeight;
    }

    public String getAffectedRegionLabel() {
        return affectedRegionLabel;
    }

    public void setAffectedRegionLabel(String affectedRegionLabel) {
        this.affectedRegionLabel = affectedRegionLabel;
    }

    public double getEstimatedEnergyLossWatts() {
        return estimatedEnergyLossWatts;
    }

    public void setEstimatedEnergyLossWatts(double estimatedEnergyLossWatts) {
        this.estimatedEnergyLossWatts = estimatedEnergyLossWatts;
    }

    public double getEstimatedEnergyLossPercent() {
        return estimatedEnergyLossPercent;
    }

    public void setEstimatedEnergyLossPercent(double estimatedEnergyLossPercent) {
        this.estimatedEnergyLossPercent = estimatedEnergyLossPercent;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getAnalyzedImagePath() {
        return analyzedImagePath;
    }

    public void setAnalyzedImagePath(String analyzedImagePath) {
        this.analyzedImagePath = analyzedImagePath;
    }

    public String getAnalysisNotes() {
        return analysisNotes;
    }

    public void setAnalysisNotes(String analysisNotes) {
        this.analysisNotes = analysisNotes;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getVoltage() {
        return voltage;
    }

    public void setVoltage(Double voltage) {
        this.voltage = voltage;
    }

    public Double getCurrent() {
        return current;
    }

    public void setCurrent(Double current) {
        this.current = current;
    }

    public Double getIrradiance() {
        return irradiance;
    }

    public void setIrradiance(Double irradiance) {
        this.irradiance = irradiance;
    }

    public Double getHumidity() {
        return humidity;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return String.format("Report #%d [%s - %s (%.1f%%) %s]", id, panelId, faultType, confidence, severity);
    }
}
