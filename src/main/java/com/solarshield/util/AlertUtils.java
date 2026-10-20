package com.solarshield.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

import java.util.Optional;

/**
 * Utility class providing styled JavaFX alert and notification dialogs.
 */
public final class AlertUtils {

    private AlertUtils() {
        // Prevent instantiation
    }

    public static void showInfo(String title, String header, String content) {
        runOnFx(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            styleDialog(alert);
            alert.showAndWait();
        });
    }

    public static void showWarning(String title, String header, String content) {
        runOnFx(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            styleDialog(alert);
            alert.showAndWait();
        });
    }

    public static void showError(String title, String header, String content) {
        runOnFx(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            styleDialog(alert);
            alert.showAndWait();
        });
    }

    public static boolean showConfirmation(String title, String header, String content) {
        if (!Platform.isFxApplicationThread()) {
            return false;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        styleDialog(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void showCriticalFaultAlert(String panelId, String faultType, String priority, String action) {
        runOnFx(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("CRITICAL ALERT - SolarShield");
            alert.setHeaderText("⚠ CRITICAL SOLAR PANEL FAULT DETECTED");
            alert.setContentText(String.format(
                    "Panel ID: %s\nFault Type: %s\nMaintenance Priority: %s\n\nRecommended Action:\n%s",
                    panelId, faultType, priority, action
            ));
            styleDialog(alert);
            alert.showAndWait();
        });
    }

    private static void styleDialog(Alert alert) {
        DialogPane pane = alert.getDialogPane();
        pane.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 13px;");
    }

    private static void runOnFx(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException e) {
            // JavaFX toolkit not initialized (e.g. running in headless unit tests)
            System.out.println("[AlertUtils] Headless test environment; skipping modal dialog.");
        }
    }
}
