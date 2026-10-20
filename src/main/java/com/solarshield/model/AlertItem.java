package com.solarshield.model;

import java.time.LocalDateTime;

/**
 * Model class representing an automated critical warning or alert raised by the monitoring system.
 */
public class AlertItem {
    private int id;
    private String panelId;
    private Integer faultReportId;
    private Severity severity;
    private String message;
    private String status; // "ACTIVE", "ACKNOWLEDGED", "RESOLVED"
    private LocalDateTime createdAt;

    public AlertItem() {
        this.status = "ACTIVE";
        this.severity = Severity.HIGH;
        this.createdAt = LocalDateTime.now();
    }

    public AlertItem(int id, String panelId, Integer faultReportId, Severity severity, String message, String status, LocalDateTime createdAt) {
        this.id = id;
        this.panelId = panelId;
        this.faultReportId = faultReportId;
        this.severity = severity != null ? severity : Severity.HIGH;
        this.message = message;
        this.status = status != null ? status : "ACTIVE";
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

    public Integer getFaultReportId() {
        return faultReportId;
    }

    public void setFaultReportId(Integer faultReportId) {
        this.faultReportId = faultReportId;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", severity, panelId, message);
    }
}
