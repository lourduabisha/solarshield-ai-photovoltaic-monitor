package com.solarshield.ai;

import com.solarshield.model.BoundingBox;
import com.solarshield.model.FaultType;
import com.solarshield.model.MaintenancePriority;
import com.solarshield.model.Severity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model class encapsulating the diagnostic output of the AI inference engine.
 * Demonstrates encapsulation and abstraction of machine learning / computer vision results.
 */
public class ModelPrediction {
    private FaultType faultType;
    private double confidence; // 0.0 to 100.0%
    private Severity severity;
    private MaintenancePriority maintenancePriority;
    private BoundingBox boundingBox;
    private boolean demoFallback;
    private String modelName;
    private String analysisNotes;
    private Map<String, Object> featureMetrics;

    public ModelPrediction() {
        this.faultType = FaultType.HEALTHY;
        this.confidence = 98.0;
        this.severity = Severity.LOW;
        this.maintenancePriority = MaintenancePriority.LOW;
        this.boundingBox = new BoundingBox(0, 0, 0, 0, "Nominal Cell Array");
        this.demoFallback = false;
        this.modelName = "SolarShield AI";
        this.analysisNotes = "Nominal operation detected.";
        this.featureMetrics = new LinkedHashMap<>();
    }

    public ModelPrediction(FaultType faultType, double confidence, Severity severity,
                           MaintenancePriority maintenancePriority, BoundingBox boundingBox,
                           boolean demoFallback, String modelName, String analysisNotes) {
        this.faultType = faultType != null ? faultType : FaultType.HEALTHY;
        this.confidence = Math.max(0.0, Math.min(100.0, confidence));
        this.severity = severity != null ? severity : Severity.LOW;
        this.maintenancePriority = maintenancePriority != null ? maintenancePriority : MaintenancePriority.LOW;
        this.boundingBox = boundingBox != null ? boundingBox : new BoundingBox();
        this.demoFallback = demoFallback;
        this.modelName = modelName != null ? modelName : "SolarShield AI";
        this.analysisNotes = analysisNotes != null ? analysisNotes : "";
        this.featureMetrics = new LinkedHashMap<>();
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

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
    }

    public boolean isDemoFallback() {
        return demoFallback;
    }

    public void setDemoFallback(boolean demoFallback) {
        this.demoFallback = demoFallback;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelType() {
        return demoFallback ? "DEMO AI / FALLBACK MODEL" : "OpenCV Computer Vision Engine";
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getAnalysisNotes() {
        return analysisNotes;
    }

    public void setAnalysisNotes(String analysisNotes) {
        this.analysisNotes = analysisNotes;
    }

    public Map<String, Object> getFeatureMetrics() {
        return featureMetrics;
    }

    public void addMetric(String key, Object val) {
        this.featureMetrics.put(key, val);
    }

    @Override
    public String toString() {
        return String.format("[%s] Fault: %s, Conf: %.1f%%, Sev: %s, Box: %s (Demo: %s)",
                modelName, faultType, confidence, severity, boundingBox, demoFallback);
    }
}
