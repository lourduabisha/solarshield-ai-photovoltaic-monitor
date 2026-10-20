package com.solarshield.service;

import com.solarshield.database.MaintenanceRepository;
import com.solarshield.database.SolarPanelRepository;
import com.solarshield.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing maintenance workflows, technician task assignments, and panel status synchronizations.
 */
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final SolarPanelRepository panelRepository;

    public MaintenanceService() {
        this.maintenanceRepository = new MaintenanceRepository();
        this.panelRepository = new SolarPanelRepository();
    }

    public MaintenanceService(MaintenanceRepository maintenanceRepository, SolarPanelRepository panelRepository) {
        this.maintenanceRepository = maintenanceRepository;
        this.panelRepository = panelRepository;
    }

    public boolean createMaintenanceTask(String panelId, Integer faultReportId, String technician,
                                         String maintenanceType, LocalDate scheduledDate,
                                         MaintenancePriority priority, String notes) {
        Maintenance task = new Maintenance(
                0,
                panelId,
                faultReportId,
                technician,
                maintenanceType,
                scheduledDate,
                priority,
                MaintenanceStatus.PENDING,
                notes,
                LocalDateTime.now(),
                null
        );

        boolean saved = maintenanceRepository.save(task);
        if (saved) {
            // Update panel status to reflect maintenance scheduling
            panelRepository.findByPanelId(panelId).ifPresent(panel -> {
                if (panel.getStatus() != PanelStatus.CRITICAL) {
                    panelRepository.updateStatusAndFault(
                            panelId,
                            PanelStatus.MAINTENANCE,
                            panel.getCurrentFault(),
                            panel.getSeverity(),
                            priority,
                            LocalDateTime.now()
                    );
                }
            });
        }
        return saved;
    }

    public boolean updateStatus(int taskId, MaintenanceStatus newStatus, String panelId) {
        boolean updated = maintenanceRepository.updateStatus(taskId, newStatus);
        if (updated && newStatus == MaintenanceStatus.COMPLETED && panelId != null) {
            // Panel restored to healthy after maintenance completion
            panelRepository.updateStatusAndFault(
                    panelId,
                    PanelStatus.HEALTHY,
                    FaultType.HEALTHY,
                    Severity.LOW,
                    MaintenancePriority.LOW,
                    LocalDateTime.now()
            );
        }
        return updated;
    }

    public boolean updateTask(Maintenance task) {
        return maintenanceRepository.update(task);
    }

    public List<Maintenance> getAllTasks() {
        return maintenanceRepository.findAll();
    }

    public List<Maintenance> getFilteredTasks(String panelId, MaintenanceStatus status, MaintenancePriority priority) {
        return maintenanceRepository.findFiltered(panelId, status, priority);
    }

    public int getPendingCount() {
        return maintenanceRepository.countPending();
    }
}
