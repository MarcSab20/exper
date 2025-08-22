package application;

import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
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
    private final String DB_URL = "jdbc:mysql://localhost:3306/exploit";
    private final String DB_USER = "marco";
    private final String DB_PASSWORD = "29Papa278.";
    
    private String currentService;
    private List<String> availableTables;
    
    @FXML
    public void initialize() {
        currentService = UserSession.getCurrentService();
        availableTables = ServicePermissions.getTablesForService(currentService);
        
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
            addDistributionChart("maintenance", "type", "Maintenances par Type", "Barre");
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
        
        switch (chartType) {
            case "Camembert":
                createPieChart(data, title);
                break;
            case "Barre":
                createBarChart(data, title, column);
                break;
            default:
                createBarChart(data, title, column);
        }
    }
    
    private void addTemporalChart(String tableName, String timeColumn, String title, String chartType) throws SQLException {
        Map<String, Integer> data = getTemporalData(tableName, timeColumn);
        
        if (data.isEmpty()) {
            return;
        }
        
        switch (chartType) {
            case "Ligne":
                createLineChart(data, title, timeColumn);
                break;
            case "Barre":
                createBarChart(data, title, timeColumn);
                break;
            default:
                createLineChart(data, title, timeColumn);
        }
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
    
    private void createPieChart(Map<String, Integer> data, String title) {
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            pieChartData.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
        }
        
        PieChart chart = new PieChart(pieChartData);
        chart.setTitle(title);
        chart.setLabelsVisible(true);
        chart.setLegendVisible(true);
        
        addChartToContainer(chart);
    }
    
    private void createBarChart(Map<String, Integer> data, String title, String xAxisLabel) {
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
        addChartToContainer(barChart);
    }
    
    private void createLineChart(Map<String, Integer> data, String title, String xAxisLabel) {
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
        
        addChartToContainer(lineChart);
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
            VBox chartBox = (VBox) chartsContainer.getChildren().get(i);
            Chart chart = (Chart) chartBox.getChildren().get(0);
            existingCharts.getItems().add(chart.getTitle());
        }
        
        Button removeChartBtn = new Button("Supprimer le graphique sélectionné");
        
        VBox existingChartsBox = new VBox(10, existingCharts, removeChartBtn);
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

        dialog.showAndWait();
    }
    
    private void loadColumnsForTable(String tableName, ComboBox<String> columnSelect) {
        columnSelect.getItems().clear();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns("exploit", null, tableName, null);
            
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String dataType = columns.getString("TYPE_NAME");
                
                // Filtrer les colonnes appropriées pour les graphiques
                if (isColumnSuitableForChart(dataType)) {
                    columnSelect.getItems().add(columnName);
                }
            }
            
        } catch (SQLException e) {
            showErrorAlert("Erreur de base de données", "Impossible de charger les colonnes: " + e.getMessage());
        }
    }
    
    private boolean isColumnSuitableForChart(String dataType) {
        // Retourner true pour les types de données appropriés pour les graphiques
        return dataType.toUpperCase().contains("VARCHAR") ||
               dataType.toUpperCase().contains("CHAR") ||
               dataType.toUpperCase().contains("TEXT") ||
               dataType.toUpperCase().contains("ENUM") ||
               dataType.toUpperCase().contains("INT") ||
               dataType.toUpperCase().contains("DATE");
    }

    private void addChartToContainer(Chart chart) {
        VBox chartBox = new VBox(chart);
        chartBox.getStyleClass().add("chart-container");
        chart.getStyleClass().add("chart");
        
        // Définir des dimensions spécifiques pour le graphique
        chart.setPrefWidth(400);
        chart.setPrefHeight(300);
        
        if (chartsContainer.getChildren().size() < 8) { // Maximum 8 graphiques
            chartsContainer.getChildren().add(chartBox);
        } else {
            showErrorAlert("Limite atteinte", "Vous avez atteint le nombre maximum de graphiques (8).");
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