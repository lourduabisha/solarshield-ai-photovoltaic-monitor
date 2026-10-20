package com.solarshield.service;

import com.solarshield.database.FaultReportRepository;
import com.solarshield.database.MaintenanceRepository;
import com.solarshield.database.SolarPanelRepository;
import com.solarshield.model.AlertItem;
import com.solarshield.model.FaultType;
import com.solarshield.model.Severity;

import java.util.List;
import java.util.Map;

/**
 * Service aggregating farm-level KPIs, defect trends, and system status for the main dashboard.
 */
public class DashboardService {

    private final SolarPanelRepository panelRepository;
    private final FaultReportRepository reportRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final AlertService alertService;

    public DashboardService() {
        this.panelRepository = new SolarPanelRepository();
        this.reportRepository = new FaultReportRepository();
        this.maintenanceRepository = new MaintenanceRepository();
        this.alertService = new AlertService();
    }

    public static class DashboardStats {
        private final int totalPanels;
        private final int healthyPanels;
        private final int faultyPanels;
        private final int criticalFaults;
        private final int pendingMaintenance;
        private final int totalInspections;
        private final int activeAlerts;

        public DashboardStats(int totalPanels, int healthyPanels, int faultyPanels,
                              int criticalFaults, int pendingMaintenance,
                              int totalInspections, int activeAlerts) {
            this.totalPanels = totalPanels;
            this.healthyPanels = healthyPanels;
            this.faultyPanels = faultyPanels;
            this.criticalFaults = criticalFaults;
            this.pendingMaintenance = pendingMaintenance;
            this.totalInspections = totalInspections;
            this.activeAlerts = activeAlerts;
        }

        public int getTotalPanels() { return totalPanels; }
        public int getHealthyPanels() { return healthyPanels; }
        public int getFaultyPanels() { return faultyPanels; }
        public int getCriticalFaults() { return criticalFaults; }
        public int getPendingMaintenance() { return pendingMaintenance; }
        public int getTotalInspections() { return totalInspections; }
        public int getActiveAlerts() { return activeAlerts; }
    }

    public DashboardStats getStats() {
        int total = panelRepository.countTotal();
        int healthy = panelRepository.countHealthy();
        int faulty = panelRepository.countFaulty();
        int critical = panelRepository.countCritical();
        int pendingMaint = maintenanceRepository.countPending();
        int totalReports = reportRepository.countTotal();
        int activeAlerts = alertService.getActiveAlertCount();

        return new DashboardStats(total, healthy, faulty, critical, pendingMaint, totalReports, activeAlerts);
    }

    public Map<FaultType, Integer> getFaultDistribution() {
        return reportRepository.getFaultDistribution();
    }

    public Map<Severity, Integer> getSeverityDistribution() {
        return reportRepository.getSeverityDistribution();
    }

    public Map<String, Integer> getFaultTrend() {
        return reportRepository.getFaultTrend();
    }

    public List<AlertItem> getActiveAlerts() {
        return alertService.getActiveAlerts();
    }
}
