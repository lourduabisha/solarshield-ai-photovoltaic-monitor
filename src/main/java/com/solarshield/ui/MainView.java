package com.solarshield.ui;

import com.solarshield.ai.AIInferenceService;
import com.solarshield.database.*;
import com.solarshield.model.FaultReport;
import com.solarshield.model.SolarPanel;
import com.solarshield.service.*;
import com.solarshield.util.DateUtils;
import com.solarshield.util.SessionManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Main application shell containing the sidebar navigation, top header,
 * live system clock, user identity badge, and dynamic central content switcher.
 */
public class MainView extends BorderPane {

    private final Runnable onLogout;

    // Repositories & Services
    private final SolarPanelRepository panelRepository;
    private final FaultReportRepository reportRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final AlertRepository alertRepository;

    private final DashboardService dashboardService;
    private final FaultDetectionService detectionService;
    private final MaintenanceService maintenanceService;
    private final ReportService reportService;

    // Views
    private DashboardView dashboardView;
    private PanelView panelView;
    private DetectionView detectionView;
    private FaultHistoryView faultHistoryView;
    private MaintenanceView maintenanceView;
    private ReportView reportView;
    private SettingsView settingsView;

    // Navigation UI
    private final Map<String, Button> navButtons = new HashMap<>();
    private Label clockLabel;
    private Label userGreetingLabel;
    private Label alertCounterBadge;

    public MainView(Runnable onLogout) {
        this.onLogout = onLogout;

        // Initialize dependencies
        this.panelRepository = new SolarPanelRepository();
        this.reportRepository = new FaultReportRepository();
        this.maintenanceRepository = new MaintenanceRepository();
        this.alertRepository = new AlertRepository();

        this.dashboardService = new DashboardService();
        this.detectionService = new FaultDetectionService();
        this.maintenanceService = new MaintenanceService(maintenanceRepository, panelRepository);
        this.reportService = new ReportService();

        initViews();
        initUI();
        startLiveClock();

        // Start on Dashboard
        navigateTo("DASHBOARD");
    }

    private void initViews() {
        dashboardView = new DashboardView(dashboardService, this::navigateTo);

        panelView = new PanelView(
                panelRepository,
                panel -> {
                    detectionView.selectPanel(panel);
                    navigateTo("DETECT");
                },
                panel -> {
                    maintenanceView.showCreateTaskDialog(panel.getPanelId(), null);
                    navigateTo("MAINTENANCE");
                }
        );

        detectionView = new DetectionView(
                detectionService,
                panelRepository,
                report -> {
                    maintenanceView.showCreateTaskDialog(report.getPanelId(), report.getId());
                    navigateTo("MAINTENANCE");
                },
                report -> {
                    reportView.generateSingleFaultReportPdf(report);
                }
        );

        faultHistoryView = new FaultHistoryView(reportRepository, panelRepository);
        maintenanceView = new MaintenanceView(maintenanceService, panelRepository);
        reportView = new ReportView(reportService, reportRepository, panelRepository, maintenanceRepository);
        settingsView = new SettingsView(detectionService.getAiInferenceService());
    }

    private void initUI() {
        setLeft(createSidebar());
        setTop(createTopHeader());
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setPadding(new Insets(20, 14, 20, 14));
        sidebar.setSpacing(6);

        // Brand Banner
        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 0, 16, 6));

        StackPane emblem = new StackPane();
        Circle c = new Circle(18, Color.web("#00b4d8"));
        Label sym = new Label("☀");
        sym.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff;");
        emblem.getChildren().addAll(c, sym);

        VBox brandText = new VBox(2);
        Label brandTitle = new Label("SOLARSHIELD");
        brandTitle.getStyleClass().add("sidebar-brand-title");
        Label brandSub = new Label("AI FAULT MONITORING");
        brandSub.getStyleClass().add("sidebar-brand-subtitle");
        brandText.getChildren().addAll(brandTitle, brandSub);

        brand.getChildren().addAll(emblem, brandText);
        sidebar.getChildren().addAll(brand, new Separator());

        // Nav items
        addNavItem(sidebar, "DASHBOARD", "📊  Dashboard");
        addNavItem(sidebar, "PANELS", "⚡  Solar Panels");
        addNavItem(sidebar, "DETECT", "🔍  Detect Fault");
        addNavItem(sidebar, "HISTORY", "📜  Fault History");
        addNavItem(sidebar, "MAINTENANCE", "🔧  Maintenance");
        addNavItem(sidebar, "REPORTS", "📑  Reports");
        addNavItem(sidebar, "SETTINGS", "⚙  Settings");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        // Logout Button
        Button logoutBtn = new Button("🚪  Logout");
        logoutBtn.getStyleClass().add("logout-button");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> {
            SessionManager.getInstance().logout();
            onLogout.run();
        });

        sidebar.getChildren().addAll(new Separator(), logoutBtn);
        return sidebar;
    }

    private void addNavItem(VBox sidebar, String key, String label) {
        Button btn = new Button(label);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> navigateTo(key));
        navButtons.put(key, btn);
        sidebar.getChildren().add(btn);
    }

    private HBox createTopHeader() {
        HBox header = new HBox(16);
        header.getStyleClass().add("top-header");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("SOLARSHIELD");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 900; -fx-text-fill: #0f172a; -fx-letter-spacing: 1px;");
        Label subtitle = new Label("AI-Based Solar Panel Fault Detection & Predictive Monitoring");
        subtitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #0284c7;");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Live Clock
        clockLabel = new Label();
        clockLabel.getStyleClass().add("header-clock");

        // User badge
        HBox userBadge = new HBox(8);
        userBadge.getStyleClass().add("user-badge");
        userBadge.setAlignment(Pos.CENTER_LEFT);

        Label userIcon = new Label("👤");
        userGreetingLabel = new Label("Welcome, " + SessionManager.getInstance().getCurrentUserName());
        userGreetingLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b; -fx-font-size: 12px;");
        userBadge.getChildren().addAll(userIcon, userGreetingLabel);

        // Active Alerts Indicator
        HBox alertIndicator = new HBox(4);
        alertIndicator.setAlignment(Pos.CENTER);
        alertIndicator.setStyle("-fx-background-color: #fee2e2; -fx-background-radius: 12px; -fx-padding: 4 10 4 10; -fx-cursor: hand;");
        Label aIcon = new Label("🚨");
        alertCounterBadge = new Label("0 Alerts");
        alertCounterBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #991b1b;");
        alertIndicator.getChildren().addAll(aIcon, alertCounterBadge);
        alertIndicator.setOnMouseClicked(e -> navigateTo("DASHBOARD"));

        header.getChildren().addAll(titleBox, spacer, clockLabel, alertIndicator, userBadge);
        return header;
    }

    public void navigateTo(String key) {
        // Reset nav styles
        for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
            entry.getValue().getStyleClass().remove("nav-button-active");
            if (entry.getKey().equals(key)) {
                entry.getValue().getStyleClass().add("nav-button-active");
            }
        }

        Node viewToSet = switch (key) {
            case "PANELS" -> {
                panelView.loadPanels();
                yield panelView;
            }
            case "DETECT" -> detectionView;
            case "HISTORY" -> {
                faultHistoryView.loadReports();
                yield faultHistoryView;
            }
            case "MAINTENANCE" -> {
                maintenanceView.loadTasks();
                yield maintenanceView;
            }
            case "REPORTS" -> reportView;
            case "SETTINGS" -> {
                settingsView.refreshStatus();
                yield settingsView;
            }
            default -> {
                dashboardView.refreshData();
                yield dashboardView;
            }
        };

        setCenter(viewToSet);
        updateAlertCount();
    }

    private void updateAlertCount() {
        int count = alertRepository.countActive();
        if (alertCounterBadge != null) {
            alertCounterBadge.setText(count + (count == 1 ? " Alert" : " Alerts"));
        }
    }

    private void startLiveClock() {
        updateClock();
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void updateClock() {
        if (clockLabel != null) {
            clockLabel.setText("🕒 " + DateUtils.formatDisplayDateTime(LocalDateTime.now()));
        }
    }

    public void refreshUserInfo() {
        if (userGreetingLabel != null) {
            userGreetingLabel.setText("Welcome, " + SessionManager.getInstance().getCurrentUserName());
        }
    }
}
