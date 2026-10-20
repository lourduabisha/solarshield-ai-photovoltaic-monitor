package com.solarshield;

import com.solarshield.database.DatabaseInitializer;
import com.solarshield.service.AuthenticationService;
import com.solarshield.ui.LoginView;
import com.solarshield.ui.MainView;
import com.solarshield.ui.RegisterView;
import com.solarshield.util.OpenCVLoader;
import com.solarshield.util.SampleImageGenerator;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.File;

/**
 * JavaFX Application entrypoint for SolarShield.
 *
 * CRITICAL FLOW:
 * The application strictly presents the Login/Authentication interface first.
 * The Main Dashboard is inaccessible until valid user authentication is established.
 */
public class MainApp extends Application {

    private Stage primaryStage;
    private AuthenticationService authService;
    private Scene loginScene;
    private Scene registerScene;

    @Override
    public void init() {
        System.out.println("==================================================");
        System.out.println(" SOLARSHIELD - AI-BASED SOLAR PANEL FAULT MONITOR ");
        System.out.println("==================================================");

        // Ensure directories exist
        new File("data/images").mkdirs();
        new File("sample_images").mkdirs();

        // 1. Initialize SQLite Database Schema & Seed Data
        DatabaseInitializer.initialize();

        // 2. Load OpenCV Native Libraries
        OpenCVLoader.load();

        // 3. Generate Demonstration Test Images
        SampleImageGenerator.generateAllSamples();

        // 4. Initialize Services
        authService = new AuthenticationService();
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("SolarShield - AI-Based Solar Panel Fault Detection");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(720);

        // Build Authentication Scenes
        LoginView loginView = new LoginView(
                authService,
                this::showDashboard,
                this::showRegisterScreen
        );
        loginScene = new Scene(loginView, 1100, 750);
        loginScene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        RegisterView registerView = new RegisterView(
                authService,
                this::showLoginScreen
        );
        registerScene = new Scene(registerView, 1100, 750);
        registerScene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        // Strictly present Login screen first
        showLoginScreen();
        primaryStage.show();
    }

    public void showLoginScreen() {
        primaryStage.setScene(loginScene);
    }

    public void showRegisterScreen() {
        primaryStage.setScene(registerScene);
    }

    public void showDashboard() {
        MainView mainView = new MainView(this::showLoginScreen);
        mainView.refreshUserInfo();

        Scene mainScene = new Scene(mainView, 1280, 820);
        mainScene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        primaryStage.setScene(mainScene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
