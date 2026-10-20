package com.solarshield.model;

/**
 * Enumeration representing the urgency level of a maintenance task.
 */
public enum MaintenancePriority {
    LOW("Low", "#10B981", "Schedule during regular seasonal servicing."),
    MEDIUM("Medium", "#F59E0B", "Inspect within 14 business days to prevent cell degradation."),
    HIGH("High", "#F97316", "Intervention required within 48 hours to avert loss."),
    URGENT("Urgent", "#EF4444", "Immediate emergency dispatch required. Hazard or critical yield drop.");

    private final String displayName;
    private final String colorHex;
    private final String description;

    MaintenancePriority(String displayName, String colorHex, String description) {
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

    public static MaintenancePriority fromString(String text) {
        if (text == null) return LOW;
        for (MaintenancePriority p : MaintenancePriority.values()) {
            if (p.name().equalsIgnoreCase(text) || p.displayName.equalsIgnoreCase(text)) {
                return p;
            }
        }
        return LOW;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
