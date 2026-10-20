package com.solarshield.model;

/**
 * Enumeration representing the operational status of a solar panel.
 */
public enum PanelStatus {
    HEALTHY("Healthy", "#10B981", "Panel operating within nominal parameters"),
    WARNING("Warning", "#F59E0B", "Minor deviation or minor defect noted"),
    FAULTY("Faulty", "#EF4444", "Active fault identified requiring attention"),
    CRITICAL("Critical", "#B91C1C", "Severe fault causing hazardous or offline state"),
    MAINTENANCE("In Maintenance", "#3B82F6", "Under scheduled or active technician service"),
    OFFLINE("Offline", "#6B7280", "Disconnected from monitoring grid");

    private final String displayName;
    private final String colorHex;
    private final String description;

    PanelStatus(String displayName, String colorHex, String description) {
        this.displayName = displayName;
        this.colorHex = colorHex;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public String getDescription() {
        return description;
    }

    public static PanelStatus fromString(String text) {
        if (text == null) return HEALTHY;
        for (PanelStatus s : PanelStatus.values()) {
            if (s.name().equalsIgnoreCase(text) || s.displayName.equalsIgnoreCase(text)) {
                return s;
            }
        }
        return HEALTHY;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
