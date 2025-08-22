package application;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.time.LocalDate;
import java.time.Period;

public class dashboardController {

    @FXML private FlowPane chartsContainer;
    @FXML private Label messagesLabel;
    @FXML private Label salesLabel;
    @FXML private Label notificationsLabel;
    @FXML private Label scheduledLabel;
    @FXML private Label messagesCount, salesCount, notificationsCount, scheduledCount;
    @FXML private Label serviceInfoLabel;

    // Informations de connexion à la base de données
    private final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private final String DB_USER = "marco";
    private final String DB_PASSWORD = "29Papa278.";
    
    private String currentService;
    private List<String> availableTables;
    
    @FXML
    public void initialize() {
        currentService = UserSession.getCurrentService();
        availableTables = ServicePermissions.getTablesForService(currentService);
        
        // Configurer le conteneur de graphiques
        setupChartsContainer();
        
        try {
            // Initialiser les informations de service
            initializeServiceInfo();
            
            // Charger les données depuis la base de données
            loadDataFromDatabase();
            
            // Mettre à jour les statistiques dans les boîtes
            updateStats();
            
            // Créer les graphiques initiaux selon le service
            createServiceSpecificCharts();
            
        } catch (SQLException e) {
            showErrorAlert("Erreur de connexion à la base de données", e.getMessage());
        }
    }
    
    /**
     * Configure le conteneur de graphiques pour une meilleure visibilité
     */
    private void setupChartsContainer() {
        if (chartsContainer != null) {
            chartsContainer.setHgap(15);
            chartsContainer.setVgap(15);
            chartsContainer.setPadding(new Insets(10));
            chartsContainer.setAlignment(Pos.TOP_LEFT);
            
            // Assurer que le conteneur peut s'agrandir
            chartsContainer.setPrefWrapLength(Region.USE_COMPUTED_SIZE);
        }
    }
    
    private void initializeServiceInfo() {
        if (serviceInfoLabel != null) {
            serviceInfoLabel.setText("Service: " + currentService + " | Tables disponibles: " + availableTables.size());
        }
    }
    
    private void loadDataFromDatabase() throws SQLException {
        // Statistiques basées sur les tables communes
        Map<String, Integer> stats = new HashMap<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Statistiques de base depuis identite_personnelle (commune à tous)
            if (availableTables.contains("identite_personnelle")) {
                stats.put("total_personnel", countRecords(conn, "SELECT COUNT(*) FROM identite_personnelle"));
                stats.put("feminin_personnel", countRecords(conn, "SELECT COUNT(*) FROM identite_personnelle WHERE sexe = 'Femme'"));
            }
            
            // Statistiques spécifiques par service
            switch (currentService) {
                case "Logistique":
                    loadLogistiqueStats(conn, stats);
                    break;
                case "Opérations":
                    loadOperationsStats(conn, stats);
                    break;
                case "Ressources Humaines":
                    loadRhStats(conn, stats);
                    break;
                default:
                    loadDefaultStats(conn, stats);
            }
        }
        
        updateStatBoxes(stats);
    }
    
    private void loadLogistiqueStats(Connection conn, Map<String, Integer> stats) throws SQLException {
        if (availableTables.contains("parametres_corporels")) {
            stats.put("parametres_definis", countRecords(conn, "SELECT COUNT(*) FROM parametres_corporels"));
        }
        if (availableTables.contains("dotation_particuliere")) {
            stats.put("dotations_actives", countRecords(conn, "SELECT COUNT(DISTINCT matricule) FROM dotation_particuliere"));
        }
        if (availableTables.contains("maintenance")) {
            stats.put("maintenances_planifiees", countRecords(conn, "SELECT COUNT(*) FROM maintenance"));
        }
    }
    
    private void loadOperationsStats(Connection conn, Map<String, Integer> stats) throws SQLException {
        if (availableTables.contains("operation")) {
            stats.put("operations_total", countRecords(conn, "SELECT COUNT(*) FROM operation"));
            stats.put("operations_recentes", countRecords(conn, "SELECT COUNT(*) FROM operation WHERE annee >= YEAR(CURDATE()) - 1"));
        }
        if (availableTables.contains("personnel_naviguant")) {
            stats.put("personnel_naviguant", countRecords(conn, "SELECT COUNT(*) FROM personnel_naviguant"));
        }
        if (availableTables.contains("punition")) {
            stats.put("punitions_actives", countRecords(conn, "SELECT COUNT(*) FROM punition WHERE date >= DATE_SUB(CURDATE(), INTERVAL 1 YEAR)"));
        }
    }
    
    private void loadRhStats(Connection conn, Map<String, Integer> stats) throws SQLException {
        if (availableTables.contains("grade_actuel")) {
            stats.put("grades_officiers", countRecords(conn, "SELECT COUNT(*) FROM grade_actuel WHERE rang LIKE '%Lieutenant%' OR rang LIKE '%Capitaine%' OR rang LIKE '%Colonel%'"));
        }
        if (availableTables.contains("ecole_militaire")) {
            stats.put("formations_militaires", countRecords(conn, "SELECT COUNT(DISTINCT matricule) FROM ecole_militaire"));
        }
        if (availableTables.contains("decoration")) {
            stats.put("decorations_total", countRecords(conn, "SELECT COUNT(*) FROM decoration"));
        }
        if (availableTables.contains("historique_grades")) {
            stats.put("promotions_recentes", countRecords(conn, "SELECT COUNT(*) FROM historique_grades WHERE date >= DATE_SUB(CURDATE(), INTERVAL 1 YEAR)"));
        }
    }
    
    private void loadDefaultStats(Connection conn, Map<String, Integer> stats) throws SQLException {
        // Statistiques par défaut pour l'admin ou autres
        stats.put("total_tables", availableTables.size());
        stats.put("mises_a_jour", countRecords(conn, "SELECT COUNT(*) FROM historique_mises_a_jour_enhanced"));
    }
    
    private int countRecords(Connection conn, String query) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
    
    private void updateStatBoxes(Map<String, Integer> stats) {
        switch (currentService) {
            case "Logistique":
                messagesLabel.setText("Personnel total");
                messagesCount.setText(String.valueOf(stats.getOrDefault("total_personnel", 0)));
                
                salesLabel.setText("Paramètres corporels");
                salesCount.setText(String.valueOf(stats.getOrDefault("parametres_definis", 0)));
                
                notificationsLabel.setText("Dotations actives");
                notificationsCount.setText(String.valueOf(stats.getOrDefault("dotations_actives", 0)));
                
                scheduledLabel.setText("Maintenances");
                scheduledCount.setText(String.valueOf(stats.getOrDefault("maintenances_planifiees", 0)));
                break;
                
            case "Opérations":
                messagesLabel.setText("Personnel total");
                messagesCount.setText(String.valueOf(stats.getOrDefault("total_personnel", 0)));
                
                salesLabel.setText("Opérations totales");
                salesCount.setText(String.valueOf(stats.getOrDefault("operations_total", 0)));
                
                notificationsLabel.setText("Personnel naviguant");
                notificationsCount.setText(String.valueOf(stats.getOrDefault("personnel_naviguant", 0)));
                
                scheduledLabel.setText("Opérations récentes");
                scheduledCount.setText(String.valueOf(stats.getOrDefault("operations_recentes", 0)));
                break;
                
            case "Ressources Humaines":
                messagesLabel.setText("Personnel total");
                messagesCount.setText(String.valueOf(stats.getOrDefault("total_personnel", 0)));
                
                salesLabel.setText("Officiers");
                salesCount.setText(String.valueOf(stats.getOrDefault("grades_officiers", 0)));
                
                notificationsLabel.setText("Formations militaires");
                notificationsCount.setText(String.valueOf(stats.getOrDefault("formations_militaires", 0)));
                
                scheduledLabel.setText("Décorations");
                scheduledCount.setText(String.valueOf(stats.getOrDefault("decorations_total", 0)));
                break;
                
            default:
                messagesLabel.setText("Personnel total");
                messagesCount.setText(String.valueOf(stats.getOrDefault("total_personnel", 0)));
                
                salesLabel.setText("Tables disponibles");
                salesCount.setText(String.valueOf(stats.getOrDefault("total_tables", 0)));
                
                notificationsLabel.setText("Mises à jour");
                notificationsCount.setText(String.valueOf(stats.getOrDefault("mises_a_jour", 0)));
                
                scheduledLabel.setText("Personnel féminin");
                scheduledCount.setText(String.valueOf(stats.getOrDefault("feminin_personnel", 0)));
        }
    }

    private void updateStats() {
        // Cette méthode est appelée après updateStatBoxes, donc pas besoin de redondance
    }

    private void createServiceSpecificCharts() throws SQLException {
        // Nettoyer le conteneur avant d'ajouter de nouveaux graphiques
        Platform.runLater(() -> chartsContainer.getChildren().clear());
        
        switch (currentService) {
            case "Logistique":
                createLogistiqueCharts();
                break;
            case "Opérations":
                createOperationsCharts();
                break;
            case "Ressources Humaines":
                createRhCharts();
                break;
            default:
                createDefaultCharts();
        }
    }
    
    private void createLogistiqueCharts() throws SQLException {
        // Graphique des tailles si parametres_corporels disponible
        if (availableTables.contains("parametres_corporels")) {
            addDistributionChart("parametres_corporels", "taille", "Distribution des Tailles", "Camembert");
        }
        
        // Graphique des dotations par année si disponible
        if (availableTables.contains("dotation_particuliere")) {
            addTemporalChart("dotation_particuliere", "annee", "Dotations par Année", "Barre");
        }
        
        // Graphique des maintenances par type si disponible
        if (availableTables.contains("maintenance")) {
            addDistributionChart("maintenance", "type_maintenance", "Maintenances par Type", "Barre");
        }
        
        // Graphique de base sur le sexe (table commune)
        if (availableTables.contains("identite_personnelle")) {
            addDistributionChart("identite_personnelle", "sexe", "Répartition par Sexe", "Camembert");
        }
    }
    
    private void createOperationsCharts() throws SQLException {
        // Graphique des opérations par type
        if (availableTables.contains("operation")) {
            addDistributionChart("operation", "type", "Opérations par Type", "Barre");
            addTemporalChart("operation", "annee", "Opérations par Année", "Ligne");
        }
        
        // Graphique du personnel naviguant par qualification
        if (availableTables.contains("personnel_naviguant")) {
            addDistributionChart("personnel_naviguant", "qualification_type", "Qualifications Personnel Naviguant", "Camembert");
        }
        
        // Graphique des langues parlées
        if (availableTables.contains("langue")) {
            addDistributionChart("langue", "langue", "Langues Parlées", "Barre");
        }
        
        // Graphique de base sur les grades (table commune)
        if (availableTables.contains("grade_actuel")) {
            addDistributionChart("grade_actuel", "rang", "Répartition par Grade", "Barre");
        }
    }
    
    private void createRhCharts() throws SQLException {
        // Graphique des grades
        if (availableTables.contains("grade_actuel")) {
            addDistributionChart("grade_actuel", "rang", "Répartition par Grade", "Barre");
        }
        
        // Graphique des écoles de formation
        if (availableTables.contains("ecole_formation_initiale")) {
            addDistributionChart("ecole_formation_initiale", "nom_ecole", "Écoles de Formation", "Camembert");
        }
        
        // Graphique des décorations par type
        if (availableTables.contains("decoration")) {
            addDistributionChart("decoration", "type", "Décorations par Type", "Barre");
        }
        
        // Graphique de l'évolution des grades dans le temps
        if (availableTables.contains("historique_grades")) {
            addTemporalChart("historique_grades", "YEAR(date)", "Évolution des Promotions", "Ligne");
        }
    }
    
    private void createDefaultCharts() throws SQLException {
        // Graphiques par défaut basés sur les tables communes
        if (availableTables.contains("identite_personnelle")) {
            addDistributionChart("identite_personnelle", "sexe", "Répartition par Sexe", "Camembert");
        }
        
        if (availableTables.contains("identite_culturelle")) {
            addDistributionChart("identite_culturelle", "region_origine", "Répartition par Région", "Barre");
        }
        
        if (availableTables.contains("grade_actuel")) {
            addDistributionChart("grade_actuel", "rang", "Répartition par Grade", "Barre");
        }
    }
    
    private void addDistributionChart(String tableName, String column, String title, String chartType) throws SQLException {
        Map<String, Integer> data = getDistributionData(tableName, column);
        
        if (data.isEmpty()) {
            return; // Pas de données à afficher
        }
        
        Platform.runLater(() -> {
            try {
                Chart chart = null;
                switch (chartType) {
                    case "Camembert":
                        chart = createPieChart(data, title);
                        break;
                    case "Barre":
                        chart = createBarChart(data, title, column);
                        break;
                    default:
                        chart = createBarChart(data, title, column);
                }
                
                if (chart != null) {
                    addChartToContainer(chart);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void addTemporalChart(String tableName, String timeColumn, String title, String chartType) throws SQLException {
        Map<String, Integer> data = getTemporalData(tableName, timeColumn);
        
        if (data.isEmpty()) {
            return;
        }
        
        Platform.runLater(() -> {
            try {
                Chart chart = null;
                switch (chartType) {
                    case "Ligne":
                        chart = createLineChart(data, title, timeColumn);
                        break;
                    case "Barre":
                        chart = createBarChart(data, title, timeColumn);
                        break;
                    default:
                        chart = createLineChart(data, title, timeColumn);
                }
                
                if (chart != null) {
                    addChartToContainer(chart);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private Map<String, Integer> getDistributionData(String tableName, String column) throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT " + column + ", COUNT(*) as count FROM " + tableName + 
                      " WHERE " + column + " IS NOT NULL AND " + column + " != '' " +
                      " GROUP BY " + column + " ORDER BY count DESC LIMIT 10";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String key = rs.getString(column);
                int count = rs.getInt("count");
                if (key != null && !key.trim().isEmpty()) {
                    data.put(key, count);
                }
            }
        }
        
        return data;
    }
    
    private Map<String, Integer> getTemporalData(String tableName, String timeColumn) throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT " + timeColumn + " as time_period, COUNT(*) as count FROM " + tableName + 
                      " WHERE " + timeColumn + " IS NOT NULL " +
                      " GROUP BY " + timeColumn + " ORDER BY time_period";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String period = rs.getString("time_period");
                int count = rs.getInt("count");
                if (period != null) {
                    data.put(period, count);
                }
            }
        }
        
        return data;
    }
    
    private PieChart createPieChart(Map<String, Integer> data, String title) {
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            pieChartData.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
        }
        
        PieChart chart = new PieChart(pieChartData);
        chart.setTitle(title);
        chart.setLabelsVisible(true);
        chart.setLegendVisible(true);
        
        // Améliorer la taille et la visibilité
        chart.setPrefSize(400, 350);
        chart.setMinSize(350, 300);
        
        return chart;
    }
    
    private BarChart<String, Number> createBarChart(Map<String, Integer> data, String title, String xAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        
        barChart.setTitle(title);
        xAxis.setLabel(xAxisLabel);
        yAxis.setLabel("Nombre");
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Données");
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        barChart.getData().add(series);
        
        // Améliorer la taille et la visibilité
        barChart.setPrefSize(450, 350);
        barChart.setMinSize(400, 300);
        
        // Configurer l'affichage des catégories
        xAxis.setTickLabelRotation(45);
        
        return barChart;
    }
    
    private LineChart<String, Number> createLineChart(Map<String, Integer> data, String title, String xAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        
        lineChart.setTitle(title);
        xAxis.setLabel(xAxisLabel);
        yAxis.setLabel("Nombre");
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Évolution");
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        lineChart.getData().add(series);
        lineChart.setCreateSymbols(true);
        
        // Améliorer la taille et la visibilité
        lineChart.setPrefSize(450, 350);
        lineChart.setMinSize(400, 300);
        
        return lineChart;
    }

    @FXML
    private void showCustomizeDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Personnaliser le Dashboard - " + currentService);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("customize-dialog");
        
        // Informations sur le service
        Label serviceLabel = new Label("Service actuel: " + currentService);
        serviceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        content.getChildren().add(serviceLabel);
        
        Label tablesLabel = new Label("Tables disponibles: " + String.join(", ", availableTables));
        tablesLabel.setWrapText(true);
        tablesLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        content.getChildren().add(tablesLabel);
        
        content.getChildren().add(new Separator());
        
        // Section pour les graphiques existants
        TitledPane existingChartsPane = new TitledPane();
        existingChartsPane.setText("Graphiques existants");
        
        ListView<String> existingCharts = new ListView<>();
        
        for (int i = 0; i < chartsContainer.getChildren().size(); i++) {
            Node chartNode = chartsContainer.getChildren().get(i);
            if (chartNode instanceof VBox) {
                VBox chartBox = (VBox) chartNode;
                if (!chartBox.getChildren().isEmpty() && chartBox.getChildren().get(0) instanceof Chart) {
                    Chart chart = (Chart) chartBox.getChildren().get(0);
                    existingCharts.getItems().add(chart.getTitle());
                }
            } else if (chartNode instanceof Chart) {
                Chart chart = (Chart) chartNode;
                existingCharts.getItems().add(chart.getTitle());
            }
        }
        
        Button removeChartBtn = new Button("Supprimer le graphique sélectionné");
        Button refreshChartsBtn = new Button("Actualiser tous les graphiques");
        
        VBox existingChartsBox = new VBox(10, existingCharts, 
            new HBox(10, removeChartBtn, refreshChartsBtn));
        existingChartsPane.setContent(existingChartsBox);
        
        // Section pour ajouter un nouveau graphique
        TitledPane addChartPane = new TitledPane();
        addChartPane.setText("Ajouter un graphique");
        
        ComboBox<String> tableSelect = new ComboBox<>();
        tableSelect.getItems().addAll(availableTables);
        tableSelect.setPromptText("Sélectionner une table");
        
        ComboBox<String> columnSelect = new ComboBox<>();
        columnSelect.setPromptText("Sélectionner une colonne");
        
        ComboBox<String> chartTypeSelect = new ComboBox<>();
        chartTypeSelect.getItems().addAll("Camembert", "Barre", "Ligne");
        chartTypeSelect.setPromptText("Type de graphique");
        
        TextField titleField = new TextField();
        titleField.setPromptText("Titre du graphique");
        
        Button addChartBtn = new Button("Ajouter ce graphique");
        
        // Écouteur pour charger les colonnes quand une table est sélectionnée
        tableSelect.setOnAction(e -> {
            String selectedTable = tableSelect.getValue();
            if (selectedTable != null) {
                loadColumnsForTable(selectedTable, columnSelect);
            }
        });
        
        GridPane addChartGrid = new GridPane();
        addChartGrid.setHgap(10);
        addChartGrid.setVgap(10);
        
        addChartGrid.add(new Label("Table:"), 0, 0);
        addChartGrid.add(tableSelect, 1, 0);
        addChartGrid.add(new Label("Colonne:"), 0, 1);
        addChartGrid.add(columnSelect, 1, 1);
        addChartGrid.add(new Label("Type:"), 0, 2);
        addChartGrid.add(chartTypeSelect, 1, 2);
        addChartGrid.add(new Label("Titre:"), 0, 3);
        addChartGrid.add(titleField, 1, 3);
        addChartGrid.add(addChartBtn, 1, 4);
        
        addChartPane.setContent(addChartGrid);
        
        content.getChildren().addAll(existingChartsPane, addChartPane);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        // Gestionnaire d'événement pour le bouton d'ajout
        addChartBtn.setOnAction(e -> {
            String table = tableSelect.getValue();
            String column = columnSelect.getValue();
            String chartType = chartTypeSelect.getValue();
            String title = titleField.getText();
            
            if (table != null && column != null && chartType != null) {
                if (title == null || title.trim().isEmpty()) {
                    title = "Graphique " + column;
                }
                
                try {
                    addDistributionChart(table, column, title, chartType);
                    HistoryManager.logCreation("Dashboard", 
                            "Ajout d'un graphique personnalisé - Service: " + currentService);
                } catch (SQLException ex) {
                    showErrorAlert("Erreur d'ajout de graphique", ex.getMessage());
                }
            } else {
                showErrorAlert("Paramètres incomplets", "Veuillez sélectionner tous les paramètres requis");
            }
        });

        // Gestionnaire d'événement pour le bouton de suppression
        removeChartBtn.setOnAction(e -> {
            int selectedIndex = existingCharts.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < chartsContainer.getChildren().size()) {
                chartsContainer.getChildren().remove(selectedIndex);
                existingCharts.getItems().remove(selectedIndex);
                HistoryManager.logDeletion("Dashboard", 
                        "Suppression d'un graphique - Service: " + currentService);
            }
        });
        
        // Gestionnaire pour actualiser tous les graphiques
        refreshChartsBtn.setOnAction(e -> {
            try {
                createServiceSpecificCharts();
                dialog.close();
                HistoryManager.logUpdate("Dashboard", 
                        "Actualisation des graphiques - Service: " + currentService);
            } catch (SQLException ex) {
                showErrorAlert("Erreur d'actualisation", ex.getMessage());
            }
        });

        dialog.showAndWait();
    }
    
    private void loadColumnsForTable(String tableName, ComboBox<String> columnSelect) {
        columnSelect.getItems().clear();
        List<String> columns = TableColumnManager.getColumnsForTable(tableName);
        
        // Filtrer les colonnes appropriées pour les graphiques
        List<String> graphableColumns = columns.stream()
                .filter(col -> !col.toLowerCase().equals("id") && !col.toLowerCase().startsWith("id_"))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        columnSelect.getItems().addAll(graphableColumns);
    }

    private void addChartToContainer(Chart chart) {
        if (chart == null) return;
        
        // Créer un conteneur pour le graphique avec un titre visible
        VBox chartContainer = new VBox(5);
        chartContainer.getStyleClass().add("chart-container");
        chartContainer.setAlignment(Pos.CENTER);
        
        // Ajouter une bordure et un style pour améliorer la visibilité
        chartContainer.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #e0e0e0;" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 10px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);"
        );
        
        // Définir des dimensions fixes pour une meilleure visibilité
        chart.setPrefSize(420, 320);
        chart.setMinSize(380, 280);
        chart.setMaxSize(500, 400);
        
        // Améliorer le style du graphique
        chart.setStyle("-fx-background-color: transparent;");
        
        chartContainer.getChildren().add(chart);
        
        // Limiter le nombre de graphiques pour éviter l'encombrement
        if (chartsContainer.getChildren().size() < 6) {
            chartsContainer.getChildren().add(chartContainer);
        } else {
            showErrorAlert("Limite atteinte", "Vous avez atteint le nombre maximum de graphiques (6) pour une meilleure lisibilité.");
        }
    }
    
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}