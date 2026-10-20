package com.solarshield.model;

/**
 * Enumeration representing solar panel defect and operational states.
 * Demonstrates Java OOP enum with fields, constructors, and specialized methods.
 */
public enum FaultType {
    HEALTHY("Healthy", "Normal operational state with uniform cell irradiance and no discernible defects.", "#10B981", false),
    HOTSPOT("Hotspot", "Local localized overheating indicative of defective bypass diodes or localized cell degradation.", "#EF4444", true),
    CRACK("Crack", "Micro-fractures or structural cell breakages impairing electron flow and conductivity.", "#F97316", true),
    SOILING("Soiling / Dirt", "Surface accumulation of dust, dirt, pollen, or environmental residue attenuating sunlight.", "#EAB308", true),
    PARTIAL_SHADING("Partial Shading", "Vegetation, nearby structures, or debris casting dynamic shadows across modules.", "#8B5CF6", true),
    PHYSICAL_DAMAGE("Physical Damage", "Severe glass breakage, delamination, frame deformities, or impact fractures.", "#DC2626", true);

    private final String displayName;
    private final String description;
    private final String colorHex;
    private final boolean isFault;

    FaultType(String displayName, String description, String colorHex, boolean isFault) {
        this.displayName = displayName;
        this.description = description;
        this.colorHex = colorHex;
        this.isFault = isFault;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getColorHex() {
        return colorHex;
    }

    public boolean isFault() {
        return isFault;
    }

    public static FaultType fromString(String text) {
        if (text == null) return HEALTHY;
        for (FaultType ft : FaultType.values()) {
            if (ft.name().equalsIgnoreCase(text) || ft.displayName.equalsIgnoreCase(text)) {
                return ft;
            }
        }
        return HEALTHY;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
