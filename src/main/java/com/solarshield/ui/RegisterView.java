package com.solarshield.ui;

import com.solarshield.service.AuthenticationService;
import com.solarshield.util.AlertUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Account registration screen.
 * Validates full name, email format, password security requirements, and confirms password equality.
 */
public class RegisterView extends StackPane {

    private final AuthenticationService authService;
    private final Runnable onNavigateToLogin;

    private TextField nameField;
    private TextField emailField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private Label feedbackLabel;

    public RegisterView(AuthenticationService authService, Runnable onNavigateToLogin) {
        this.authService = authService;
        this.onNavigateToLogin = onNavigateToLogin;

        initUI();
    }

    private void initUI() {
        setStyle("-fx-background-color: linear-gradient(to bottom right, #091726 0%, #0d2744 50%, #071524 100%);");

        VBox card = new VBox(18);
        card.setMaxWidth(460);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(32, 40, 32, 40));
        card.setStyle(
                "-fx-background-color: #ffffff;" +
                "-fx-background-radius: 16px;" +
                "-fx-border-color: #e2e8f0;" +
                "-fx-border-radius: 16px;" +
                "-fx-border-width: 1px;" +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.35), 24, 0, 0, 8);"
        );

        // Logo
        StackPane logoPane = new StackPane();
        Circle bgCircle = new Circle(28, Color.web("#10b981"));
        Label logoIcon = new Label("✚");
        logoIcon.setStyle("-fx-font-size: 24px; -fx-text-fill: #ffffff;");
        logoPane.getChildren().addAll(bgCircle, logoIcon);

        VBox titleBox = new VBox(4);
        titleBox.setAlignment(Pos.CENTER);
        Label title = new Label("CREATE NEW ACCOUNT");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("Register as a SolarShield Monitoring Technician");
        subtitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(title, subtitle);

        feedbackLabel = new Label();
        feedbackLabel.setStyle("-fx-font-weight: 600; -fx-font-size: 12px;");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
        feedbackLabel.setWrapText(true);

        VBox form = new VBox(12);
        form.setAlignment(Pos.CENTER_LEFT);

        // Name
        VBox nameBox = new VBox(5);
        Label nameLbl = new Label("Full Name *");
        nameLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 12px;");
        nameField = new TextField();
        nameField.setPromptText("e.g. Dr. Arthur Pendelton");
        nameField.setPrefHeight(40);
        nameBox.getChildren().addAll(nameLbl, nameField);

        // Email
        VBox emailBox = new VBox(5);
        Label emailLbl = new Label("Email Address *");
        emailLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 12px;");
        emailField = new TextField();
        emailField.setPromptText("technician@solarshield.com");
        emailField.setPrefHeight(40);
        emailBox.getChildren().addAll(emailLbl, emailField);

        // Password
        VBox passBox = new VBox(5);
        Label passLbl = new Label("Password (min 6 characters) *");
        passLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 12px;");
        passwordField = new PasswordField();
        passwordField.setPromptText("Create secure password");
        passwordField.setPrefHeight(40);
        passBox.getChildren().addAll(passLbl, passwordField);

        // Confirm Password
        VBox confirmPassBox = new VBox(5);
        Label confirmPassLbl = new Label("Confirm Password *");
        confirmPassLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155; -fx-font-size: 12px;");
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Re-enter password");
        confirmPasswordField.setPrefHeight(40);
        confirmPassBox.getChildren().addAll(confirmPassLbl, confirmPasswordField);

        form.getChildren().addAll(nameBox, emailBox, passBox, confirmPassBox);

        // Buttons
        Button createAccountButton = new Button("CREATE ACCOUNT");
        createAccountButton.getStyleClass().add("btn-success");
        createAccountButton.setMaxWidth(Double.MAX_VALUE);
        createAccountButton.setPrefHeight(44);
        createAccountButton.setOnAction(e -> handleRegister());

        Button backButton = new Button("BACK TO LOGIN");
        backButton.getStyleClass().add("btn-outline");
        backButton.setMaxWidth(Double.MAX_VALUE);
        backButton.setPrefHeight(40);
        backButton.setOnAction(e -> onNavigateToLogin.run());

        card.getChildren().addAll(
                logoPane,
                titleBox,
                feedbackLabel,
                form,
                createAccountButton,
                backButton
        );

        getChildren().add(card);
    }

    private void handleRegister() {
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        String name = nameField.getText();
        String email = emailField.getText();
        String pass = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        AuthenticationService.AuthResult result = authService.register(name, email, pass, confirm);
        if (result.isSuccess()) {
            AlertUtils.showInfo("Registration Successful", "Account Created",
                    "Your account has been registered successfully.\nPlease log in with your credentials.");
            onNavigateToLogin.run();
        } else {
            feedbackLabel.setText(result.getMessage());
            feedbackLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: 600; -fx-font-size: 12px;");
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        }
    }
}
