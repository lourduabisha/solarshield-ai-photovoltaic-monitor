package com.solarshield.util;

import nu.pattern.OpenCV;

/**
 * Utility to reliably load the OpenCV native library.
 * Uses openpnp-opencv which automatically extracts and loads the
 * native dynamic library for the host OS (Windows, Linux, macOS).
 */
public class OpenCVLoader {
    private static boolean isLoaded = false;
    private static String loadMessage = "OpenCV not loaded yet";

    public static synchronized boolean load() {
        if (isLoaded) {
            return true;
        }

        try {
            // Nu Pattern Openpnp loader extracts Windows x64 DLL and calls System.load
            OpenCV.loadLocally();
            isLoaded = true;
            loadMessage = "OpenCV " + org.opencv.core.Core.VERSION + " initialized successfully";
            System.out.println("[SolarShield] " + loadMessage);
            return true;
        } catch (Throwable t) {
            isLoaded = false;
            loadMessage = "Failed to load OpenCV: " + t.getMessage();
            System.err.println("[SolarShield] " + loadMessage);
            return false;
        }
    }

    public static boolean isAvailable() {
        if (!isLoaded) {
            load();
        }
        return isLoaded;
    }

    public static String getStatusMessage() {
        return loadMessage;
    }
}
