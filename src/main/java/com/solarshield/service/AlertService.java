package com.solarshield.service;

import com.solarshield.database.AlertRepository;
import com.solarshield.model.*;
import com.solarshield.util.AlertUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing real-time notifications, threshold alerts, and active warning tracking.
 */
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService() {
        this.alertRepository = new AlertRepository();
    }

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Checks detection diagnostics and raises system alert if severity or priority exceeds threshold.
     */
    public AlertItem processFaultAlert(FaultReport report, String actionableGuidance) {
        if (report == null) return null;

        boolean isHighRisk = report.getSeverity() == Severity.HIGH ||
                             report.getSeverity() == Severity.CRITICAL ||
                             report.getMaintenancePriority() == MaintenancePriority.URGENT;

        if (!isHighRisk) {
            return null;
        }

        String message = String.format("⚠ %s: %s detected on panel %s. Priority: %s.",
                report.getSeverity().name(),
                report.getFaultType().getDisplayName(),
                report.getPanelId(),
                report.getMaintenancePriority().name());

        AlertItem alert = new AlertItem(
                0,
                report.getPanelId(),
                report.getId() > 0 ? report.getId() : null,
                report.getSeverity(),
                message,
                "ACTIVE",
                LocalDateTime.now()
        );

        alertRepository.save(alert);

        // Display modal alert
        AlertUtils.showCriticalFaultAlert(
                report.getPanelId(),
                report.getFaultType().getDisplayName(),
                report.getMaintenancePriority().name(),
                actionableGuidance != null ? actionableGuidance : "Immediate field inspection recommended."
        );

        return alert;
    }

    public List<AlertItem> getActiveAlerts() {
        return alertRepository.findActive();
    }

    public List<AlertItem> getAllAlerts() {
        return alertRepository.findAll();
    }

    public boolean acknowledgeAlert(int alertId) {
        return alertRepository.updateStatus(alertId, "ACKNOWLEDGED");
    }

    public boolean resolveAlert(int alertId) {
        return alertRepository.updateStatus(alertId, "RESOLVED");
    }

    public int getActiveAlertCount() {
        return alertRepository.countActive();
    }
}
