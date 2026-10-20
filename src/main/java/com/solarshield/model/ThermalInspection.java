package com.solarshield.model;

import java.time.LocalDateTime;

/**
 * Encapsulates an Infrared Thermography (IRT) inspection record adhering to IEC TS 62446-3.
 * Preserves calibrated temperature metrics, spatial hotspot reticles, and anomaly classifications.
 */
public class ThermalInspection {
    private int id;
    private String panelId;
    private String panelName;
    private String imagePath;
    private String analyzedImagePath;
    private String colormap;
    private boolean isCalibrated;
    private String calibrationSource;
    private Double tMin;
    private Double tMax;
    private Double tAvg;
    private Double tHotspot;
    private Double tNominal;
    private Double deltaT;
    private Double emissivity;
    private Double ambientTemp;
    private String iecClass;
    private Severity severity;
    private String anomalyType;
    private int hotspotX;
    private int hotspotY;
    private int hotspotWidth;
    private int hotspotHeight;
    private String whyFlagged;
    private String recommendedAction;
    private String matrixPath;
    private LocalDateTime createdAt;

    public ThermalInspection() {
        this.colormap = "IRONBOW";
        this.isCalibrated = true;
        this.calibrationSource = "RADIOMETRIC_MATRIX";
        this.emissivity = 0.95;
        this.ambientTemp = 25.0;
        this.severity = Severity.LOW;
        this.createdAt = LocalDateTime.now();
    }

    public ThermalInspection(int id, String panelId, String panelName, String imagePath,
                             String analyzedImagePath, String colormap, boolean isCalibrated,
                             String calibrationSource, Double tMin, Double tMax, Double tAvg,
                             Double tHotspot, Double tNominal, Double deltaT, Double emissivity,
                             Double ambientTemp, String iecClass, Severity severity,
                             String anomalyType, int hotspotX, int hotspotY,
                             int hotspotWidth, int hotspotHeight, String whyFlagged,
                             String recommendedAction, String matrixPath, LocalDateTime createdAt) {
        this.id = id;
        this.panelId = panelId;
        this.panelName = panelName;
        this.imagePath = imagePath;
        this.analyzedImagePath = analyzedImagePath;
        this.colormap = colormap != null ? colormap : "IRONBOW";
        this.isCalibrated = isCalibrated;
        this.calibrationSource = calibrationSource;
        this.tMin = tMin;
        this.tMax = tMax;
        this.tAvg = tAvg;
        this.tHotspot = tHotspot;
        this.tNominal = tNominal;
        this.deltaT = deltaT;
        this.emissivity = emissivity != null ? emissivity : 0.95;
        this.ambientTemp = ambientTemp != null ? ambientTemp : 25.0;
        this.iecClass = iecClass;
        this.severity = severity != null ? severity : Severity.LOW;
        this.anomalyType = anomalyType;
        this.hotspotX = hotspotX;
        this.hotspotY = hotspotY;
        this.hotspotWidth = hotspotWidth;
        this.hotspotHeight = hotspotHeight;
        this.whyFlagged = whyFlagged;
        this.recommendedAction = recommendedAction;
        this.matrixPath = matrixPath;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPanelId() { return panelId; }
    public void setPanelId(String panelId) { this.panelId = panelId; }

    public String getPanelName() { return panelName; }
    public void setPanelName(String panelName) { this.panelName = panelName; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getAnalyzedImagePath() { return analyzedImagePath; }
    public void setAnalyzedImagePath(String analyzedImagePath) { this.analyzedImagePath = analyzedImagePath; }

    public String getColormap() { return colormap; }
    public void setColormap(String colormap) { this.colormap = colormap; }

    public boolean isCalibrated() { return isCalibrated; }
    public void setCalibrated(boolean calibrated) { isCalibrated = calibrated; }

    public String getCalibrationSource() { return calibrationSource; }
    public void setCalibrationSource(String calibrationSource) { this.calibrationSource = calibrationSource; }

    public Double getTMin() { return tMin; }
    public void setTMin(Double tMin) { this.tMin = tMin; }

    public Double getTMax() { return tMax; }
    public void setTMax(Double tMax) { this.tMax = tMax; }

    public Double getTAvg() { return tAvg; }
    public void setTAvg(Double tAvg) { this.tAvg = tAvg; }

    public Double getTHotspot() { return tHotspot; }
    public void setTHotspot(Double tHotspot) { this.tHotspot = tHotspot; }

    public Double getTNominal() { return tNominal; }
    public void setTNominal(Double tNominal) { this.tNominal = tNominal; }

    public Double getDeltaT() { return deltaT; }
    public void setDeltaT(Double deltaT) { this.deltaT = deltaT; }

    public Double getEmissivity() { return emissivity; }
    public void setEmissivity(Double emissivity) { this.emissivity = emissivity; }

    public Double getAmbientTemp() { return ambientTemp; }
    public void setAmbientTemp(Double ambientTemp) { this.ambientTemp = ambientTemp; }

    public String getIecClass() { return iecClass; }
    public void setIecClass(String iecClass) { this.iecClass = iecClass; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getAnomalyType() { return anomalyType; }
    public void setAnomalyType(String anomalyType) { this.anomalyType = anomalyType; }

    public int getHotspotX() { return hotspotX; }
    public void setHotspotX(int hotspotX) { this.hotspotX = hotspotX; }

    public int getHotspotY() { return hotspotY; }
    public void setHotspotY(int hotspotY) { this.hotspotY = hotspotY; }

    public int getHotspotWidth() { return hotspotWidth; }
    public void setHotspotWidth(int hotspotWidth) { this.hotspotWidth = hotspotWidth; }

    public int getHotspotHeight() { return hotspotHeight; }
    public void setHotspotHeight(int hotspotHeight) { this.hotspotHeight = hotspotHeight; }

    public String getWhyFlagged() { return whyFlagged; }
    public void setWhyFlagged(String whyFlagged) { this.whyFlagged = whyFlagged; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }

    public String getMatrixPath() { return matrixPath; }
    public void setMatrixPath(String matrixPath) { this.matrixPath = matrixPath; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
