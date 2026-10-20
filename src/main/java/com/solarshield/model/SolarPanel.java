package com.solarshield.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Model class representing a photovoltaic solar panel in the monitored farm array.
 * Demonstrates encapsulation and object-oriented representation of domain entities.
 */
public class SolarPanel {
    private int id;
    private String panelId;
    private String name;
    private String location;
    private LocalDate installationDate;
    private String panelType;
    private double ratedPower; // in Watts (W)
    private double latitude;
    private double longitude;
    private PanelStatus status;
    private LocalDateTime lastInspectionDate;
    private FaultType currentFault;
    private Severity severity;
    private MaintenancePriority maintenancePriority;
    private LocalDateTime createdAt;

    public SolarPanel() {
        this.status = PanelStatus.HEALTHY;
        this.currentFault = FaultType.HEALTHY;
        this.severity = Severity.LOW;
        this.maintenancePriority = MaintenancePriority.LOW;
        this.ratedPower = 400.0;
        this.installationDate = LocalDate.now().minusYears(1);
        this.createdAt = LocalDateTime.now();
    }

    public SolarPanel(int id, String panelId, String name, String location, LocalDate installationDate,
                      String panelType, double ratedPower, double latitude, double longitude,
                      PanelStatus status, LocalDateTime lastInspectionDate, FaultType currentFault,
                      Severity severity, MaintenancePriority maintenancePriority, LocalDateTime createdAt) {
        this.id = id;
        this.panelId = panelId;
        this.name = name;
        this.location = location;
        this.installationDate = installationDate != null ? installationDate : LocalDate.now();
        this.panelType = panelType != null ? panelType : "Monocrystalline";
        this.ratedPower = ratedPower > 0 ? ratedPower : 400.0;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status != null ? status : PanelStatus.HEALTHY;
        this.lastInspectionDate = lastInspectionDate;
        this.currentFault = currentFault != null ? currentFault : FaultType.HEALTHY;
        this.severity = severity != null ? severity : Severity.LOW;
        this.maintenancePriority = maintenancePriority != null ? maintenancePriority : MaintenancePriority.LOW;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getInstallationDate() {
        return installationDate;
    }

    public void setInstallationDate(LocalDate installationDate) {
        this.installationDate = installationDate;
    }

    public String getPanelType() {
        return panelType;
    }

    public void setPanelType(String panelType) {
        this.panelType = panelType;
    }

    public double getRatedPower() {
        return ratedPower;
    }

    public void setRatedPower(double ratedPower) {
        this.ratedPower = ratedPower;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public PanelStatus getStatus() {
        return status;
    }

    public void setStatus(PanelStatus status) {
        this.status = status;
    }

    public LocalDateTime getLastInspectionDate() {
        return lastInspectionDate;
    }

    public void setLastInspectionDate(LocalDateTime lastInspectionDate) {
        this.lastInspectionDate = lastInspectionDate;
    }

    public FaultType getCurrentFault() {
        return currentFault;
    }

    public void setCurrentFault(FaultType currentFault) {
        this.currentFault = currentFault;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return panelId + " - " + name + " (" + location + ")";
    }
}
