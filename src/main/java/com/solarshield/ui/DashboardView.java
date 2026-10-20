package com.solarshield.ui;

import com.solarshield.model.*;
import com.solarshield.service.DashboardService;
import com.solarshield.util.DateUtils;
import com.solarshield.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Main dashboard view providing farm-wide health KPIs, fault distribution analytics,
 * temporal trend charts, and active operational alerts.
 */
public class DashboardView extends ScrollPane {

    private final DashboardService dashboardService;
    private final Consumer<String> navigationHandler;

    private Label totalPanelsLbl;
    private Label healthyPanelsLbl;
    private Label faultyPanelsLbl;
    private Label criticalFaultsLbl;
    private Label pendingMaintLbl;

    private VBox alertsContainer;
    private StackPane chart1Container;
    private StackPane chart2Container;
    private StackPane chart3Container;

    public DashboardView(DashboardService dashboardService, Consumer<String> navigationHandler) {
        this.dashboardService = dashboardService;
        this.navigationHandler = navigationHandler;

        setFitToWidth(true);
        setStyle("-fx-background: #f1f5f9; -fx-background-color: transparent;");

        initUI();
        refreshData();
    }

    private void initUI() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(24));

        // 1. Welcome & Subtitle Banner
        VBox banner = new VBox(4);
        Label title = new Label("SYSTEM OVERVIEW & PREDICTIVE ANALYTICS");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("AI-Assisted Photovoltaic Farm Health, Anomaly Distribution & Fleet Status");
        subtitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: #64748b;");
        banner.getChildren().addAll(title, subtitle);

        // 2. KPI Cards Row (Total, Healthy, Faulty, Critical, Pending Maintenance)
        HBox kpiRow = new HBox(16);
        kpiRow.setAlignment(Pos.CENTER);

        VBox totalCard = createKpiCard("TOTAL MONITORED PANELS", "0", "kpi-card-primary", "⚡");
        totalPanelsLbl = (Label) totalCard.lookup(".kpi-value");

        VBox healthyCard = createKpiCard("HEALTHY MODULES", "0", "kpi-card-healthy", "✔");
        healthyPanelsLbl = (Label) healthyCard.lookup(".kpi-value");

        VBox faultyCard = createKpiCard("ACTIVE FAULT DETECTIONS", "0", "kpi-card-warning", "⚠");
        faultyPanelsLbl = (Label) faultyCard.lookup(".kpi-value");

        VBox criticalCard = createKpiCard("CRITICAL SEVERITY FAULTS", "0", "kpi-card-danger", "🚨");
        criticalFaultsLbl = (Label) criticalCard.lookup(".kpi-value");

        VBox maintCard = createKpiCard("PENDING MAINTENANCE", "0", "kpi-card-primary", "🔧");
        pendingMaintLbl = (Label) maintCard.lookup(".kpi-value");

        HBox.setHgrow(totalCard, Priority.ALWAYS);
        HBox.setHgrow(healthyCard, Priority.ALWAYS);
        HBox.setHgrow(faultyCard, Priority.ALWAYS);
        HBox.setHgrow(criticalCard, Priority.ALWAYS);
        HBox.setHgrow(maintCard, Priority.ALWAYS);

        kpiRow.getChildren().addAll(totalCard, healthyCard, faultyCard, criticalCard, maintCard);

        // 3. Quick Action Bar
        HBox actionsBar = new HBox(12);
        actionsBar.setAlignment(Pos.CENTER_LEFT);
        actionsBar.setPadding(new Insets(10, 16, 10, 16));
        actionsBar.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10px; -fx-border-color: #e2e8f0; -fx-border-radius: 10px;");

        Label quickLbl = new Label("Quick Actions:");
        quickLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155;");

        Button detectBtn = new Button("🔍 Run New AI Detection");
        detectBtn.getStyleClass().add("btn-primary");
        detectBtn.setOnAction(e -> navigationHandler.accept("DETECT"));

        Button addPanelBtn = new Button("✚ Register Solar Panel");
        addPanelBtn.getStyleClass().add("btn-outline");
        addPanelBtn.setOnAction(e -> navigationHandler.accept("PANELS"));

        Button maintBtn = new Button("🔧 Schedule Maintenance");
        maintBtn.getStyleClass().add("btn-outline");
        maintBtn.setOnAction(e -> navigationHandler.accept("MAINTENANCE"));

        Button reportBtn = new Button("📑 Generate Audit Report");
        reportBtn.getStyleClass().add("btn-outline");
        reportBtn.setOnAction(e -> navigationHandler.accept("REPORTS"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("🔄 Refresh Dashboard");
        refreshBtn.getStyleClass().add("btn-outline");
        refreshBtn.setOnAction(e -> refreshData());

        actionsBar.getChildren().addAll(quickLbl, detectBtn, addPanelBtn, maintBtn, reportBtn, spacer, refreshBtn);

        // 4. Charts Row: Chart 1 (PieChart Fault Distribution) and Chart 2 (BarChart Severity Distribution)
        HBox chartsRow1 = new HBox(20);
        chart1Container = new StackPane();
        chart1Container.getStyleClass().add("card");
        HBox.setHgrow(chart1Container, Priority.ALWAYS);
        chart1Container.setPrefHeight(320);

        chart2Container = new StackPane();
        chart2Container.getStyleClass().add("card");
        HBox.setHgrow(chart2Container, Priority.ALWAYS);
        chart2Container.setPrefHeight(320);

        chartsRow1.getChildren().addAll(chart1Container, chart2Container);

        // 5. Lower Row: Chart 3 (Fault Trend over time) + Active Alerts Box
        HBox lowerRow = new HBox(20);

        chart3Container = new StackPane();
        chart3Container.getStyleClass().add("card");
        HBox.setHgrow(chart3Container, Priority.ALWAYS);
        chart3Container.setPrefHeight(280);

        // Active Alerts Card
        VBox alertsCard = new VBox(12);
        alertsCard.getStyleClass().add("card");
        alertsCard.setPrefWidth(380);
        alertsCard.setMinWidth(320);

        HBox alertsHeader = new HBox(8);
        alertsHeader.setAlignment(Pos.CENTER_LEFT);
        Label alertsTitle = new Label("Active Critical Alerts");
        alertsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);
        Label alertsBadge = new Label("LIVE");
        alertsBadge.getStyleClass().addAll("badge", "badge-faulty");
        alertsHeader.getChildren().addAll(alertsTitle, hSpacer, alertsBadge);

        alertsContainer = new VBox(8);

        alertsCard.getChildren().addAll(alertsHeader, new Separator(), alertsContainer);

        lowerRow.getChildren().addAll(chart3Container, alertsCard);

        root.getChildren().addAll(banner, kpiRow, actionsBar, chartsRow1, lowerRow);
        setContent(root);
    }

    private VBox createKpiCard(String titleText, String initValue, String borderClass, String icon) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("kpi-card", borderClass);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 14px;");
        Label title = new Label(titleText);
        title.getStyleClass().add("kpi-title");
        top.getChildren().addAll(iconLbl, title);

        Label value = new Label(initValue);
        value.getStyleClass().add("kpi-value");

        card.getChildren().addAll(top, value);
        return card;
    }

    public void refreshData() {
        DashboardService.DashboardStats stats = dashboardService.getStats();

        totalPanelsLbl.setText(String.valueOf(stats.getTotalPanels()));
        healthyPanelsLbl.setText(String.valueOf(stats.getHealthyPanels()));
        faultyPanelsLbl.setText(String.valueOf(stats.getFaultyPanels()));
        criticalFaultsLbl.setText(String.valueOf(stats.getCriticalFaults()));
        pendingMaintLbl.setText(String.valueOf(stats.getPendingMaintenance()));

        // Populate Chart 1: Fault Distribution PieChart
        renderFaultDistributionChart();

        // Populate Chart 2: Severity Distribution BarChart
        renderSeverityDistributionChart();

        // Populate Chart 3: Fault Trend Line/Bar Chart
        renderFaultTrendChart();

        // Populate Alerts
        renderAlerts(dashboardService.getActiveAlerts());
    }

    private void renderFaultDistributionChart() {
        chart1Container.getChildren().clear();
        Map<FaultType, Integer> dist = dashboardService.getFaultDistribution();
        int sum = dist.values().stream().mapToInt(Integer::intValue).sum();

        if (sum == 0) {
            chart1Container.getChildren().add(createEmptyState("No Fault Data Available", "Run a fault detection on solar panels to populate distribution charts."));
            return;
        }

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (Map.Entry<FaultType, Integer> entry : dist.entrySet()) {
            if (entry.getValue() > 0) {
                pieData.add(new PieChart.Data(entry.getKey().getDisplayName() + " (" + entry.getValue() + ")", entry.getValue()));
            }
        }

        PieChart pieChart = new PieChart(pieData);
        pieChart.setTitle("Defect Classification Distribution");
        pieChart.setLegendVisible(true);
        chart1Container.getChildren().add(pieChart);
    }

    private void renderSeverityDistributionChart() {
        chart2Container.getChildren().clear();
        Map<Severity, Integer> sev = dashboardService.getSeverityDistribution();
        int sum = sev.values().stream().mapToInt(Integer::intValue).sum();

        if (sum == 0) {
            chart2Container.getChildren().add(createEmptyState("No Severity Telemetry", "Inspect solar panel images to classify risk severities."));
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Severity Level");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Detected Incidents");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Defect Risk Severity Breakdown");
        barChart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<Severity, Integer> entry : sev.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey().getDisplayName(), entry.getValue()));
        }
        barChart.getData().add(series);
        chart2Container.getChildren().add(barChart);
    }

    private void renderFaultTrendChart() {
        chart3Container.getChildren().clear();
        Map<String, Integer> trend = dashboardService.getFaultTrend();

        if (trend.isEmpty()) {
            chart3Container.getChildren().add(createEmptyState("No Trend Timeline Available", "Inspections logged over time will visualize defect incidence trends here."));
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Inspection Date");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Fault Count");

        BarChart<String, Number> lineChart = new BarChart<>(xAxis, yAxis);
        lineChart.setTitle("Temporal Fault Detection Timeline");
        lineChart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Integer> entry : trend.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        lineChart.getData().add(series);
        chart3Container.getChildren().add(lineChart);
    }

    private void renderAlerts(List<AlertItem> alerts) {
        alertsContainer.getChildren().clear();
        if (alerts == null || alerts.isEmpty()) {
            VBox empty = new VBox(6);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(24));
            Label icon = new Label("✔");
            icon.setStyle("-fx-font-size: 24px; -fx-text-fill: #10b981;");
            Label msg = new Label("All Panels Operating Within Nominal Limits");
            msg.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-font-weight: 600;");
            empty.getChildren().addAll(icon, msg);
            alertsContainer.getChildren().add(empty);
            return;
        }

        for (AlertItem alert : alerts) {
            VBox item = new VBox(4);
            item.setPadding(new Insets(8, 10, 8, 10));
            item.setStyle(
                    "-fx-background-color: #fef2f2; -fx-background-radius: 6px; -fx-border-color: #fca5a5; -fx-border-radius: 6px; -fx-border-width: 1px;"
            );

            HBox top = new HBox(6);
            top.setAlignment(Pos.CENTER_LEFT);
            Label pLbl = new Label(alert.getPanelId());
            pLbl.setStyle("-fx-font-weight: 800; -fx-text-fill: #991b1b; -fx-font-size: 12px;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Label sevLbl = new Label(alert.getSeverity().name());
            sevLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: #b91c1c;");
            top.getChildren().addAll(pLbl, sp, sevLbl);

            Label text = new Label(alert.getMessage());
            text.setWrapText(true);
            text.setStyle("-fx-font-size: 11px; -fx-text-fill: #450a0a;");

            item.getChildren().addAll(top, text);
            alertsContainer.getChildren().add(item);
        }
    }

    private VBox createEmptyState(String titleText, String subtitleText) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));

        Label icon = new Label("📊");
        icon.setStyle("-fx-font-size: 32px; -fx-text-fill: #94a3b8;");
        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #64748b;");
        Label subtitle = new Label(subtitleText);
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");

        box.getChildren().addAll(icon, title, subtitle);
        return box;
    }
}
