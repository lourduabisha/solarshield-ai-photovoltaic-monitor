package com.solarshield.ui;

import com.solarshield.database.SolarPanelRepository;
import com.solarshield.model.*;
import com.solarshield.service.MaintenanceService;
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
import java.util.List;

/**
 * Maintenance Work Orders and Field Operations View.
 * Enables scheduling technician interventions, tracking repair statuses, and completing service orders.
 */
public class MaintenanceView extends VBox {

    private final MaintenanceService maintenanceService;
    private final SolarPanelRepository panelRepository;

    private TableView<Maintenance> taskTable;
    private ObservableList<Maintenance> taskData;

    private ComboBox<String> statusFilter;
    private ComboBox<String> priorityFilter;

    public MaintenanceView(MaintenanceService maintenanceService, SolarPanelRepository panelRepository) {
        this.maintenanceService = maintenanceService;
        this.panelRepository = panelRepository;

        setSpacing(18);
        setPadding(new Insets(24));
        setStyle("-fx-background-color: transparent;");

        initUI();
        loadTasks();
    }

    private void initUI() {
        // 1. Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label title = new Label("MAINTENANCE OPERATIONS & DISPATCH");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("Field service work orders, cleaning schedules, and component replacement tracking");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button newTaskBtn = new Button("✚ Create Maintenance Task");
        newTaskBtn.getStyleClass().add("btn-primary");
        newTaskBtn.setOnAction(e -> showCreateTaskDialog(null, null));

        header.getChildren().addAll(titleBox, spacer, newTaskBtn);

        // 2. Filter Bar
        HBox filterBar = new HBox(14);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(12, 16, 12, 16));
        filterBar.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10px; -fx-border-color: #e2e8f0; -fx-border-radius: 10px;");

        VBox sBox = new VBox(4);
        sBox.getChildren().addAll(new Label("Status Filter:"), statusFilter = new ComboBox<>());
        statusFilter.setPrefWidth(140);
        statusFilter.getItems().add("ALL");
        for (MaintenanceStatus s : MaintenanceStatus.values()) statusFilter.getItems().add(s.name());
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> applyFilter());

        VBox pBox = new VBox(4);
        pBox.getChildren().addAll(new Label("Priority Filter:"), priorityFilter = new ComboBox<>());
        priorityFilter.setPrefWidth(140);
        priorityFilter.getItems().add("ALL");
        for (MaintenancePriority p : MaintenancePriority.values()) priorityFilter.getItems().add(p.name());
        priorityFilter.setValue("ALL");
        priorityFilter.setOnAction(e -> applyFilter());

        Region fSpacer = new Region();
        HBox.setHgrow(fSpacer, Priority.ALWAYS);

        Button refreshBtn = new Button("🔄 Refresh Tasks");
        refreshBtn.getStyleClass().add("btn-outline");
        refreshBtn.setOnAction(e -> loadTasks());

        filterBar.getChildren().addAll(sBox, pBox, fSpacer, refreshBtn);

        // 3. Table
        taskTable = new TableView<>();
        taskData = FXCollections.observableArrayList();
        taskTable.setItems(taskData);
        VBox.setVgrow(taskTable, Priority.ALWAYS);
        taskTable.setPlaceholder(new Label("No maintenance tasks found matching filters."));

        TableColumn<Maintenance, String> idCol = new TableColumn<>("Order #");
        idCol.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getId()));
        idCol.setPrefWidth(80);

        TableColumn<Maintenance, String> panelCol = new TableColumn<>("Panel ID");
        panelCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPanelId()));
        panelCol.setPrefWidth(90);

        TableColumn<Maintenance, String> typeCol = new TableColumn<>("Work Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMaintenanceType()));
        typeCol.setPrefWidth(180);

        TableColumn<Maintenance, String> techCol = new TableColumn<>("Assigned Technician");
        techCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTechnician()));
        techCol.setPrefWidth(160);

        TableColumn<Maintenance, String> dateCol = new TableColumn<>("Scheduled Date");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.formatDate(c.getValue().getScheduledDate())));
        dateCol.setPrefWidth(120);

        TableColumn<Maintenance, String> priCol = new TableColumn<>("Priority");
        priCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPriority().name()));
        priCol.setPrefWidth(90);

        TableColumn<Maintenance, String> statusCol = new TableColumn<>("Lifecycle Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        statusCol.setPrefWidth(120);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                } else {
                    Label b = new Label(item);
                    b.getStyleClass().add("badge");
                    if (item.equalsIgnoreCase("COMPLETED")) b.getStyleClass().add("badge-healthy");
                    else if (item.equalsIgnoreCase("IN_PROGRESS")) b.getStyleClass().add("badge-info");
                    else if (item.equalsIgnoreCase("SCHEDULED")) b.getStyleClass().add("badge-info");
                    else b.getStyleClass().add("badge-warning");
                    setGraphic(b);
                }
            }
        });

        TableColumn<Maintenance, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(180);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button completeBtn = new Button("Mark Done");
            private final Button editBtn = new Button("Edit");
            private final HBox box = new HBox(6, completeBtn, editBtn);

            {
                completeBtn.getStyleClass().add("btn-success");
                completeBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                editBtn.getStyleClass().add("btn-outline");
                editBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");

                completeBtn.setOnAction(e -> {
                    Maintenance m = getTableView().getItems().get(getIndex());
                    handleMarkComplete(m);
                });
                editBtn.setOnAction(e -> {
                    Maintenance m = getTableView().getItems().get(getIndex());
                    showEditTaskDialog(m);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Maintenance m = getTableView().getItems().get(getIndex());
                    completeBtn.setDisable(m.getStatus() == MaintenanceStatus.COMPLETED);
                    setGraphic(box);
                }
            }
        });

        taskTable.getColumns().addAll(List.of(idCol, panelCol, typeCol, techCol, dateCol, priCol, statusCol, actionCol));

        getChildren().addAll(header, filterBar, taskTable);
    }

    public void loadTasks() {
        taskData.clear();
        taskData.addAll(maintenanceService.getAllTasks());
    }

    private void applyFilter() {
        MaintenanceStatus s = null;
        String sStr = statusFilter.getValue();
        if (sStr != null && !"ALL".equalsIgnoreCase(sStr)) {
            s = MaintenanceStatus.fromString(sStr);
        }

        MaintenancePriority p = null;
        String pStr = priorityFilter.getValue();
        if (pStr != null && !"ALL".equalsIgnoreCase(pStr)) {
            p = MaintenancePriority.fromString(pStr);
        }

        List<Maintenance> filtered = maintenanceService.getFilteredTasks(null, s, p);
        taskData.setAll(filtered);
    }

    private void handleMarkComplete(Maintenance task) {
        boolean ok = AlertUtils.showConfirmation("Complete Maintenance",
                "Mark Task #" + task.getId() + " as COMPLETED?",
                "This will resolve the maintenance work order and restore panel " + task.getPanelId() + " status.");
        if (ok) {
            maintenanceService.updateStatus(task.getId(), MaintenanceStatus.COMPLETED, task.getPanelId());
            loadTasks();
            AlertUtils.showInfo("Task Completed", "Service Resolved", "Maintenance order #" + task.getId() + " marked as completed.");
        }
    }

    public void showCreateTaskDialog(String preSelectedPanel, Integer faultReportId) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Schedule Maintenance Task");

        VBox content = new VBox(14);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: #ffffff;");

        Label title = new Label("Create Maintenance Work Order");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);

        ComboBox<String> panelCombo = new ComboBox<>();
        for (SolarPanel p : panelRepository.findAll()) {
            panelCombo.getItems().add(p.getPanelId());
        }
        if (preSelectedPanel != null && !preSelectedPanel.isEmpty()) {
            panelCombo.setValue(preSelectedPanel);
        } else if (!panelCombo.getItems().isEmpty()) {
            panelCombo.setValue(panelCombo.getItems().get(0));
        }

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(
                "Surface De-soiling & Wash",
                "Thermal Bypass Diode Service",
                "Cell Wafer Solder Reflow",
                "Module Replacement",
                "Vegetation / Obstacle Trimming",
                "Inverter / Junction Box Inspection",
                "Frame Structural Realignment"
        );
        typeCombo.setValue("Surface De-soiling & Wash");

        TextField techField = new TextField("Alex Vance (Lead Tech)");
        DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(2));

        ComboBox<MaintenancePriority> priorityCombo = new ComboBox<>();
        priorityCombo.getItems().addAll(MaintenancePriority.values());
        priorityCombo.setValue(MaintenancePriority.HIGH);

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Enter field instructions and technician notes...");
        notesArea.setPrefRowCount(3);

        grid.add(new Label("Target Panel: *"), 0, 0); grid.add(panelCombo, 1, 0);
        grid.add(new Label("Work Order Type: *"), 0, 1); grid.add(typeCombo, 1, 1);
        grid.add(new Label("Assigned Technician: *"), 0, 2); grid.add(techField, 1, 2);
        grid.add(new Label("Scheduled Date: *"), 0, 3); grid.add(datePicker, 1, 3);
        grid.add(new Label("Urgency Priority: *"), 0, 4); grid.add(priorityCombo, 1, 4);
        grid.add(new Label("Technician Notes:"), 0, 5); grid.add(notesArea, 1, 5);

        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-outline");
        cancelBtn.setOnAction(e -> dialog.close());

        Button saveBtn = new Button("Dispatch Order");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            String pId = panelCombo.getValue();
            String tech = techField.getText().trim();
            if (pId == null || tech.isEmpty()) {
                AlertUtils.showError("Validation Error", "Missing Fields", "Please select a panel and enter a technician name.");
                return;
            }

            boolean saved = maintenanceService.createMaintenanceTask(
                    pId, faultReportId, tech, typeCombo.getValue(),
                    datePicker.getValue(), priorityCombo.getValue(), notesArea.getText().trim()
            );

            if (saved) {
                dialog.close();
                loadTasks();
                AlertUtils.showInfo("Order Dispatched", "Work Order Scheduled", "Maintenance task created for panel " + pId);
            } else {
                AlertUtils.showError("Database Error", "Failed", "Could not save maintenance task.");
            }
        });

        btnBox.getChildren().addAll(cancelBtn, saveBtn);
        content.getChildren().addAll(title, new Separator(), grid, new Separator(), btnBox);

        Scene scene = new Scene(content, 480, 460);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showEditTaskDialog(Maintenance task) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Edit Task #" + task.getId());

        VBox content = new VBox(14);
        content.setPadding(new Insets(24));
        content.setStyle("-fx-background-color: #ffffff;");

        Label title = new Label("Edit Work Order #" + task.getId());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);

        TextField techField = new TextField(task.getTechnician());
        DatePicker datePicker = new DatePicker(task.getScheduledDate());

        ComboBox<MaintenanceStatus> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll(MaintenanceStatus.values());
        statusCombo.setValue(task.getStatus());

        ComboBox<MaintenancePriority> priCombo = new ComboBox<>();
        priCombo.getItems().addAll(MaintenancePriority.values());
        priCombo.setValue(task.getPriority());

        TextArea notesArea = new TextArea(task.getNotes());
        notesArea.setPrefRowCount(3);

        grid.add(new Label("Assigned Technician:"), 0, 0); grid.add(techField, 1, 0);
        grid.add(new Label("Scheduled Date:"), 0, 1); grid.add(datePicker, 1, 1);
        grid.add(new Label("Task Status:"), 0, 2); grid.add(statusCombo, 1, 2);
        grid.add(new Label("Priority:"), 0, 3); grid.add(priCombo, 1, 3);
        grid.add(new Label("Notes:"), 0, 4); grid.add(notesArea, 1, 4);

        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-outline");
        cancel.setOnAction(e -> dialog.close());

        Button save = new Button("Save Changes");
        save.getStyleClass().add("btn-primary");
        save.setOnAction(e -> {
            task.setTechnician(techField.getText().trim());
            task.setScheduledDate(datePicker.getValue());
            task.setStatus(statusCombo.getValue());
            task.setPriority(priCombo.getValue());
            task.setNotes(notesArea.getText().trim());

            boolean ok = maintenanceService.updateTask(task);
            if (ok) {
                dialog.close();
                loadTasks();
            }
        });

        btnBox.getChildren().addAll(cancel, save);
        content.getChildren().addAll(title, new Separator(), grid, new Separator(), btnBox);

        Scene scene = new Scene(content, 460, 420);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}
