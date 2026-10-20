package com.solarshield;

/**
 * Standard non-subclassed Java entrypoint for executing SolarShield.
 * Prevents JavaFX runtime launcher issues when executed via Maven or standalone jar.
 */
public class Main {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
