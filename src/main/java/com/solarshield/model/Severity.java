package com.solarshield.model;

/**
 * Enumeration representing fault severity levels.
 */
public enum Severity {
    LOW("Low", "#10B981", 1, "Minor anomaly. Minimal immediate energy impact."),
    MEDIUM("Medium", "#F59E0B", 2, "Moderate defect. Measurable power loss, schedule inspection."),
    HIGH("High", "#F97316", 3, "High risk defect. Substantial power degradation and potential cell damage."),
    CRITICAL("Critical", "#EF4444", 4, "Severe hazard. Immediate risk of fire, permanent failure, or circuit trip.");

    private final String displayName;
    private final String colorHex;
    private final int level;
    private final String guidance;

    Severity(String displayName, String colorHex, int level, String guidance) {
        this.displayName = displayName;
        this.colorHex = colorHex;
        this.level = level;
        this.guidance = guidance;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public int getLevel() {
        return level;
    }

    public String getGuidance() {
        return guidance;
    }

    public static Severity fromString(String text) {
        if (text == null) return LOW;
        for (Severity s : Severity.values()) {
            if (s.name().equalsIgnoreCase(text) || s.displayName.equalsIgnoreCase(text)) {
                return s;
            }
        }
        return LOW;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
