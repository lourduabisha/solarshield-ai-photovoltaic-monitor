package com.solarshield.model;

import org.opencv.imgproc.Imgproc;

/**
 * Supported false-color thermal palettes and visual filters for solar inspection.
 * Emulates thermographic FLIR and radiometric drone sensor modes.
 */
public enum ThermalColormap {
    NATURAL("Original Natural (RGB)", -1, "Standard true-color visible spectrum"),
    INFERNO("Thermal Inferno (FLIR Radiometric)", Imgproc.COLORMAP_INFERNO, "Deep purple-to-yellow FLIR radiometric thermography"),
    JET("Thermal Jet (Rainbow Spectrum)", Imgproc.COLORMAP_JET, "Classic blue-cyan-yellow-red rainbow gradient"),
    HOT("Thermal Ironbow / Hot", Imgproc.COLORMAP_HOT, "High-contrast thermal black-red-yellow-white palette"),
    TURBO("Thermal Turbo (Google AI HDR)", Imgproc.COLORMAP_TURBO, "Perceptually uniform high-dynamic-range spectrum"),
    BONE("Thermal Bone (Grayscale Enhanced)", Imgproc.COLORMAP_BONE, "Medical/industrial high-contrast grayscale"),
    EDGE_ENHANCED("Multi-Spectral Edge / CLAHE", -2, "Contrast-Limited Adaptive Histogram Equalization with Canny reticle");

    private final String displayName;
    private final int opencvCode;
    private final String description;

    ThermalColormap(String displayName, int opencvCode, String description) {
        this.displayName = displayName;
        this.opencvCode = opencvCode;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getOpencvCode() {
        return opencvCode;
    }

    public String getDescription() {
        return description;
    }

    public boolean isThermalPalette() {
        return opencvCode >= 0;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
