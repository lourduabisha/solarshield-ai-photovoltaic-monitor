package com.solarshield.ui;

import com.solarshield.database.SolarPanelRepository;
import com.solarshield.model.*;
import com.solarshield.util.AlertUtils;
import com.solarshield.util.DateUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Solar Panel Fleet Management View.
 * Displays array modules, real-time statuses, and allows registering new solar modules into the database.
 */
public class PanelView extends VBox {

    private final SolarPanelRepository panelRepository;
    private final Consumer<SolarPanel> onDetectPanel;
    private final Consumer<SolarPanel> onMaintenancePanel;

    private TableView<SolarPanel> panelTable;
    private ObservableList<SolarPanel> panelData;
    private TextField searchField;

    public PanelView(SolarPanelRepository panelRepository,
                     Consumer<SolarPanel> onDetectPanel,
                     Consumer<SolarPanel> onMaintenancePanel) {
        this.panelRepository = panelRepository;
        this.onDetectPanel = onDetectPanel;
        this.onMaintenancePanel = onMaintenancePanel;

        setSpacing(18);
        setPadding(new Insets(24));
        setStyle("-fx-background-color: transparent;");

        initUI();
        loadPanels();
    }

    private void initUI() {
        // Header Row
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label title = new Label("SOLAR PANEL FLEET MANAGEMENT");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("Register, monitor, and configure photovoltaic array modules in the farm array");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addPanelBtn = new Button("✚ Add Solar Panel");
        addPanelBtn.getStyleClass().add("btn-primary");
        addPanelBtn.setOnAction(e -> showAddPanelDialog());

        header.getChildren().addAll(titleBox, spacer, addPanelBtn);

        // Filter Bar
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(12, 16, 12, 16));
        filterBar.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10px; -fx-border-color: #e2e8f0; -fx-border-radius: 10px;");

        Label searchLbl = new Label("Search Panels:");
        searchLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #334155;");

        searchField = new TextField();
        searchField.setPromptText("Filter by ID, name, location...");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, oldV, newV) -> filterPanels(newV));

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        Button reloadBtn = new Button("🔄 Refresh List");
        reloadBtn.getStyleClass().add("btn-outline");
        reloadBtn.setOnAction(e -> loadPanels());

        filterBar.getChildren().addAll(searchLbl, searchField, filterSpacer, reloadBtn);

        // Table
        panelTable = new TableView<>();
        panelData = FXCollections.observableArrayList();
        panelTable.setItems(panelData);
        VBox.setVgrow(panelTable, Priority.ALWAYS);
        panelTable.setPlaceholder(new Label("No solar panels registered yet. Click '+ Add Solar Panel' above."));

        // Column 1: Panel ID
        TableColumn<SolarPanel, String> idCol = new TableColumn<>("Panel ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPanelId()));
        idCol.setPrefWidth(90);

        // Column 2: Name
        TableColumn<SolarPanel, String> nameCol = new TableColumn<>("Panel Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(140);

        // Column 3: Location
        TableColumn<SolarPanel, String> locCol = new TableColumn<>("Location Sector");
        locCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLocation()));
        locCol.setPrefWidth(160);

        // Column 4: Rated Power
        TableColumn<SolarPanel, String> powerCol = new TableColumn<>("Rated Power");
        powerCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRatedPower() + " W"));
        powerCol.setPrefWidth(100);

        // Column 5: Status
        TableColumn<SolarPanel, String> statusCol = new TableColumn<>("Grid Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().getDisplayName()));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    if (item.equalsIgnoreCase("Healthy")) badge.getStyleClass().add("badge-healthy");
                    else if (item.equalsIgnoreCase("Warning")) badge.getStyleClass().add("badge-warning");
                    else if (item.equalsIgnoreCase("Critical")) badge.getStyleClass().add("badge-critical");
                    else badge.getStyleClass().add("badge-faulty");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // Column 6: Current Fault
        TableColumn<SolarPanel, String> faultCol = new TableColumn<>("Current Fault");
        faultCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCurrentFault().getDisplayName()));
        faultCol.setPrefWidth(130);

        // Column 7: Severity
        TableColumn<SolarPanel, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeverity().name()));
        sevCol.setPrefWidth(90);

        // Column 8: Priority
        TableColumn<SolarPanel, String> priCol = new TableColumn<>("Priority");
        priCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMaintenancePriority().name()));
        priCol.setPrefWidth(90);

        // Column 9: Last Inspection
        TableColumn<SolarPanel, String> dateCol = new TableColumn<>("Last Inspection");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.formatDisplayDateTime(c.getValue().getLastInspectionDate())));
        dateCol.setPrefWidth(140);

        // Column 10: Actions
        TableColumn<SolarPanel, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(210);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            private final Button detectBtn = new Button("Detect");
            private final Button maintBtn = new Button("Maint");
            private final HBox box = new HBox(6, viewBtn, detectBtn, maintBtn);

            {
                viewBtn.getStyleClass().add("btn-outline");
                viewBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                detectBtn.getStyleClass().add("btn-primary");
                detectBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                maintBtn.getStyleClass().add("btn-outline");
                maintBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");

                viewBtn.setOnAction(e -> {
                    SolarPanel p = getTableView().getItems().get(getIndex());
                    showPanelDetails(p);
                });
                detectBtn.setOnAction(e -> {
                    SolarPanel p = getTableView().getItems().get(getIndex());
                    onDetectPanel.accept(p);
                });
                maintBtn.setOnAction(e -> {
                    SolarPanel p = getTableView().getItems().get(getIndex());
                    onMaintenancePanel.accept(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        panelTable.getColumns().addAll(List.of(idCol, nameCol, locCol, powerCol, statusCol, faultCol, sevCol, priCol, dateCol, actionCol));

        getChildren().addAll(header, filterBar, panelTable);
    }

    public void loadPanels() {
        panelData.clear();
        panelData.addAll(panelRepository.findAll());
    }

    private void filterPanels(String query) {
        if (query == null || query.trim().isEmpty()) {
            loadPanels();
            return;
        }
        String q = query.toLowerCase().trim();
        List<SolarPanel> filtered = panelRepository.findAll().stream()
                .filter(p -> p.getPanelId().toLowerCase().contains(q) ||
                             p.getName().toLowerCase().contains(q) ||
                             p.getLocation().toLowerCase().contains(q) ||
                             p.getPanelType().toLowerCase().contains(q) ||
                             p.getStatus().name().toLowerCase().contains(q))
                .toList();
        panelData.setAll(filtered);
    }

    private void showPanelDetails(SolarPanel panel) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Solar Module Details - " + panel.getPanelId());

        VBox content = new VBox(14);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: #ffffff;");

        Label title = new Label(panel.getPanelId() + ": " + panel.getName());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);

        addGridRow(grid, 0, "Location Sector:", panel.getLocation());
        addGridRow(grid, 1, "Panel Technology:", panel.getPanelType());
        addGridRow(grid, 2, "Rated Nominal Power:", panel.getRatedPower() + " W");
        addGridRow(grid, 3, "Installation Date:", DateUtils.formatDate(panel.getInstallationDate()));
        addGridRow(grid, 4, "Current Operational Status:", panel.getStatus().getDisplayName());
        addGridRow(grid, 5, "Last Diagnostic Inspection:", DateUtils.formatDisplayDateTime(panel.getLastInspectionDate()));
        addGridRow(grid, 6, "Identified Anomaly:", panel.getCurrentFault().getDisplayName());
        addGridRow(grid, 7, "Risk Severity Level:", panel.getSeverity().name());
        addGridRow(grid, 8, "Maintenance Priority:", panel.getMaintenancePriority().name());
        addGridRow(grid, 9, "GPS Coordinates:", String.format("%.4f, %.4f", panel.getLatitude(), panel.getLongitude()));

        HBox btns = new HBox(12);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Button close = new Button("Close");
        close.getStyleClass().add("btn-outline");
        close.setOnAction(e -> dialog.close());

        Button detect = new Button("🔍 Run AI Fault Detection");
        detect.getStyleClass().add("btn-primary");
        detect.setOnAction(e -> {
            dialog.close();
            onDetectPanel.accept(panel);
        });

        btns.getChildren().addAll(close, detect);
        content.getChildren().addAll(title, new Separator(), grid, new Separator(), btns);

        Scene scene = new Scene(content, 460, 420);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void addGridRow(GridPane grid, int row, String label, String value) {
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: 700; -fx-text-fill: #475569;");
        Label v = new Label(value != null ? value : "N/A");
        v.setStyle("-fx-text-fill: #0f172a;");
        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    private void showAddPanelDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Register New Solar Panel Module");

        VBox content = new VBox(14);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: #ffffff;");

        Label title = new Label("Register Solar Panel");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);

        // Fields
        TextField idInput = new TextField("SP-00" + (panelData.size() + 1));
        TextField nameInput = new TextField("Array Module " + (panelData.size() + 1));
        TextField locInput = new TextField("Zone D - South Array");
        DatePicker datePicker = new DatePicker(LocalDate.now());

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Monocrystalline PERC", "Polycrystalline High-Yield", "Bifacial Dual-Glass", "Thin-Film CdTe");
        typeCombo.setValue("Monocrystalline PERC");

        TextField powerInput = new TextField("420.0");
        TextField latInput = new TextField("34.0535");
        TextField lonInput = new TextField("-118.2445");

        grid.add(new Label("Panel ID: *"), 0, 0); grid.add(idInput, 1, 0);
        grid.add(new Label("Module Name: *"), 0, 1); grid.add(nameInput, 1, 1);
        grid.add(new Label("Location Sector: *"), 0, 2); grid.add(locInput, 1, 2);
        grid.add(new Label("Installation Date:"), 0, 3); grid.add(datePicker, 1, 3);
        grid.add(new Label("Panel Technology:"), 0, 4); grid.add(typeCombo, 1, 4);
        grid.add(new Label("Rated Power (W):"), 0, 5); grid.add(powerInput, 1, 5);
        grid.add(new Label("Latitude:"), 0, 6); grid.add(latInput, 1, 6);
        grid.add(new Label("Longitude:"), 0, 7); grid.add(lonInput, 1, 7);

        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-outline");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("Save Solar Panel");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            String pId = idInput.getText().trim();
            String pName = nameInput.getText().trim();
            String pLoc = locInput.getText().trim();

            if (pId.isEmpty() || pName.isEmpty() || pLoc.isEmpty()) {
                AlertUtils.showError("Validation Error", "Missing Required Fields", "Panel ID, Name, and Location are required.");
                return;
            }

            double power = 400.0;
            try { power = Double.parseDouble(powerInput.getText().trim()); } catch (Exception ignored) {}
            double lat = 0.0;
            try { lat = Double.parseDouble(latInput.getText().trim()); } catch (Exception ignored) {}
            double lon = 0.0;
            try { lon = Double.parseDouble(lonInput.getText().trim()); } catch (Exception ignored) {}

            SolarPanel newPanel = new SolarPanel(
                    0, pId, pName, pLoc, datePicker.getValue(), typeCombo.getValue(),
                    power, lat, lon, PanelStatus.HEALTHY, null, FaultType.HEALTHY,
                    Severity.LOW, MaintenancePriority.LOW, LocalDateTime.now()
            );

            boolean saved = panelRepository.save(newPanel);
            if (saved) {
                dialog.close();
                loadPanels();
                AlertUtils.showInfo("Success", "Panel Registered", "Solar panel " + pId + " has been added to the farm database.");
            } else {
                AlertUtils.showError("Database Error", "Failed to Save", "Could not save panel. Ensure Panel ID is unique.");
            }
        });

        btnBox.getChildren().addAll(cancelBtn, saveBtn);
        content.getChildren().addAll(title, new Separator(), grid, new Separator(), btnBox);

        Scene scene = new Scene(content, 440, 480);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}
