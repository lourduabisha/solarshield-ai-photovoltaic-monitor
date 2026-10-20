package com.solarshield.ui;

import com.solarshield.service.AuthenticationService;
import com.solarshield.util.AlertUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

/**
 * Initial application screen for user authentication.
 * As strictly specified in the project requirements:
 * The application must ONLY display authentication on initial launch;
 * the dashboard is inaccessible prior to authenticated credentials validation.
 */
public class LoginView extends StackPane {

    private final AuthenticationService authService;
    private final Runnable onLoginSuccess;
    private final Runnable onNavigateToRegister;

    private TextField emailField;
    private PasswordField passwordField;
    private Label errorLabel;
    private Button loginButton;

    public LoginView(AuthenticationService authService, Runnable onLoginSuccess, Runnable onNavigateToRegister) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;
        this.onNavigateToRegister = onNavigateToRegister;

        initUI();
    }

    private void initUI() {
        // Deep solar gradient background
        setStyle("-fx-background-color: linear-gradient(to bottom right, #091726 0%, #0d2744 50%, #071524 100%);");

        VBox card = new VBox(20);
        card.setMaxWidth(440);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(36, 40, 36, 40));
        card.setStyle(
                "-fx-background-color: #ffffff;" +
                "-fx-background-radius: 16px;" +
                "-fx-border-color: #e2e8f0;" +
                "-fx-border-radius: 16px;" +
                "-fx-border-width: 1px;" +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.35), 24, 0, 0, 8);"
        );

        // Solar Logo Emblem
        StackPane logoPane = new StackPane();
        Circle bgCircle = new Circle(32, Color.web("#0284c7"));
        Label logoIcon = new Label("☀");
        logoIcon.setStyle("-fx-font-size: 32px; -fx-text-fill: #ffffff;");
        logoPane.getChildren().addAll(bgCircle, logoIcon);

        // Header Labels
        VBox titleBox = new VBox(4);
        titleBox.setAlignment(Pos.CENTER);
        Label title = new Label("SOLARSHIELD");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: #0f172a; -fx-letter-spacing: 1.5px;");
        Label subtitle = new Label("AI-Based Solar Panel Fault Detection");
        subtitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #0284c7;");
        titleBox.getChildren().addAll(title, subtitle);

        // Error message display
        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: 600; -fx-font-size: 12px;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);

        // Form Fields
        VBox form = new VBox(14);
        form.setAlignment(Pos.CENTER_LEFT);

        VBox emailBox = new VBox(6);
        Label emailLabel = new Label("Email / Username");
        emailLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 12px;");
        emailField = new TextField();
        emailField.setPromptText("engineer@solarshield.com");
        emailField.setText("admin@solarshield.com"); // Pre-fill with default for convenient demonstration
        emailField.setPrefHeight(42);
        emailBox.getChildren().addAll(emailLabel, emailField);

        VBox passwordBox = new VBox(6);
        Label passLabel = new Label("Password");
        passLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 12px;");
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setText("admin123"); // Pre-fill default password
        passwordField.setPrefHeight(42);
        passwordBox.getChildren().addAll(passLabel, passwordField);

        form.getChildren().addAll(emailBox, passwordBox);

        // Buttons
        loginButton = new Button("LOGIN");
        loginButton.getStyleClass().add("btn-primary");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setPrefHeight(44);
        loginButton.setOnAction(e -> handleLogin());

        // Enable Enter key submission
        passwordField.setOnAction(e -> handleLogin());
        emailField.setOnAction(e -> handleLogin());

        Button registerButton = new Button("CREATE NEW ACCOUNT");
        registerButton.getStyleClass().add("btn-outline");
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setPrefHeight(40);
        registerButton.setOnAction(e -> onNavigateToRegister.run());

        // Demo credentials hint badge
        VBox hintBox = new VBox(4);
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPadding(new Insets(10, 0, 0, 0));
        Label hintHeader = new Label("Default Academic Demo Credentials:");
        hintHeader.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-font-weight: 600;");
        Label hintCreds = new Label("admin@solarshield.com  |  admin123");
        hintCreds.setStyle("-fx-font-size: 11px; -fx-text-fill: #0284c7; -fx-font-weight: 700;");
        hintBox.getChildren().addAll(hintHeader, hintCreds);

        card.getChildren().addAll(
                logoPane,
                titleBox,
                errorLabel,
                form,
                loginButton,
                registerButton,
                new Separator(),
                hintBox
        );

        getChildren().add(card);
    }

    private void handleLogin() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        String email = emailField.getText();
        String pass = passwordField.getText();

        AuthenticationService.AuthResult result = authService.login(email, pass);
        if (result.isSuccess()) {
            onLoginSuccess.run();
        } else {
            errorLabel.setText(result.getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }
}
