package com.solarshield.ui;

import com.solarshield.database.FaultReportRepository;
import com.solarshield.database.SolarPanelRepository;
import com.solarshield.model.*;
import com.solarshield.util.DateUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

/**
 * Historical Fault Reports View.
 * Displays all past AI inspection diagnostic reports stored in the SQLite database
 * with multi-factor filtering, sorting, and modal inspection details.
 */
public class FaultHistoryView extends VBox {

    private final FaultReportRepository reportRepository;
    private final SolarPanelRepository panelRepository;

    private TableView<FaultReport> reportTable;
    private ObservableList<FaultReport> reportData;

    private ComboBox<String> panelFilter;
    private ComboBox<String> faultFilter;
    private ComboBox<String> severityFilter;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;

    public FaultHistoryView(FaultReportRepository reportRepository, SolarPanelRepository panelRepository) {
        this.reportRepository = reportRepository;
        this.panelRepository = panelRepository;

        setSpacing(18);
        setPadding(new Insets(24));
        setStyle("-fx-background-color: transparent;");

        initUI();
        loadReports();
    }

    private void initUI() {
        // 1. Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label title = new Label("FAULT DETECTION HISTORY & LOGS");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("Comprehensive historical archive of all automated AI diagnostic inspections");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("🔄 Refresh History");
        refreshBtn.getStyleClass().add("btn-outline");
        refreshBtn.setOnAction(e -> applyFilter());

        header.getChildren().addAll(titleBox, spacer, refreshBtn);

        // 2. Filter Controls Card
        HBox filterCard = new HBox(14);
        filterCard.setAlignment(Pos.CENTER_LEFT);
        filterCard.setPadding(new Insets(12, 16, 12, 16));
        filterCard.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10px; -fx-border-color: #e2e8f0; -fx-border-radius: 10px;");

        // Panel filter
        VBox pBox = new VBox(4);
        pBox.getChildren().addAll(new Label("Filter Panel:"), panelFilter = new ComboBox<>());
        panelFilter.setPrefWidth(120);

        // Fault filter
        VBox fBox = new VBox(4);
        fBox.getChildren().addAll(new Label("Fault Type:"), faultFilter = new ComboBox<>());
        faultFilter.setPrefWidth(140);
        faultFilter.getItems().add("ALL FAULTS");
        for (FaultType ft : FaultType.values()) faultFilter.getItems().add(ft.name());
        faultFilter.setValue("ALL FAULTS");

        // Severity filter
        VBox sBox = new VBox(4);
        sBox.getChildren().addAll(new Label("Severity:"), severityFilter = new ComboBox<>());
        severityFilter.setPrefWidth(120);
        severityFilter.getItems().add("ALL SEVERITIES");
        for (Severity s : Severity.values()) severityFilter.getItems().add(s.name());
        severityFilter.setValue("ALL SEVERITIES");

        // Date from/to
        VBox d1Box = new VBox(4);
        d1Box.getChildren().addAll(new Label("From Date:"), fromDatePicker = new DatePicker());
        fromDatePicker.setPrefWidth(120);

        VBox d2Box = new VBox(4);
        d2Box.getChildren().addAll(new Label("To Date:"), toDatePicker = new DatePicker());
        toDatePicker.setPrefWidth(120);

        Button applyBtn = new Button("Apply Filters");
        applyBtn.getStyleClass().add("btn-primary");
        applyBtn.setOnAction(e -> applyFilter());

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("btn-outline");
        resetBtn.setOnAction(e -> {
            panelFilter.setValue("ALL");
            faultFilter.setValue("ALL FAULTS");
            severityFilter.setValue("ALL SEVERITIES");
            fromDatePicker.setValue(null);
            toDatePicker.setValue(null);
            loadReports();
        });

        filterCard.getChildren().addAll(pBox, fBox, sBox, d1Box, d2Box, applyBtn, resetBtn);

        // 3. Reports Table
        reportTable = new TableView<>();
        reportData = FXCollections.observableArrayList();
        reportTable.setItems(reportData);
        VBox.setVgrow(reportTable, Priority.ALWAYS);
        reportTable.setPlaceholder(new Label("No historical fault reports match the specified filters."));

        // Double click to view details
        reportTable.setRowFactory(tv -> {
            TableRow<FaultReport> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showReportDetails(row.getItem());
                }
            });
            return row;
        });

        TableColumn<FaultReport, String> idCol = new TableColumn<>("Report #");
        idCol.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getId()));
        idCol.setPrefWidth(80);

        TableColumn<FaultReport, String> panelCol = new TableColumn<>("Panel ID");
        panelCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPanelId()));
        panelCol.setPrefWidth(90);

        TableColumn<FaultReport, String> dateCol = new TableColumn<>("Inspection Date");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.formatDisplayDateTime(c.getValue().getCreatedAt())));
        dateCol.setPrefWidth(140);

        TableColumn<FaultReport, String> faultCol = new TableColumn<>("Identified Fault");
        faultCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFaultType().getDisplayName()));
        faultCol.setPrefWidth(140);

        TableColumn<FaultReport, String> confCol = new TableColumn<>("Confidence");
        confCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.1f%%", c.getValue().getConfidence())));
        confCol.setPrefWidth(90);

        TableColumn<FaultReport, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeverity().name()));
        sevCol.setPrefWidth(90);
        sevCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                } else {
                    Label b = new Label(item);
                    b.getStyleClass().add("badge");
                    if (item.equalsIgnoreCase("LOW")) b.getStyleClass().add("badge-healthy");
                    else if (item.equalsIgnoreCase("MEDIUM")) b.getStyleClass().add("badge-warning");
                    else b.getStyleClass().add("badge-faulty");
                    setGraphic(b);
                }
            }
        });

        TableColumn<FaultReport, String> priCol = new TableColumn<>("Priority");
        priCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMaintenancePriority().name()));
        priCol.setPrefWidth(90);

        TableColumn<FaultReport, String> lossCol = new TableColumn<>("Est. Power Loss");
        lossCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.1f W (%.1f%%)", c.getValue().getEstimatedEnergyLossWatts(), c.getValue().getEstimatedEnergyLossPercent())));
        lossCol.setPrefWidth(130);

        TableColumn<FaultReport, String> regionCol = new TableColumn<>("Affected Sector");
        regionCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAffectedRegionLabel()));
        regionCol.setPrefWidth(160);

        TableColumn<FaultReport, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Details");
            {
                btn.getStyleClass().add("btn-outline");
                btn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10 4 10;");
                btn.setOnAction(e -> {
                    FaultReport r = getTableView().getItems().get(getIndex());
                    showReportDetails(r);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        reportTable.getColumns().addAll(List.of(idCol, panelCol, dateCol, faultCol, confCol, sevCol, priCol, lossCol, regionCol, actionCol));

        getChildren().addAll(header, filterCard, reportTable);
    }

    public void loadReports() {
        populatePanelFilter();
        reportData.clear();
        reportData.addAll(reportRepository.findAll());
    }

    private void populatePanelFilter() {
        panelFilter.getItems().clear();
        panelFilter.getItems().add("ALL");
        for (SolarPanel p : panelRepository.findAll()) {
            panelFilter.getItems().add(p.getPanelId());
        }
        panelFilter.setValue("ALL");
    }

    private void applyFilter() {
        String pId = panelFilter.getValue();
        if ("ALL".equalsIgnoreCase(pId)) pId = null;

        FaultType ft = null;
        String ftStr = faultFilter.getValue();
        if (ftStr != null && !ftStr.equalsIgnoreCase("ALL FAULTS")) {
            ft = FaultType.fromString(ftStr);
        }

        Severity sev = null;
        String sStr = severityFilter.getValue();
        if (sStr != null && !sStr.equalsIgnoreCase("ALL SEVERITIES")) {
            sev = Severity.fromString(sStr);
        }

        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();

        List<FaultReport> list = reportRepository.findFiltered(pId, ft, sev, from, to);
        reportData.setAll(list);
    }

    private void showReportDetails(FaultReport report) {
        if (report == null) return;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Diagnostic Inspection Breakdown - Report #" + report.getId());

        VBox content = new VBox(16);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: #ffffff;");

        Label title = new Label("Fault Report #" + report.getId() + " - " + report.getPanelId());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        // Images preview
        HBox imgRow = new HBox(16);
        imgRow.setAlignment(Pos.CENTER);

        ImageView origView = createThumbView(report.getImagePath(), "Original Input");
        ImageView anaView = createThumbView(report.getAnalyzedImagePath(), "Analyzed Localization");
        imgRow.getChildren().addAll(origView, anaView);

        // Details grid
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);

        grid.add(new Label("Identified Anomaly:"), 0, 0);
        Label ftLbl = new Label(report.getFaultType().getDisplayName());
        ftLbl.setStyle("-fx-font-weight: 800; -fx-text-fill: #0f172a;");
        grid.add(ftLbl, 1, 0);

        grid.add(new Label("AI Confidence:"), 2, 0);
        grid.add(new Label(String.format("%.1f%%", report.getConfidence())), 3, 0);

        grid.add(new Label("Risk Severity:"), 0, 1);
        grid.add(new Label(report.getSeverity().name()), 1, 1);

        grid.add(new Label("Maintenance Priority:"), 2, 1);
        grid.add(new Label(report.getMaintenancePriority().name()), 3, 1);

        grid.add(new Label("Estimated Power Loss:"), 0, 2);
        grid.add(new Label(String.format("%.1f W (%.1f%% derating)", report.getEstimatedEnergyLossWatts(), report.getEstimatedEnergyLossPercent())), 1, 2);

        grid.add(new Label("Localized Affected Region:"), 2, 2);
        grid.add(new Label(report.getAffectedRegionLabel()), 3, 2);

        if (report.getTemperature() != null) {
            grid.add(new Label("Telemetry:"), 0, 3);
            grid.add(new Label(String.format("T: %.1f°C, V: %.1fV, I: %.1fA", report.getTemperature(), report.getVoltage(), report.getCurrent())), 1, 3);
        }

        VBox notesBox = new VBox(4);
        Label notesTitle = new Label("AI / Diagnostic Technical Notes:");
        notesTitle.setStyle("-fx-font-weight: 700; -fx-text-fill: #475569;");
        Label notes = new Label(report.getAnalysisNotes() != null ? report.getAnalysisNotes() : "No technical notes logged.");
        notes.setWrapText(true);
        notesBox.getChildren().addAll(notesTitle, notes);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-primary");
        closeBtn.setOnAction(e -> dialog.close());
        HBox bBox = new HBox(closeBtn);
        bBox.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(title, new Separator(), imgRow, grid, new Separator(), notesBox, new Separator(), bBox);

        Scene scene = new Scene(new ScrollPane(content), 640, 560);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private ImageView createThumbView(String path, String label) {
        ImageView iv = new ImageView();
        iv.setFitWidth(260);
        iv.setFitHeight(180);
        iv.setPreserveRatio(true);

        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) {
                iv.setImage(new Image(f.toURI().toString()));
            }
        }
        return iv;
    }
}
