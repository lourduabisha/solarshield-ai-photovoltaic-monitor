package com.solarshield.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Model class representing a maintenance work order or service task.
 */
public class Maintenance {
    private int id;
    private String panelId;
    private Integer faultReportId;
    private String technician;
    private String maintenanceType;
    private LocalDate scheduledDate;
    private MaintenancePriority priority;
    private MaintenanceStatus status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public Maintenance() {
        this.scheduledDate = LocalDate.now().plusDays(2);
        this.priority = MaintenancePriority.MEDIUM;
        this.status = MaintenanceStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Maintenance(int id, String panelId, Integer faultReportId, String technician,
                       String maintenanceType, LocalDate scheduledDate, MaintenancePriority priority,
                       MaintenanceStatus status, String notes, LocalDateTime createdAt, LocalDateTime completedAt) {
        this.id = id;
        this.panelId = panelId;
        this.faultReportId = faultReportId;
        this.technician = technician != null ? technician : "Unassigned";
        this.maintenanceType = maintenanceType != null ? maintenanceType : "Inspection & Diagnostic";
        this.scheduledDate = scheduledDate != null ? scheduledDate : LocalDate.now().plusDays(2);
        this.priority = priority != null ? priority : MaintenancePriority.MEDIUM;
        this.status = status != null ? status : MaintenanceStatus.PENDING;
        this.notes = notes;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.completedAt = completedAt;
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

    public Integer getFaultReportId() {
        return faultReportId;
    }

    public void setFaultReportId(Integer faultReportId) {
        this.faultReportId = faultReportId;
    }

    public String getTechnician() {
        return technician;
    }

    public void setTechnician(String technician) {
        this.technician = technician;
    }

    public String getMaintenanceType() {
        return maintenanceType;
    }

    public void setMaintenanceType(String maintenanceType) {
        this.maintenanceType = maintenanceType;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public MaintenancePriority getPriority() {
        return priority;
    }

    public void setPriority(MaintenancePriority priority) {
        this.priority = priority;
    }

    public MaintenanceStatus getStatus() {
        return status;
    }

    public void setStatus(MaintenanceStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public String toString() {
        return String.format("Task #%d [%s - %s - %s]", id, panelId, maintenanceType, status);
    }
}
