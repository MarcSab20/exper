package application;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.chart.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.sql.*;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

public class RequeteSpecialeController {
    @FXML private ComboBox<String> colonneComboBox;
    @FXML private ComboBox<String> operateurComboBox;
    @FXML private HBox valueContainer;
    @FXML private ListView<String> contraintesListView;
    @FXML private ComboBox<String> formatSortieComboBox;
    @FXML private HBox optionsContainer;
    @FXML private StackPane resultsContainer;
    
    // Nouveaux composants pour graphiques à 2 variables
    @FXML private ComboBox<String> colonneX;
    @FXML private ComboBox<String> colonneY;
    @FXML private CheckBox graphique2Variables;
    
    // Composants pour la gestion des requêtes sauvegardées
    @FXML private ListView<RequeteSauvegardee> requetesSauvegardeesListView;
    @FXML private TextField nomRequeteTextField;
    
    // Composants pour les options d'affichage
    private TextField textField;
    private ComboBox<String> valueComboBox;
    private ListView<CheckBox> valueCheckListView;
    private Spinner<Integer> valueSpinner;
    private Spinner<Double> valueDoubleSpinner;
    private DatePicker datePicker;
    
    private ComboBox<String> colonneAffichageComboBox;
    private ComboBox<String> triComboBox;
    private ComboBox<String> colonneTriComboBox;
    private ComboBox<String> typeGraphiqueComboBox;
    private ComboBox<String> colonneGroupByComboBox;
    
    private List<CheckBox> selectedColumnsCheckBoxes;

    private Map<String, String> colonneTypes = new HashMap<>();
    private Map<String, List<String>> stringValues = new HashMap<>();
    private final List<String> contraintes = new ArrayList<>();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    
    // Informations de connexion à la base de données
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    // Données dynamiques selon le service
    private List<String> columnNames = new ArrayList<>();
    private String currentService;
    
    // Liste observable des requêtes sauvegardées
    private ObservableList<RequeteSauvegardee> requetesSauvegardees = FXCollections.observableArrayList();
    
    @FXML
    public void initialize() {
        // Obtenir le service actuel
        currentService = UserSession.getCurrentService();
        
        // Initialiser les composants de valeur
        initializeValueComponents();
        
        // Remplir la ComboBox des formats de sortie
        formatSortieComboBox.setItems(FXCollections.observableArrayList(
            "Liste", "Tableau", "Graphique"
        ));
        
        // Écouter les changements de colonnes
        colonneComboBox.setOnAction(e -> {
            updateOperateurs();
            updateValueField();
        });
        
        // Écouter les changements de format de sortie
        formatSortieComboBox.setOnAction(e -> {
            updateOptionsContainer();
        });
        
        // Configurer la ListView des requêtes sauvegardées
        if (requetesSauvegardeesListView != null) {
            requetesSauvegardeesListView.setItems(requetesSauvegardees);
            requetesSauvegardeesListView.setCellFactory(lv -> new ListCell<RequeteSauvegardee>() {
                @Override
                protected void updateItem(RequeteSauvegardee item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getNom());
                    }
                }
            });
            
            // Ajouter un écouteur de sélection
            requetesSauvegardeesListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        chargerRequeteSauvegardee(newValue);
                    }
                }
            );
        }
        
        // Charger les métadonnées dynamiquement selon le service
        loadServiceMetadata();
        
        // Charger les requêtes sauvegardées depuis la base de données
        chargerRequetesSauvegardees();
    }
    
    /**
     * Charge les métadonnées spécifiques au service actuel
     */
    private void loadServiceMetadata() {
        try {
            // Obtenir les colonnes disponibles pour ce service
            columnNames = TableColumnManager.getAvailableColumnsForService(currentService);
            
            // Obtenir les types des colonnes
            colonneTypes = TableColumnManager.getColumnTypesForService(currentService);
            
            // Obtenir les valeurs distinctes pour les colonnes STRING
            stringValues = TableColumnManager.getDistinctValuesForService(currentService);
            
            // Remplir la ComboBox des colonnes
            colonneComboBox.setItems(FXCollections.observableArrayList(columnNames));
            
            System.out.println("Métadonnées chargées pour le service " + currentService + 
                             ": " + columnNames.size() + " colonnes disponibles");
            
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Erreur de chargement", "Impossible de charger les métadonnées pour le service " + 
                     currentService + ": " + e.getMessage());
        }
    }
    
    private void initializeValueComponents() {
        // Initialize TextField
        textField = new TextField();
        
        // Initialize ComboBox
        valueComboBox = new ComboBox<>();
        
        // Initialize CheckListView for multiple selection
        valueCheckListView = new ListView<>();
        valueCheckListView.setPrefHeight(150);
        
        // Initialize Integer Spinner
        SpinnerValueFactory<Integer> valueFactory = 
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 10000, 0);
        valueSpinner = new Spinner<>();
        valueSpinner.setValueFactory(valueFactory);
        valueSpinner.setEditable(true);
        
        // Initialize Double Spinner for decimal values
        SpinnerValueFactory<Double> doubleValueFactory = 
                new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 10000.0, 0.0, 0.1);
        valueDoubleSpinner = new Spinner<>();
        valueDoubleSpinner.setValueFactory(doubleValueFactory);
        valueDoubleSpinner.setEditable(true);
        
        // Initialize DatePicker
        datePicker = new DatePicker();
        
        // Initialize options components
        colonneAffichageComboBox = new ComboBox<>();
        triComboBox = new ComboBox<>(FXCollections.observableArrayList("ASC", "DESC"));
        colonneTriComboBox = new ComboBox<>();
        typeGraphiqueComboBox = new ComboBox<>(FXCollections.observableArrayList(
            "Camembert", "Barre", "Ligne", "Nuage de points", "Graphique croisé"
        ));
        colonneGroupByComboBox = new ComboBox<>();
        
        // Initialize components for 2-variable charts
        colonneX = new ComboBox<>();
        colonneY = new ComboBox<>();
        graphique2Variables = new CheckBox("Graphique à 2 variables");
        
        // Listener for 2-variable checkbox
        graphique2Variables.setOnAction(e -> updateGraphOptionsVisibility());
    }
    
    private void updateGraphOptionsVisibility() {
        // Cette méthode sera appelée quand on coche/décoche la case 2 variables
        updateOptionsContainer();
    }
    
    private void updateValueField() {
        String colonne = colonneComboBox.getValue();
        if (colonne == null) return;

        String type = colonneTypes.get(colonne);
        if (type == null) type = "STRING"; // Valeur par défaut
        
        valueContainer.getChildren().clear();

        switch (type) {
            case "STRING":
                if (stringValues.containsKey(colonne)) {
                    // Créer une liste de CheckBox pour sélection multiple
                    valueCheckListView.getItems().clear();
                    List<CheckBox> checkBoxes = new ArrayList<>();
                    
                    for (String value : stringValues.get(colonne)) {
                        CheckBox cb = new CheckBox(value);
                        checkBoxes.add(cb);
                    }
                    
                    valueCheckListView.setCellFactory(lv -> new ListCell<CheckBox>() {
                        @Override
                        protected void updateItem(CheckBox item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty) {
                                setGraphic(null);
                            } else {
                                setGraphic(item);
                            }
                        }
                    });
                    
                    valueCheckListView.getItems().addAll(checkBoxes);
                    valueContainer.getChildren().add(valueCheckListView);
                } else {
                    // Si pas de valeurs prédéfinies, utiliser un TextField
                    valueContainer.getChildren().add(textField);
                }
                break;
            case "INTEGER":
                valueContainer.getChildren().add(valueSpinner);
                break;
            case "DECIMAL":
                valueContainer.getChildren().add(valueDoubleSpinner);
                break;
            case "DATE":
                valueContainer.getChildren().add(datePicker);
                break;
        }
    }

    private void updateOperateurs() {
        String colonne = colonneComboBox.getValue();
        if (colonne == null) return;
        
        String type = colonneTypes.get(colonne);
        if (type == null) type = "STRING";
        
        ObservableList<String> operateurs;
        if ("STRING".equals(type)) {
            operateurs = FXCollections.observableArrayList("=", "!=", "LIKE", "IN", "NOT IN");
        } else if ("DATE".equals(type)) {
            operateurs = FXCollections.observableArrayList("=", "!=", ">", "<", ">=", "<=", "BETWEEN");
        } else {
            operateurs = FXCollections.observableArrayList("=", "!=", ">", "<", ">=", "<=", "BETWEEN");
        }
        
        operateurComboBox.setItems(operateurs);
    }
    
    private void updateOptionsContainer() {
        String format = formatSortieComboBox.getValue();
        if (format == null) return;
        
        optionsContainer.getChildren().clear();
        
        switch (format) {
            case "Liste":
                Label colonneLabel = new Label("Colonne à afficher:");
                colonneAffichageComboBox.setItems(FXCollections.observableArrayList(columnNames));
                
                Label triLabel = new Label("Tri:");
                
                Label colonneTriLabel = new Label("Colonne de tri:");
                colonneTriComboBox.setItems(FXCollections.observableArrayList(columnNames));
                
                optionsContainer.getChildren().addAll(
                    colonneLabel, colonneAffichageComboBox,
                    triLabel, triComboBox,
                    colonneTriLabel, colonneTriComboBox
                );
                break;
                
            case "Tableau":
                // Créer une VBox pour contenir les checkboxes des colonnes
                VBox columnSelectionBox = new VBox(5);
                columnSelectionBox.setPadding(new Insets(5));
                Label selectColumnsLabel = new Label("Colonnes à afficher:");
                columnSelectionBox.getChildren().add(selectColumnsLabel);
                
                // Créer les checkboxes pour chaque colonne
                selectedColumnsCheckBoxes = new ArrayList<>();
                for (String colName : columnNames) {
                    CheckBox cb = new CheckBox(colName);
                    selectedColumnsCheckBoxes.add(cb);
                    columnSelectionBox.getChildren().add(cb);
                }
                
                ScrollPane scrollPane = new ScrollPane(columnSelectionBox);
                scrollPane.setFitToWidth(true);
                scrollPane.setPrefHeight(150);
                
                Label triTableauLabel = new Label("Tri:");
                
                Label colonneTriTableauLabel = new Label("Colonne de tri:");
                colonneTriComboBox.setItems(FXCollections.observableArrayList(columnNames));
                
                optionsContainer.getChildren().addAll(
                    scrollPane,
                    triTableauLabel, triComboBox,
                    colonneTriTableauLabel, colonneTriComboBox
                );
                break;
                
            case "Graphique":
                // Options pour graphiques simples et à 2 variables
                Label typeGraphLabel = new Label("Type de graphique:");
                
                VBox graphOptionsBox = new VBox(5);
                
                // Checkbox pour activer les graphiques à 2 variables
                graphOptionsBox.getChildren().add(graphique2Variables);
                
                if (graphique2Variables.isSelected()) {
                    // Options pour graphiques à 2 variables
                    Label xLabel = new Label("Variable X:");
                    colonneX.setItems(FXCollections.observableArrayList(
                        TableColumnManager.getGraphableColumnsForService(currentService)));
                    
                    Label yLabel = new Label("Variable Y:");
                    colonneY.setItems(FXCollections.observableArrayList(
                        TableColumnManager.getGraphableColumnsForService(currentService)));
                    
                    graphOptionsBox.getChildren().addAll(xLabel, colonneX, yLabel, colonneY);
                } else {
                    // Options pour graphiques simples
                    Label groupByLabel = new Label("Grouper par:");
                    colonneGroupByComboBox.setItems(FXCollections.observableArrayList(
                        TableColumnManager.getGraphableColumnsForService(currentService)));
                    
                    graphOptionsBox.getChildren().addAll(groupByLabel, colonneGroupByComboBox);
                }
                
                optionsContainer.getChildren().addAll(
                    typeGraphLabel, typeGraphiqueComboBox,
                    graphOptionsBox
                );
                break;
        }
    }

    @FXML
    private void ajouterContrainte() {
        String colonne = colonneComboBox.getValue();
        String operateur = operateurComboBox.getValue();
        
        if (colonne == null || operateur == null) {
            showAlert("Erreur", "Veuillez sélectionner une colonne et un opérateur");
            return;
        }
        
        String valeur = getValueFromCurrentField();
        if (valeur == null || valeur.isEmpty()) {
            showAlert("Erreur", "Veuillez sélectionner au moins une valeur");
            return;
        }

        String contrainte = String.format("%s %s %s", colonne, operateur, valeur);
        contraintes.add(contrainte);
        contraintesListView.getItems().add(contrainte);

        // Réinitialiser les champs
        resetValueField();
    }
    
    private String getValueFromCurrentField() {
        String colonne = colonneComboBox.getValue();
        String type = colonneTypes.getOrDefault(colonne, "STRING");
        String operateur = operateurComboBox.getValue();

        switch (type) {
            case "STRING":
                if (stringValues.containsKey(colonne) && valueCheckListView.getItems().size() > 0) {
                    if ("IN".equals(operateur) || "NOT IN".equals(operateur)) {
                        // Pour les opérateurs IN et NOT IN, collectez toutes les valeurs sélectionnées
                        List<String> selectedValues = new ArrayList<>();
                        for (int i = 0; i < valueCheckListView.getItems().size(); i++) {
                            CheckBox cb = valueCheckListView.getItems().get(i);
                            if (cb.isSelected()) {
                                selectedValues.add("'" + cb.getText() + "'");
                            }
                        }
                        if (selectedValues.isEmpty()) return "";
                        return "(" + String.join(", ", selectedValues) + ")";
                    } else {
                        // Pour les autres opérateurs, prenez la première valeur sélectionnée
                        for (int i = 0; i < valueCheckListView.getItems().size(); i++) {
                            CheckBox cb = valueCheckListView.getItems().get(i);
                            if (cb.isSelected()) {
                                if ("LIKE".equals(operateur)) {
                                    return "'%" + cb.getText() + "%'";
                                } else {
                                    return "'" + cb.getText() + "'";
                                }
                            }
                        }
                        return "";
                    }
                } else {
                    // Utiliser le TextField
                    String value = textField.getText().trim();
                    if (value.isEmpty()) return "";
                    if ("LIKE".equals(operateur)) {
                        return "'%" + value + "%'";
                    } else {
                        return "'" + value + "'";
                    }
                }
            case "INTEGER":
                return valueSpinner.getValue().toString();
            case "DECIMAL":
                return valueDoubleSpinner.getValue().toString();
            case "DATE":
                LocalDate date = datePicker.getValue();
                return date != null ? "'" + date.format(dateFormatter) + "'" : "";
            default:
                return "";
        }
    }
    
    private void resetValueField() {
        String colonne = colonneComboBox.getValue();
        if (colonne == null) return;
        
        String type = colonneTypes.getOrDefault(colonne, "STRING");
        
        switch (type) {
            case "STRING":
                if (stringValues.containsKey(colonne)) {
                    // Décocher toutes les cases
                    for (CheckBox cb : valueCheckListView.getItems()) {
                        cb.setSelected(false);
                    }
                } else {
                    textField.clear();
                }
                break;
            case "INTEGER":
                valueSpinner.getValueFactory().setValue(0);
                break;
            case "DECIMAL":
                valueDoubleSpinner.getValueFactory().setValue(0.0);
                break;
            case "DATE":
                datePicker.setValue(null);
                break;
        }
    }

    @FXML
    private void executerRequete() {
        if (contraintes.isEmpty()) {
            showAlert("Erreur", "Aucune contrainte définie");
            return;
        }

        String formatSortie = formatSortieComboBox.getValue();
        if (formatSortie == null) {
            showAlert("Erreur", "Veuillez sélectionner un format de sortie");
            return;
        }
        
        // Vérifier que les options nécessaires sont sélectionnées
        if (!validateOptions(formatSortie)) {
            return;
        }

        // Construire et exécuter la requête SQL
        try {
            if ("Graphique".equals(formatSortie) && graphique2Variables.isSelected()) {
                executerRequeteDeuxVariables();
            } else {
                String query = buildSqlQuery(formatSortie);
                executeQuery(query, formatSortie);
            }
            HistoryManager.logCreation("Requêtes spéciales", 
                    "Éxécution d'une requête spéciale - Service: " + currentService);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Erreur", "Erreur lors de l'exécution de la requête: " + e.getMessage());
        }
    }
    
    private void executerRequeteDeuxVariables() throws SQLException {
        String colX = colonneX.getValue();
        String colY = colonneY.getValue();
        String typeGraphique = typeGraphiqueComboBox.getValue();
        
        if (colX == null || colY == null) {
            showAlert("Erreur", "Veuillez sélectionner les deux variables X et Y");
            return;
        }
        
        if (typeGraphique == null) {
            showAlert("Erreur", "Veuillez sélectionner un type de graphique");
            return;
        }
        
        // Construire la requête pour 2 variables
        StringBuilder queryBuilder = new StringBuilder("SELECT ");
        queryBuilder.append(colX).append(", ").append(colY).append(", COUNT(*) as count ");
        
        // Déterminer la table à partir des colonnes et contraintes
        String tableName = determineTableFromConstraints();
        queryBuilder.append(" FROM ").append(tableName);
        
        // Ajouter les contraintes (WHERE)
        if (!contraintes.isEmpty()) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(String.join(" AND ", contraintes));
        }
        
        queryBuilder.append(" GROUP BY ").append(colX).append(", ").append(colY);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(queryBuilder.toString())) {
            
            afficherGraphiqueDeuxVariables(rs, colX, colY, typeGraphique);
            
        }
    }
    
    private String determineTableFromConstraints() {
        // Pour l'instant, utiliser une table par défaut ou la première table disponible
        List<String> tables = ServicePermissions.getTablesForService(currentService);
        if (!tables.isEmpty()) {
            // Retourner la première table qui contient toutes les colonnes utilisées
            Set<String> usedColumns = new HashSet<>();
            usedColumns.add(colonneComboBox.getValue());
            if (colonneX.getValue() != null) usedColumns.add(colonneX.getValue());
            if (colonneY.getValue() != null) usedColumns.add(colonneY.getValue());
            
            for (String table : tables) {
                List<String> tableColumns = TableColumnManager.getColumnsForTable(table);
                if (tableColumns.containsAll(usedColumns)) {
                    return table;
                }
            }
            
            return tables.get(0); // Fallback
        }
        return "identite_personnelle"; // Fallback par défaut
    }
    
    private void afficherGraphiqueDeuxVariables(ResultSet rs, String colX, String colY, String typeGraphique) throws SQLException {
        switch (typeGraphique) {
            case "Nuage de points":
                createScatterPlot(rs, colX, colY);
                break;
            case "Graphique croisé":
                createCrossChart(rs, colX, colY);
                break;
            default:
                createHeatMap(rs, colX, colY);
                break;
        }
    }
    
    private void createScatterPlot(ResultSet rs, String colX, String colY) throws SQLException {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        ScatterChart<Number, Number> scatterChart = new ScatterChart<>(xAxis, yAxis);
        
        scatterChart.setTitle("Nuage de points: " + colX + " vs " + colY);
        xAxis.setLabel(colX);
        yAxis.setLabel(colY);
        
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Données");
        
        while (rs.next()) {
            try {
                double x = Double.parseDouble(rs.getString(colX));
                double y = Double.parseDouble(rs.getString(colY));
                series.getData().add(new XYChart.Data<>(x, y));
            } catch (NumberFormatException e) {
                // Ignorer les valeurs non numériques
            }
        }
        
        scatterChart.getData().add(series);
        resultsContainer.getChildren().setAll(scatterChart);
    }
    
    private void createCrossChart(ResultSet rs, String colX, String colY) throws SQLException {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        StackedBarChart<String, Number> chart = new StackedBarChart(xAxis, yAxis);
        
        chart.setTitle("Graphique croisé: " + colX + " vs " + colY);
        xAxis.setLabel(colX);
        yAxis.setLabel("Fréquence");
        
        Map<String, XYChart.Series<String, Number>> seriesMap = new HashMap<>();
        
        while (rs.next()) {
            String xValue = rs.getString(colX);
            String yValue = rs.getString(colY);
            int count = rs.getInt("count");
            
            if (!seriesMap.containsKey(yValue)) {
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                series.setName(yValue);
                seriesMap.put(yValue, series);
            }
            
            seriesMap.get(yValue).getData().add(new XYChart.Data<>(xValue, count));
        }
        
        chart.getData().addAll(seriesMap.values());
        resultsContainer.getChildren().setAll(chart);
    }
    
    private void createHeatMap(ResultSet rs, String colX, String colY) throws SQLException {
        // Pour une heatmap simple, créer un tableau visuel
        VBox heatMapContainer = new VBox(5);
        heatMapContainer.getChildren().add(new Label("Carte de chaleur: " + colX + " vs " + colY));
        
        Map<String, Map<String, Integer>> data = new HashMap<>();
        
        while (rs.next()) {
            String xValue = rs.getString(colX);
            String yValue = rs.getString(colY);
            int count = rs.getInt("count");
            
            data.computeIfAbsent(xValue, k -> new HashMap<>()).put(yValue, count);
        }
        
        // Créer une représentation simple sous forme de tableau
        for (Map.Entry<String, Map<String, Integer>> entry : data.entrySet()) {
            HBox row = new HBox(5);
            row.getChildren().add(new Label(entry.getKey() + ": "));
            
            for (Map.Entry<String, Integer> cellEntry : entry.getValue().entrySet()) {
                Label cell = new Label(cellEntry.getKey() + "(" + cellEntry.getValue() + ")");
                cell.setStyle("-fx-background-color: lightblue; -fx-padding: 5;");
                row.getChildren().add(cell);
            }
            
            heatMapContainer.getChildren().add(row);
        }
        
        ScrollPane scrollPane = new ScrollPane(heatMapContainer);
        scrollPane.setFitToWidth(true);
        resultsContainer.getChildren().setAll(scrollPane);
    }
    
    // Méthodes utilitaires existantes...
    private boolean validateOptions(String formatSortie) {
        switch (formatSortie) {
            case "Liste":
                if (colonneAffichageComboBox.getValue() == null) {
                    showAlert("Erreur", "Veuillez sélectionner une colonne à afficher");
                    return false;
                }
                break;
                
            case "Tableau":
                boolean atLeastOneSelected = false;
                for (CheckBox cb : selectedColumnsCheckBoxes) {
                    if (cb.isSelected()) {
                        atLeastOneSelected = true;
                        break;
                    }
                }
                if (!atLeastOneSelected) {
                    showAlert("Erreur", "Veuillez sélectionner au moins une colonne à afficher");
                    return false;
                }
                break;
                
            case "Graphique":
                if (graphique2Variables.isSelected()) {
                    if (colonneX.getValue() == null || colonneY.getValue() == null) {
                        showAlert("Erreur", "Veuillez sélectionner les variables X et Y");
                        return false;
                    }
                } else {
                    if (colonneGroupByComboBox.getValue() == null) {
                        showAlert("Erreur", "Veuillez sélectionner une colonne pour le regroupement");
                        return false;
                    }
                }
                if (typeGraphiqueComboBox.getValue() == null) {
                    showAlert("Erreur", "Veuillez sélectionner un type de graphique");
                    return false;
                }
                break;
        }
        
        return true;
    }
    
    private String buildSqlQuery(String formatSortie) {
        StringBuilder queryBuilder = new StringBuilder("SELECT ");
        
        // Déterminer la table principale
        String tableName = determineTableFromConstraints();
        
        // Sélectionner les colonnes appropriées selon le format de sortie
        switch (formatSortie) {
            case "Liste":
                queryBuilder.append(colonneAffichageComboBox.getValue());
                break;
                
            case "Tableau":
                List<String> selectedColumns = new ArrayList<>();
                for (CheckBox cb : selectedColumnsCheckBoxes) {
                    if (cb.isSelected()) {
                        selectedColumns.add(cb.getText());
                    }
                }
                queryBuilder.append(String.join(", ", selectedColumns));
                break;
                
            case "Graphique":
                queryBuilder.append("COUNT(*) AS count, ")
                           .append(colonneGroupByComboBox.getValue());
                break;
        }
        
        // Ajouter la table
        queryBuilder.append(" FROM ").append(tableName);
        
        // Ajouter les contraintes (WHERE)
        if (!contraintes.isEmpty()) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(String.join(" AND ", contraintes));
        }
        
        // Ajouter GROUP BY pour les graphiques
        if (formatSortie.equals("Graphique") && !graphique2Variables.isSelected()) {
            queryBuilder.append(" GROUP BY ")
                       .append(colonneGroupByComboBox.getValue());
        }
        
        // Ajouter ORDER BY si spécifié
        if ((formatSortie.equals("Liste") || formatSortie.equals("Tableau")) 
                && colonneTriComboBox.getValue() != null 
                && triComboBox.getValue() != null) {
            queryBuilder.append(" ORDER BY ")
                       .append(colonneTriComboBox.getValue())
                       .append(" ")
                       .append(triComboBox.getValue());
        }
        
        return queryBuilder.toString();
    }
    
    private void executeQuery(String query, String formatSortie) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            switch (formatSortie) {
                case "Liste":
                    afficherResultatsListe(rs);
                    break;
                case "Tableau":
                    afficherResultatsTableau(rs);
                    break;
                case "Graphique":
                    afficherResultatsGraphique(rs);
                    break;
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Erreur", "Erreur SQL: " + e.getMessage());
        }
    }

    private void afficherResultatsTableau(ResultSet rs) throws SQLException {
        TableView<Map<String, String>> tableView = new TableView<>();
        
        // Récupérer les métadonnées pour obtenir les noms des colonnes
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        // Créer les colonnes pour la TableView
        for (int i = 1; i <= columnCount; i++) {
            final int columnIndex = i;
            String columnName = metaData.getColumnName(i);
            
            TableColumn<Map<String, String>, String> column = new TableColumn<>(columnName);
            column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(columnName)));
            tableView.getColumns().add(column);
        }
        
        // Remplir les données
        ObservableList<Map<String, String>> data = FXCollections.observableArrayList();
        while (rs.next()) {
            Map<String, String> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                row.put(columnName, rs.getString(i));
            }
            data.add(row);
        }
        
        tableView.setItems(data);
        resultsContainer.getChildren().setAll(tableView);
    }

    private void afficherResultatsListe(ResultSet rs) throws SQLException {
        ListView<String> listView = new ListView<>();
        ObservableList<String> items = FXCollections.observableArrayList();
        
        // Récupérer l'unique colonne sélectionnée
        while (rs.next()) {
            items.add(rs.getString(1));
        }
        
        listView.setItems(items);
        resultsContainer.getChildren().setAll(listView);
    }

    private void afficherResultatsGraphique(ResultSet rs) throws SQLException {
        String typeGraphique = typeGraphiqueComboBox.getValue();
        String colonneGroupe = colonneGroupByComboBox.getValue();
        
        switch (typeGraphique) {
            case "Camembert":
                afficherCamembert(rs, colonneGroupe);
                break;
            case "Barre":
                afficherBarres(rs, colonneGroupe);
                break;
            case "Ligne":
                afficherLigne(rs, colonneGroupe);
                break;
        }
    }
    
    private void afficherCamembert(ResultSet rs, String colonneGroupe) throws SQLException {
        // Créer les données pour le graphique
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        
        while (rs.next()) {
            int count = rs.getInt("count");
            String category = rs.getString(colonneGroupe);
            if (category != null) {
                pieChartData.add(new PieChart.Data(category + " (" + count + ")", count));
            } else {
                pieChartData.add(new PieChart.Data("Non défini (" + count + ")", count));
            }
        }
        
        // Créer le graphique
        final PieChart chart = new PieChart(pieChartData);
        chart.setTitle("Répartition par " + colonneGroupe);
        chart.setLabelsVisible(true);
        
        resultsContainer.getChildren().setAll(chart);
    }
    
    private void afficherBarres(ResultSet rs, String colonneGroupe) throws SQLException {
        // Créer la série de données
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Nombre");
        
        // Remplir les données
        while (rs.next()) {
            int count = rs.getInt("count");
            String category = rs.getString(colonneGroupe);
            if (category == null) category = "Non défini";
            series.getData().add(new XYChart.Data<>(category, count));
        }
        
        // Créer le graphique
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel(colonneGroupe);
        yAxis.setLabel("Nombre");
        
        final BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Répartition par " + colonneGroupe);
        barChart.getData().add(series);
        
        resultsContainer.getChildren().setAll(barChart);
    }
    
    private void afficherLigne(ResultSet rs, String colonneGroupe) throws SQLException {
        // Créer la série de données
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Nombre");
        
        // Remplir les données
        while (rs.next()) {
            int count = rs.getInt("count");
            String category = rs.getString(colonneGroupe);
            if (category == null) category = "Non défini";
            series.getData().add(new XYChart.Data<>(category, count));
        }
        
        // Créer le graphique
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel(colonneGroupe);
        yAxis.setLabel("Nombre");
        
        final LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Tendance par " + colonneGroupe);
        lineChart.getData().add(series);
        lineChart.setCreateSymbols(true);
        
        resultsContainer.getChildren().setAll(lineChart);
    }

    @FXML
    private void exporterExcel() {
        showAlert("Information", "La fonctionnalité d'export Excel sera implémentée prochainement");
    }

    @FXML
    private void exporterPDF() {
        // Vérifier s'il y a des résultats à exporter
        if (resultsContainer.getChildren().isEmpty()) {
            showAlert("Erreur", "Aucun résultat à exporter. Veuillez d'abord exécuter une requête.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer le rapport PDF");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers PDF", "*.pdf")
        );
        fileChooser.setInitialFileName("Rapport_Requete_Speciale_" + 
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
        
        Stage stage = (Stage) resultsContainer.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);
        
        if (file != null) {
            try {
                exportToPDF(file);
                showAlert("Succès", "Le rapport PDF a été généré avec succès dans :\n" + file.getAbsolutePath());
                HistoryManager.logCreation("Requêtes spéciales", 
                        "Export PDF d'une requête spéciale - Service: " + currentService);
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Erreur", "Erreur lors de la génération du PDF: " + e.getMessage());
            }
        }
    }
    
    private void exportToPDF(File file) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(file));
        
        document.open();
        
        // Titre
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("Rapport de Requête Spéciale - " + currentService, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        
        // Date et informations
        Paragraph dateInfo = new Paragraph("Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + 
                                         " | Service: " + currentService);
        dateInfo.setAlignment(Element.ALIGN_CENTER);
        document.add(dateInfo);
        document.add(new Paragraph(" "));
        
        // Contraintes utilisées
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        document.add(new Paragraph("Contraintes appliquées:", sectionFont));
        document.add(new Paragraph(" "));
        
        for (String contrainte : contraintes) {
            document.add(new Paragraph("• " + contrainte));
        }
        document.add(new Paragraph(" "));
        
        // Format de sortie
        document.add(new Paragraph("Format de sortie: " + formatSortieComboBox.getValue(), sectionFont));
        document.add(new Paragraph(" "));
        
        // Données (selon le type de résultat)
        exportResultsToPDF(document);
        
        document.close();
    }
    
    private void exportResultsToPDF(Document document) throws Exception {
        // Cette méthode sera appelée pour exporter les résultats spécifiques
        // en fonction du type d'affichage (tableau, liste, etc.)
        
        if (!resultsContainer.getChildren().isEmpty()) {
            if (resultsContainer.getChildren().get(0) instanceof TableView) {
                exportTableToPDF(document, (TableView<?>) resultsContainer.getChildren().get(0));
            } else if (resultsContainer.getChildren().get(0) instanceof ListView) {
                exportListToPDF(document, (ListView<String>) resultsContainer.getChildren().get(0));
            } else {
                document.add(new Paragraph("Résultats sous forme de graphique - voir l'application pour la visualisation."));
            }
        }
    }
    
    private void exportTableToPDF(Document document, TableView<?> tableView) throws Exception {
        if (tableView.getColumns().isEmpty()) return;
        
        PdfPTable table = new PdfPTable(tableView.getColumns().size());
        table.setWidthPercentage(100);
        
        // En-têtes
        for (TableColumn<?, ?> column : tableView.getColumns()) {
            PdfPCell cell = new PdfPCell(new Phrase(column.getText()));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
        
        // Données
        for (Object item : tableView.getItems()) {
            if (item instanceof Map) {
                Map<String, String> row = (Map<String, String>) item;
                for (TableColumn<?, ?> column : tableView.getColumns()) {
                    String value = row.get(column.getText());
                    table.addCell(value != null ? value : "");
                }
            }
        }
        
        document.add(table);
    }
    
    private void exportListToPDF(Document document, ListView<String> listView) throws Exception {
        document.add(new Paragraph("Résultats de la requête:", 
                                 new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD)));
        document.add(new Paragraph(" "));
        
        for (String item : listView.getItems()) {
            document.add(new Paragraph("• " + item));
        }
    }

    /***************************
     * MÉTHODES POUR LA GESTION DES REQUÊTES SAUVEGARDÉES
     ***************************/
    
    /**
     * Sauvegarde la requête actuelle dans la base de données
     */
    @FXML
    private void sauvegarderRequete() {
        if (contraintes.isEmpty()) {
            showAlert("Erreur", "Aucune contrainte définie à sauvegarder");
            return;
        }

        String nom = nomRequeteTextField.getText();
        if (nom == null || nom.trim().isEmpty()) {
            showAlert("Erreur", "Veuillez donner un nom à la requête");
            return;
        }
        
        String formatSortie = formatSortieComboBox.getValue();
        if (formatSortie == null) {
            showAlert("Erreur", "Veuillez sélectionner un format de sortie");
            return;
        }
        
        // Vérifier que les options nécessaires sont sélectionnées
        if (!validateOptions(formatSortie)) {
            return;
        }
        
        // Collecter les options selon le format de sortie
        Map<String, String> options = collecterOptions(formatSortie);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Vérifier si une requête avec ce nom existe déjà
            String checkQuery = "SELECT id FROM requetes_sauvegardees WHERE nom = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, nom);
                ResultSet rs = checkStmt.executeQuery();
                
                if (rs.next()) {
                    // La requête existe déjà, demander confirmation pour la remplacer
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Confirmation");
                    alert.setHeaderText("Une requête avec ce nom existe déjà");
                    alert.setContentText("Voulez-vous remplacer la requête existante ?");
                    
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        // Mettre à jour la requête existante
                        mettreAJourRequete(conn, rs.getInt("id"), nom, options, formatSortie);
                    } else {
                        return; // Annulation
                    }
                } else {
                    // Insérer une nouvelle requête
                    insererNouvelleRequete(conn, nom, options, formatSortie);
                }
            }
            
            // Rafraîchir la liste des requêtes sauvegardées
            chargerRequetesSauvegardees();
            showAlert("Information", "Requête sauvegardée avec succès");
            HistoryManager.logCreation("Requêtes spéciales", 
                    "Sauvegarde d'une requête: " + nom);
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Erreur", "Erreur lors de la sauvegarde de la requête: " + e.getMessage());
        }
    }

    /**
     * Met à jour une requête existante dans la base de données
     */
    private void mettreAJourRequete(Connection conn, int id, String nom, Map<String, String> options, String formatSortie) throws SQLException {
        String updateQuery = "UPDATE requetes_sauvegardees SET nom = ?, contraintes = ?, format_sortie = ?, options = ? WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            stmt.setString(1, nom);
            stmt.setString(2, String.join(";", contraintes));
            stmt.setString(3, formatSortie);
            stmt.setString(4, convertOptionsToJson(options));
            stmt.setInt(5, id);
            
            stmt.executeUpdate();
        }
    }

    /**
     * Insère une nouvelle requête dans la base de données
     */
    private void insererNouvelleRequete(Connection conn, String nom, Map<String, String> options, String formatSortie) throws SQLException {
        String insertQuery = "INSERT INTO requetes_sauvegardees (nom, contraintes, format_sortie, options) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            stmt.setString(1, nom);
            stmt.setString(2, String.join(";", contraintes));
            stmt.setString(3, formatSortie);
            stmt.setString(4, convertOptionsToJson(options));
            
            stmt.executeUpdate();
        }
    }

    /**
     * Collecte les options actuelles selon le format de sortie
     */
    private Map<String, String> collecterOptions(String formatSortie) {
        Map<String, String> options = new HashMap<>();
        
        switch (formatSortie) {
            case "Liste":
                options.put("colonneAffichage", colonneAffichageComboBox.getValue());
                options.put("tri", triComboBox.getValue());
                options.put("colonneTri", colonneTriComboBox.getValue());
                break;
                
            case "Tableau":
                List<String> selectedColumns = new ArrayList<>();
                for (CheckBox cb : selectedColumnsCheckBoxes) {
                    if (cb.isSelected()) {
                        selectedColumns.add(cb.getText());
                    }
                }
                options.put("colonnes", String.join(",", selectedColumns));
                options.put("tri", triComboBox.getValue());
                options.put("colonneTri", colonneTriComboBox.getValue());
                break;
                
            case "Graphique":
                options.put("typeGraphique", typeGraphiqueComboBox.getValue());
                options.put("colonneGroupBy", colonneGroupByComboBox.getValue());
                options.put("graphique2Variables", String.valueOf(graphique2Variables.isSelected()));
                if (graphique2Variables.isSelected()) {
                    options.put("colonneX", colonneX.getValue());
                    options.put("colonneY", colonneY.getValue());
                }
                break;
        }
        
        return options;
    }

    /**
     * Convertit un Map d'options en JSON
     */
    private String convertOptionsToJson(Map<String, String> options) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (!first) {
                json.append(",");
            }
            
            String value = entry.getValue() != null ? entry.getValue() : "";
            json.append("\"").append(entry.getKey()).append("\":\"").append(value).append("\"");
            first = false;
        }
        
        json.append("}");
        return json.toString();
    }

    /**
     * Charge les requêtes sauvegardées depuis la base de données
     */
    private void chargerRequetesSauvegardees() {
        requetesSauvegardees.clear();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            // Vérifier d'abord si la table existe
            String checkTableQuery = "SHOW TABLES LIKE 'requetes_sauvegardees'";
            try (ResultSet tableCheck = stmt.executeQuery(checkTableQuery)) {
                if (!tableCheck.next()) {
                    // La table n'existe pas, la créer
                    creerTableRequetesSiNecessaire(conn);
                }
            }
            
            // Charger les requêtes
            String selectQuery = "SELECT * FROM requetes_sauvegardees ORDER BY nom";
            try (ResultSet rs = stmt.executeQuery(selectQuery)) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String nom = rs.getString("nom");
                    String contraintesStr = rs.getString("contraintes");
                    String formatSortie = rs.getString("format_sortie");
                    String optionsJson = rs.getString("options");
                    
                    RequeteSauvegardee requete = new RequeteSauvegardee(id, nom, contraintesStr, formatSortie, optionsJson);
                    requetesSauvegardees.add(requete);
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Erreur", "Erreur lors du chargement des requêtes sauvegardées: " + e.getMessage());
        }
    }
    
    private void creerTableRequetesSiNecessaire(Connection conn) throws SQLException {
        String createTableSQL = 
            "CREATE TABLE requetes_sauvegardees (" +
            "  id INT AUTO_INCREMENT PRIMARY KEY," +
            "  nom VARCHAR(255) NOT NULL," +
            "  contraintes TEXT NOT NULL," +
            "  format_sortie VARCHAR(50) NOT NULL," +
            "  options TEXT NOT NULL," +
            "  date_creation TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSQL);
        }
    }
    
    /**
     * Charge une requête sauvegardée dans l'interface
     */
    private void chargerRequeteSauvegardee(RequeteSauvegardee requete) {
        // Effacer les contraintes actuelles
        contraintes.clear();
        contraintesListView.getItems().clear();
        
        // Charger les contraintes
        String[] contraintesArr = requete.getContraintes().split(";");
        for (String contrainte : contraintesArr) {
            if (!contrainte.trim().isEmpty()) {
                contraintes.add(contrainte);
                contraintesListView.getItems().add(contrainte);
            }
        }
        
        // Définir le format de sortie
        formatSortieComboBox.setValue(requete.getFormatSortie());
        
        // Mettre à jour le conteneur d'options
        updateOptionsContainer();
        
        // Charger les options
        Map<String, String> options = parseOptionsJson(requete.getOptionsJson());
        appliquerOptions(options, requete.getFormatSortie());
        
        // Mettre à jour le nom de la requête
        nomRequeteTextField.setText(requete.getNom());
    }

    /**
     * Parse les options JSON en Map
     */
    private Map<String, String> parseOptionsJson(String optionsJson) {
        Map<String, String> options = new HashMap<>();
        
        // Simple parsing JSON (sans utiliser de bibliothèque externe)
        if (optionsJson.startsWith("{") && optionsJson.endsWith("}")) {
            String content = optionsJson.substring(1, optionsJson.length() - 1);
            String[] pairs = content.split(",");
            
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim().replace("\"", "");
                    options.put(key, value);
                }
            }
        }
        
        return options;
    }

    /**
     * Applique les options chargées à l'interface
     */
    private void appliquerOptions(Map<String, String> options, String formatSortie) {
        switch (formatSortie) {
            case "Liste":
                if (options.containsKey("colonneAffichage")) {
                    colonneAffichageComboBox.setValue(options.get("colonneAffichage"));
                }
                if (options.containsKey("tri")) {
                    triComboBox.setValue(options.get("tri"));
                }
                if (options.containsKey("colonneTri")) {
                    colonneTriComboBox.setValue(options.get("colonneTri"));
                }
                break;
                
            case "Tableau":
                if (options.containsKey("colonnes")) {
                    String[] colonnes = options.get("colonnes").split(",");
                    Set<String> colonnesSet = new HashSet<>(Arrays.asList(colonnes));
                    
                    for (CheckBox cb : selectedColumnsCheckBoxes) {
                        cb.setSelected(colonnesSet.contains(cb.getText()));
                    }
                }
                if (options.containsKey("tri")) {
                    triComboBox.setValue(options.get("tri"));
                }
                if (options.containsKey("colonneTri")) {
                    colonneTriComboBox.setValue(options.get("colonneTri"));
                }
                break;
                
            case "Graphique":
                if (options.containsKey("typeGraphique")) {
                    typeGraphiqueComboBox.setValue(options.get("typeGraphique"));
                }
                if (options.containsKey("colonneGroupBy")) {
                    colonneGroupByComboBox.setValue(options.get("colonneGroupBy"));
                }
                if (options.containsKey("graphique2Variables")) {
                    graphique2Variables.setSelected("true".equals(options.get("graphique2Variables")));
                }
                if (options.containsKey("colonneX")) {
                    colonneX.setValue(options.get("colonneX"));
                }
                if (options.containsKey("colonneY")) {
                    colonneY.setValue(options.get("colonneY"));
                }
                // Mettre à jour les options d'affichage après avoir défini la checkbox
                updateOptionsContainer();
                break;
        }
    }

    /**
     * Supprime une requête sauvegardée
     */
    @FXML
    private void supprimerRequete() {
        RequeteSauvegardee selectedRequete = requetesSauvegardeesListView.getSelectionModel().getSelectedItem();
        if (selectedRequete == null) {
            showAlert("Erreur", "Aucune requête sélectionnée");
            return;
        }
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Supprimer la requête");
        alert.setContentText("Êtes-vous sûr de vouloir supprimer la requête \"" + selectedRequete.getNom() + "\" ?");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM requetes_sauvegardees WHERE id = ?")) {
                
                stmt.setInt(1, selectedRequete.getId());
                stmt.executeUpdate();
                
                // Rafraîchir la liste
                chargerRequetesSauvegardees();
                showAlert("Information", "Requête supprimée avec succès");
                HistoryManager.logDeletion("Requêtes spéciales", 
                        "Suppression de la requête: " + selectedRequete.getNom());
                
            } catch (SQLException e) {
                e.printStackTrace();
                showAlert("Erreur", "Erreur lors de la suppression de la requête: " + e.getMessage());
            }
        }
    }
    
    /**
     * Exécute la requête sauvegardée sélectionnée
     */
    @FXML
    private void executerRequeteSauvegardee() {
        RequeteSauvegardee selectedRequete = requetesSauvegardeesListView.getSelectionModel().getSelectedItem();
        if (selectedRequete == null) {
            showAlert("Erreur", "Aucune requête sélectionnée");
            return;
        }
        
        // Charger la requête dans l'interface
        chargerRequeteSauvegardee(selectedRequete);
        
        // Exécuter la requête
        executerRequete();
    }

    /**
     * Affiche une boîte de dialogue d'alerte
     */
    private void showAlert(String title, String message) {
        Alert.AlertType type = title.startsWith("Erreur") ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION;
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Classe pour représenter une requête sauvegardée
     */
    public class RequeteSauvegardee {
        private final int id;
        private final String nom;
        private final String contraintes;
        private final String formatSortie;
        private final String optionsJson;
        
        public RequeteSauvegardee(int id, String nom, String contraintes, String formatSortie, String optionsJson) {
            this.id = id;
            this.nom = nom;
            this.contraintes = contraintes;
            this.formatSortie = formatSortie;
            this.optionsJson = optionsJson;
        }
        
        public int getId() {
            return id;
        }
        
        public String getNom() {
            return nom;
        }
        
        public String getContraintes() {
            return contraintes;
        }
        
        public String getFormatSortie() {
            return formatSortie;
        }
        
        public String getOptionsJson() {
            return optionsJson;
        }
        
        @Override
        public String toString() {
            return nom;
        }
    }
}