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

    @FXML
    private FlowPane chartsContainer;
    @FXML
    private Label messagesLabel;
    @FXML
    private Label salesLabel;
    @FXML
    private Label notificationsLabel;
    @FXML
    private Label scheduledLabel;
    @FXML
    private Label messagesCount, salesCount, notificationsCount, scheduledCount;

    // Informations de connexion à la base de données
    private final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private final String DB_USER = "marco";
    private final String DB_PASSWORD = "29Papa278.";
    
    // Liste des colonnes de la table final
    private final List<String> tableColumns = Arrays.asList(
        "id", "matricule", "nom", "grade", "echelon", "date_engagement", "formation", 
        "Region_Origine", "date_Naissance", "lieu_Naissance", "departement", "arrondissement",
        "date_Grade", "ref_Grade", "ref_echelon", "date_echelon", "ethnie", "religion",
        "nom_Pere", "nom_Mere", "situation_matrimoniale", "nombre_enfants", "statut", "Origine",
        "unite", "emploi", "date_affectation", "ref_affectation", "diplome_Civil", 
        "diplome_militaire", "categorie", "specialite", "qualification", "ordre_valeur",
        "Ordre_Merite_Camerounais", "Ordre_Merite_Sportif", "Force_Publique", "Vaillance",
        "Position_SPA", "Sexe", "Etat_Punitions", "promo_contingent"
    );
    
    // Liste des colonnes de type date pour les graphiques spécifiques
    private final List<String> dateColumns = Arrays.asList(
        "date_engagement", "date_Naissance", "date_Grade", "date_echelon", "date_affectation"
    );
    
    // Liste des colonnes numériques pour les graphiques spécifiques
    private final List<String> numericColumns = Arrays.asList(
        "id", "echelon", "nombre_enfants"
    );
    
    private Map<String, String> statBoxes = new HashMap<>();

    @FXML
    public void initialize() {
        try {
            // Connexion à la base de données et chargement des données
            loadDataFromDatabase();
            
            // Mise à jour des statistiques dans les boîtes
            updateStats();
            
            // Création des graphiques initiaux
            createInitialCharts();
        } catch (SQLException e) {
            showErrorAlert("Erreur de connexion à la base de données", e.getMessage());
        }
    }
    
    private void loadDataFromDatabase() throws SQLException {
        // Initialisation des statistiques à afficher
        int totalPersonnel = countRecords("SELECT COUNT(*) FROM final");
        int femininPersonnel = countRecords("SELECT COUNT(*) FROM final WHERE Sexe = 'femme'");
        int officiersSuperieurs = countRecords("SELECT COUNT(*) FROM final WHERE grade IN ('Capitaine', 'Lieutenant', 'Sous Lieutenant', 'Commandant', 'Lieutenant-Colonel', 'Colonel')");
        int nombreFormations = countDistinct("formation");
        
        // Mise à jour des statBoxes
        statBoxes.put("messages", "Personnels enregistrés: " + totalPersonnel);
        statBoxes.put("sales", "Personnels féminins: " + femininPersonnel);
        statBoxes.put("notifications", "Officiers supérieurs: " + officiersSuperieurs);
        statBoxes.put("scheduled", "Formations: " + nombreFormations);
    }
    
    private int countRecords(String query) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
    
    private int countDistinct(String column) throws SQLException {
        String query = "SELECT COUNT(DISTINCT " + column + ") FROM final";
        return countRecords(query);
    }

    private void updateStats() {
        // Messages (Total personnel)
        messagesLabel.setText("Personnels enregistrés");
        messagesCount.setText(statBoxes.get("messages").split(": ")[1]);
        
        // Sales (Personnel féminin)
        salesLabel.setText("Personnels féminins");
        salesCount.setText(statBoxes.get("sales").split(": ")[1]);
        
        // Notifications (Officiers supérieurs)
        notificationsLabel.setText("Officiers supérieurs");
        notificationsCount.setText(statBoxes.get("notifications").split(": ")[1]);
        
        // Scheduled (Formations)
        scheduledLabel.setText("Formations");
        scheduledCount.setText(statBoxes.get("scheduled").split(": ")[1]);
    }

    private void createInitialCharts() throws SQLException {
        // Création des 3 graphiques principaux demandés
        addGradeChart();      // Graphique sur les grades
        addSexeChart();       // Graphique sur le sexe
        addRegionChart();     // Graphique sur la région d'origine
        
        // Ajout de deux graphiques croisés comme exemples
        addStackedBarChart("grade", "Sexe", "Grade par Sexe");
        addStackedBarChart("formation", "Region_Origine", "Formation par Région d'origine");
    }

    @FXML
    private void showCustomizeDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Personnaliser le Dashboard");

        // Création du contenu du dialog
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("customize-dialog");
        
        // Section pour les graphiques existants
        TitledPane existingChartsPane = new TitledPane();
        existingChartsPane.setText("Graphiques existants");
        
        ListView<String> existingCharts = new ListView<>();
        
        // Remplissage de la liste avec les noms des graphiques actuels
        for (int i = 0; i < chartsContainer.getChildren().size(); i++) {
            VBox chartBox = (VBox) chartsContainer.getChildren().get(i);
            Chart chart = (Chart) chartBox.getChildren().get(0);
            existingCharts.getItems().add(chart.getTitle());
        }
        
        Button removeChartBtn = new Button("Supprimer le graphique sélectionné");
        
        VBox existingChartsBox = new VBox(10, existingCharts, removeChartBtn);
        existingChartsPane.setContent(existingChartsBox);
        
        // Section pour ajouter un nouveau graphique simple (une colonne)
        TitledPane addSimpleChartPane = new TitledPane();
        addSimpleChartPane.setText("Ajouter un graphique simple (une colonne)");
        
        ComboBox<String> simpleChartTypeSelect = new ComboBox<>();
        simpleChartTypeSelect.getItems().addAll(
            "Diagramme en barres",
            "Camembert",
            "Ligne",
            "Aire",
            "Nuage de points"
        );
        simpleChartTypeSelect.setPromptText("Type de graphique");
        
        ComboBox<String> columnSelect = new ComboBox<>();
        columnSelect.getItems().addAll(tableColumns);
        columnSelect.setPromptText("Colonne à analyser");
        
        ComboBox<String> aggregationSelect = new ComboBox<>();
        aggregationSelect.getItems().addAll("count", "sum", "avg");
        aggregationSelect.setPromptText("Fonction d'agrégation");
        
        Button addSimpleChartBtn = new Button("Ajouter ce graphique");
        
        GridPane addSimpleChartGrid = new GridPane();
        addSimpleChartGrid.setHgap(10);
        addSimpleChartGrid.setVgap(10);
        
        addSimpleChartGrid.add(new Label("Type de graphique:"), 0, 0);
        addSimpleChartGrid.add(simpleChartTypeSelect, 1, 0);
        addSimpleChartGrid.add(new Label("Colonne à analyser:"), 0, 1);
        addSimpleChartGrid.add(columnSelect, 1, 1);
        addSimpleChartGrid.add(new Label("Agrégation:"), 0, 2);
        addSimpleChartGrid.add(aggregationSelect, 1, 2);
        addSimpleChartGrid.add(addSimpleChartBtn, 1, 3);
        
        addSimpleChartPane.setContent(addSimpleChartGrid);
        
        // Section pour ajouter un graphique croisé (deux colonnes)
        TitledPane addCrossChartPane = new TitledPane();
        addCrossChartPane.setText("Ajouter un graphique croisé (deux colonnes)");
        
        ComboBox<String> mainColumnSelect = new ComboBox<>();
        mainColumnSelect.getItems().addAll(tableColumns);
        mainColumnSelect.setPromptText("Colonne principale (X)");
        
        ComboBox<String> crossColumnSelect = new ComboBox<>();
        crossColumnSelect.getItems().addAll(tableColumns);
        crossColumnSelect.setPromptText("Colonne de croisement");
        
        TextField titleField = new TextField();
        titleField.setPromptText("Titre du graphique");
        
        Button addCrossChartBtn = new Button("Ajouter ce graphique croisé");
        
        GridPane addCrossChartGrid = new GridPane();
        addCrossChartGrid.setHgap(10);
        addCrossChartGrid.setVgap(10);
        
        addCrossChartGrid.add(new Label("Colonne principale (X):"), 0, 0);
        addCrossChartGrid.add(mainColumnSelect, 1, 0);
        addCrossChartGrid.add(new Label("Colonne de croisement:"), 0, 1);
        addCrossChartGrid.add(crossColumnSelect, 1, 1);
        addCrossChartGrid.add(new Label("Titre du graphique:"), 0, 2);
        addCrossChartGrid.add(titleField, 1, 2);
        addCrossChartGrid.add(addCrossChartBtn, 1, 3);
        
        addCrossChartPane.setContent(addCrossChartGrid);
        
        content.getChildren().addAll(existingChartsPane, addSimpleChartPane, addCrossChartPane);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        // Ajout d'un écouteur pour filtrer les choix de type de graphique en fonction de la colonne
        columnSelect.setOnAction(e -> {
            String selectedColumn = columnSelect.getValue();
            if (selectedColumn != null) {
                // Pour les colonnes de date, seuls certains types de graphiques sont pertinents
                if (dateColumns.contains(selectedColumn)) {
                    simpleChartTypeSelect.getItems().clear();
                    simpleChartTypeSelect.getItems().addAll(
                        "Diagramme en barres",
                        "Ligne",
                        "Aire"
                    );
                } 
                // Pour les colonnes numériques, tous les types sont pertinents
                else if (numericColumns.contains(selectedColumn)) {
                    simpleChartTypeSelect.getItems().clear();
                    simpleChartTypeSelect.getItems().addAll(
                        "Diagramme en barres",
                        "Camembert",
                        "Ligne",
                        "Aire",
                        "Nuage de points"
                    );
                } 
                // Pour les colonnes textuelles, les camemberts et barres sont les plus pertinents
                else {
                    simpleChartTypeSelect.getItems().clear();
                    simpleChartTypeSelect.getItems().addAll(
                        "Diagramme en barres",
                        "Camembert"
                    );
                }
            }
        });

        // Gestionnaire d'événement pour le bouton d'ajout de graphique simple
        addSimpleChartBtn.setOnAction(e -> {
            String chartType = simpleChartTypeSelect.getValue();
            String column = columnSelect.getValue();
            String function = aggregationSelect.getValue();
            
            if (chartType != null && column != null && function != null) {
                try {
                    addNewChart(chartType, column, function);
                    HistoryManager.logCreation("Dashboard", 
                            "Ajout d'un graphique simple ");
                } catch (SQLException ex) {
                    showErrorAlert("Erreur d'ajout de graphique", ex.getMessage());
                }
            } else {
                showErrorAlert("Paramètres incomplets", "Veuillez sélectionner tous les paramètres requis");
            }
        });
        
        // Gestionnaire d'événement pour le bouton d'ajout de graphique croisé
        addCrossChartBtn.setOnAction(e -> {
            String mainColumn = mainColumnSelect.getValue();
            String crossColumn = crossColumnSelect.getValue();
            String title = titleField.getText();
            
            if (mainColumn != null && crossColumn != null) {
                if (title == null || title.trim().isEmpty()) {
                    title = mainColumn + " par " + crossColumn;
                }
                
                try {
                    addStackedBarChart(mainColumn, crossColumn, title);
                    HistoryManager.logCreation("Dashboard", 
                            "Ajout d'un graphique croisé ");
                } catch (SQLException ex) {
                    showErrorAlert("Erreur d'ajout de graphique croisé", ex.getMessage());
                }
            } else {
                showErrorAlert("Paramètres incomplets", "Veuillez sélectionner les deux colonnes pour le croisement");
            }
        });

        // Gestionnaire d'événement pour le bouton de suppression
        removeChartBtn.setOnAction(e -> {
            int selectedIndex = existingCharts.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < chartsContainer.getChildren().size()) {
                chartsContainer.getChildren().remove(selectedIndex);
                existingCharts.getItems().remove(selectedIndex);
                HistoryManager.logDeletion("Dashboard", 
                        "Suppression d'un graphique ");
            }
        });

        dialog.showAndWait();
    }

    private void addNewChart(String chartType, String xColumn, String yFunction) throws SQLException {
        switch (chartType) {
            case "Diagramme en barres":
                addBarChart(xColumn, yFunction, "Répartition par " + xColumn);
                break;
            case "Camembert":
                addPieChart(xColumn, yFunction, "Répartition par " + xColumn);
                break;
            case "Ligne":
                addLineChart(xColumn, yFunction, "Évolution par " + xColumn);
                break;
            case "Aire":
                addAreaChart(xColumn, yFunction, "Distribution par " + xColumn);
                break;
            case "Nuage de points":
                addScatterChart(xColumn, yFunction, "Répartition par " + xColumn);
                break;
        }
    }

    private void addGradeChart() throws SQLException {
        addBarChart("grade", "count", "Répartition par Grade");
    }

    private void addSexeChart() throws SQLException {
        addPieChart("Sexe", "count", "Répartition par Sexe");
    }

    private void addRegionChart() throws SQLException {
        addPieChart("Region_Origine", "count", "Répartition par Région d'origine");
    }

    private void addPieChart(String column, String function, String title) throws SQLException {
        PieChart pieChart = new PieChart();
        pieChart.setTitle(title);
        
        // Récupération des données depuis la base de données
        Map<String, Integer> data = getAggregatedData(column, function);
        
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            pieChartData.add(new PieChart.Data(entry.getKey(), entry.getValue()));
        }
        
        pieChart.setData(pieChartData);
        pieChart.setLegendVisible(true);
        
        addChartToContainer(pieChart);
    }

    private void addBarChart(String column, String function, String title) throws SQLException {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        
        barChart.setTitle(title);
        xAxis.setLabel(column);
        yAxis.setLabel("Nombre");
        
        // Récupération des données depuis la base de données
        Map<String, Integer> data = getAggregatedData(column, function);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(column);
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        barChart.getData().add(series);
        addChartToContainer(barChart);
    }
    
    private void addStackedBarChart(String mainColumn, String crossColumn, String title) throws SQLException {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        StackedBarChart<String, Number> stackedBarChart = new StackedBarChart<>(xAxis, yAxis);
        
        stackedBarChart.setTitle(title);
        xAxis.setLabel(mainColumn);
        yAxis.setLabel("Nombre");
        
        // Récupération des données croisées
        Map<String, Map<String, Integer>> crossData = getCrossData(mainColumn, crossColumn);
        
        // Pour chaque valeur de la colonne de croisement, créer une série
        for (String crossValue : crossData.keySet()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(crossValue);
            
            Map<String, Integer> data = crossData.get(crossValue);
            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            }
            
            stackedBarChart.getData().add(series);
        }
        
        addChartToContainer(stackedBarChart);
    }

    private void addLineChart(String column, String function, String title) throws SQLException {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        
        lineChart.setTitle(title);
        xAxis.setLabel(column);
        yAxis.setLabel("Nombre");
        
        // Récupération des données depuis la base de données
        Map<String, Integer> data = getAggregatedData(column, function);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(column);
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        lineChart.getData().add(series);
        addChartToContainer(lineChart);
    }

    private void addAreaChart(String column, String function, String title) throws SQLException {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        AreaChart<String, Number> areaChart = new AreaChart<>(xAxis, yAxis);
        
        areaChart.setTitle(title);
        xAxis.setLabel(column);
        yAxis.setLabel("Nombre");
        
        // Récupération des données depuis la base de données
        Map<String, Integer> data = getAggregatedData(column, function);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(column);
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        areaChart.getData().add(series);
        addChartToContainer(areaChart);
    }

    private void addScatterChart(String column, String function, String title) throws SQLException {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        ScatterChart<Number, Number> scatterChart = new ScatterChart<>(xAxis, yAxis);
        
        scatterChart.setTitle(title);
        
        // Pour un graphique à nuage de points, nous avons besoin de deux variables numériques
        // Nous utiliserons ici l'âge (calculé à partir de date_Naissance) comme axe X par défaut
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Personnel par âge");
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT date_Naissance, COUNT(*) as count FROM final GROUP BY YEAR(date_Naissance)")) {
            
            while (rs.next()) {
                Date birthDate = rs.getDate("date_Naissance");
                int count = rs.getInt("count");
                
                if (birthDate != null) {
                    // Calcul de l'âge à partir de la date de naissance
                    LocalDate birth = birthDate.toLocalDate();
                    LocalDate now = LocalDate.now();
                    int age = Period.between(birth, now).getYears();
                    
                    series.getData().add(new XYChart.Data<>(age, count));
                }
            }
        }
        
        scatterChart.getData().add(series);
        addChartToContainer(scatterChart);
    }

    private Map<String, Integer> getAggregatedData(String column, String function) throws SQLException {
        Map<String, Integer> result = new LinkedHashMap<>();
        
        String query;
        if ("count".equals(function)) {
            query = "SELECT " + column + ", COUNT(*) as value FROM final GROUP BY " + column + " ORDER BY value DESC";
        } else if ("sum".equals(function)) {
            // Pour les colonnes numériques, nous pouvons faire une somme
            if (numericColumns.contains(column)) {
                query = "SELECT " + column + ", SUM(" + column + ") as value FROM final GROUP BY " + column + " ORDER BY value DESC";
            } else {
                query = "SELECT " + column + ", COUNT(*) as value FROM final GROUP BY " + column + " ORDER BY value DESC";
            }
        } else if ("avg".equals(function)) {
            // Pour les colonnes numériques, nous pouvons faire une moyenne
            if (numericColumns.contains(column)) {
                query = "SELECT " + column + ", AVG(" + column + ") as value FROM final GROUP BY " + column + " ORDER BY value DESC";
            } else {
                query = "SELECT " + column + ", COUNT(*) as value FROM final GROUP BY " + column + " ORDER BY value DESC";
            }
        } else {
            query = "SELECT " + column + ", COUNT(*) as value FROM final GROUP BY " + column + " ORDER BY value DESC";
        }
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String key = rs.getString(column);
                int value = rs.getInt("value");
                
                // Gestion des valeurs nulles
                if (key == null) {
                    key = "Non spécifié";
                }
                
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    private Map<String, Map<String, Integer>> getCrossData(String mainColumn, String crossColumn) throws SQLException {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        
        // Récupérer d'abord toutes les valeurs possibles pour la colonne de croisement
        Set<String> crossValues = new HashSet<>();
        String crossQuery = "SELECT DISTINCT " + crossColumn + " FROM final";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(crossQuery)) {
            
            while (rs.next()) {
                String value = rs.getString(1);
                if (value == null) {
                    value = "Non spécifié";
                }
                crossValues.add(value);
                // Initialiser un map vide pour chaque valeur de croisement
                result.put(value, new LinkedHashMap<>());
            }
        }
        
        // Récupérer les données pour chaque combinaison de valeurs
        String dataQuery = "SELECT " + mainColumn + ", " + crossColumn + ", COUNT(*) as count " +
                          "FROM final " + 
                          "GROUP BY " + mainColumn + ", " + crossColumn + " " +
                          "ORDER BY " + mainColumn;
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(dataQuery)) {
            
            while (rs.next()) {
                String mainValue = rs.getString(mainColumn);
                String crossValue = rs.getString(crossColumn);
                int count = rs.getInt("count");
                
                // Gestion des valeurs nulles
                if (mainValue == null) {
                    mainValue = "Non spécifié";
                }
                if (crossValue == null) {
                    crossValue = "Non spécifié";
                }
                
                // Ajouter au map correspondant
                Map<String, Integer> crossMap = result.get(crossValue);
                if (crossMap == null) {
                    crossMap = new LinkedHashMap<>();
                    result.put(crossValue, crossMap);
                }
                
                crossMap.put(mainValue, count);
            }
        }
        
        return result;
    }

    private void addChartToContainer(Chart chart) {
        VBox chartBox = new VBox(chart);
        chartBox.getStyleClass().add("chart-container");
        chart.getStyleClass().add("chart");
        
        // Définir des dimensions spécifiques pour le graphique
        chart.setPrefWidth(450);
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