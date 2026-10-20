package com.solarshield.model;

/**
 * Enumeration representing the workflow status of maintenance operations.
 */
public enum MaintenanceStatus {
    PENDING("Pending", "#F59E0B"),
    SCHEDULED("Scheduled", "#3B82F6"),
    IN_PROGRESS("In Progress", "#8B5CF6"),
    COMPLETED("Completed", "#10B981"),
    CANCELLED("Cancelled", "#6B7280");

    private final String displayName;
    private final String colorHex;

    MaintenanceStatus(String displayName, String colorHex) {
        this.displayName = displayName;
        this.colorHex = colorHex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public static MaintenanceStatus fromString(String text) {
        if (text == null) return PENDING;
        for (MaintenanceStatus s : MaintenanceStatus.values()) {
            if (s.name().equalsIgnoreCase(text) || s.displayName.equalsIgnoreCase(text)) {
                return s;
            }
        }
        return PENDING;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
