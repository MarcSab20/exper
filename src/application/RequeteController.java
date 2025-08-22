package application;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.chart.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import java.util.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.sql.*;

public class RequeteController {
    @FXML
    private ComboBox<String> colonneComboBox;
    @FXML
    private ComboBox<String> operateurComboBox;
    @FXML
    private HBox valueContainer;
    @FXML
    private ListView<String> contraintesListView;
    @FXML
    private ComboBox<String> formatSortieComboBox;
    @FXML
    private HBox optionsContainer;
    @FXML
    private StackPane resultsContainer;
    
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

    private final Map<String, String> colonneTypes = new HashMap<>();
    private final Map<String, List<String>> stringValues = new HashMap<>();
    private final List<String> contraintes = new ArrayList<>();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    
    // Informations de connexion à la base de données
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    // Liste des colonnes de la table final
    private final List<String> columnNames = Arrays.asList(
        "id", "matricule", "nom", "grade", "echelon", "date_engagement", "formation", 
        "Region_Origine", "date_Naissance", "lieu_Naissance", "departement", "arrondissement",
        "date_Grade", "ref_Grade", "ref_echelon", "date_echelon", "ethnie", "religion",
        "nom_Pere", "nom_Mere", "situation_matrimoniale", "nombre_enfants", "statut", "Origine",
        "unite", "emploi", "date_affectation", "ref_affectation", "diplome_Civil", 
        "diplome_militaire", "categorie", "specialite", "qualification", "ordre_valeur",
        "Ordre_Merite_Camerounais", "Ordre_Merite_Sportif", "Force_Publique", "Vaillance",
        "Position_SPA", "Sexe", "Etat_Punitions", "promo_contingent"
    );
    
    @FXML
    public void initialize() {
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
        
        // Charger les métadonnées de la table depuis la base de données
        loadTableMetadata();
    }
    
    private void loadTableMetadata() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Obtenir les métadonnées de la table final
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "final", null);
            
            // Charger les noms de colonnes et leurs types
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String dataType = columns.getString("TYPE_NAME");
                
                // Mapper les types SQL vers nos types simplifiés
                switch (dataType.toUpperCase()) {
                    case "VARCHAR":
                    case "CHAR":
                    case "TEXT":
                        colonneTypes.put(columnName, "STRING");
                        break;
                    case "INT":
                    case "TINYINT":
                    case "SMALLINT":
                    case "BIGINT":
                        colonneTypes.put(columnName, "INTEGER");
                        break;
                    case "DECIMAL":
                    case "FLOAT":
                    case "DOUBLE":
                        colonneTypes.put(columnName, "DECIMAL");
                        break;
                    case "DATE":
                    case "DATETIME":
                    case "TIMESTAMP":
                        colonneTypes.put(columnName, "DATE");
                        break;
                    default:
                        colonneTypes.put(columnName, "STRING");
                }
            }
            
            // Charger les valeurs possibles pour chaque colonne de type STRING
            for (String colonne : columnNames) {
                if (colonneTypes.getOrDefault(colonne, "").equals("STRING")) {
                    loadDistinctValues(conn, colonne);
                }
            }
            
            // Remplir la ComboBox des colonnes
            colonneComboBox.setItems(FXCollections.observableArrayList(columnNames));
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Erreur de base de données", "Impossible de charger les métadonnées: " + e.getMessage());
        }
    }
    
    private void loadDistinctValues(Connection conn, String colonne) {
        String query = "SELECT DISTINCT " + colonne + " FROM final WHERE " + colonne + " IS NOT NULL ORDER BY " + colonne;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            List<String> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getString(1));
            }
            
            stringValues.put(colonne, values);
            
        } catch (SQLException e) {
            e.printStackTrace();
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
            "Camembert", "Barre", "Ligne"
        ));
        colonneGroupByComboBox = new ComboBox<>();
    }
    
    private void updateValueField() {
        String colonne = colonneComboBox.getValue();
        if (colonne == null) return;

        String type = colonneTypes.get(colonne);
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
                Label typeGraphLabel = new Label("Type de graphique:");
                
                Label groupByLabel = new Label("Grouper par:");
                colonneGroupByComboBox.setItems(FXCollections.observableArrayList(columnNames));
                
                optionsContainer.getChildren().addAll(
                    typeGraphLabel, typeGraphiqueComboBox,
                    groupByLabel, colonneGroupByComboBox
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
        String type = colonneTypes.get(colonne);
        String operateur = operateurComboBox.getValue();
        
        if (type == null) return "";

        switch (type) {
            case "STRING":
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
        
        String type = colonneTypes.get(colonne);
        
        switch (type) {
            case "STRING":
                // Décocher toutes les cases
                for (CheckBox cb : valueCheckListView.getItems()) {
                    cb.setSelected(false);
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
            String query = buildSqlQuery(formatSortie);
            executeQuery(query, formatSortie);
            HistoryManager.logCreation("Requêtes", 
                    "Éxécution d'une requête ");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Erreur", "Erreur lors de l'exécution de la requête: " + e.getMessage());
        }
    }
    
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
                if (typeGraphiqueComboBox.getValue() == null) {
                    showAlert("Erreur", "Veuillez sélectionner un type de graphique");
                    return false;
                }
                if (colonneGroupByComboBox.getValue() == null) {
                    showAlert("Erreur", "Veuillez sélectionner une colonne pour le regroupement");
                    return false;
                }
                break;
        }
        
        return true;
    }
    
    private String buildSqlQuery(String formatSortie) {
        StringBuilder queryBuilder = new StringBuilder("SELECT ");
        
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
        queryBuilder.append(" FROM final");
        
        // Ajouter les contraintes (WHERE)
        if (!contraintes.isEmpty()) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(String.join(" AND ", contraintes));
        }
        
        // Ajouter GROUP BY pour les graphiques
        if (formatSortie.equals("Graphique")) {
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
                    HistoryManager.logCreation("Requêtes", 
                            "Éxécution d'une requête de format liste");
                    break;
                case "Tableau":
                    afficherResultatsTableau(rs);
                    HistoryManager.logCreation("Requêtes", 
                            "Éxécution d'une requête de format tableau");
                    break;
                case "Graphique":
                    afficherResultatsGraphique(rs);
                    HistoryManager.logCreation("Requêtes", 
                            "Éxécution d'une requête de format graphique");
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
        
        resultsContainer.getChildren().setAll(lineChart);
    }

    @FXML
    private void exporterExcel() {
        // TODO: Implémenter l'export Excel
        showAlert("Information", "La fonctionnalité d'export Excel sera implémentée prochainement");
    }

    @FXML
    private void exporterPDF() {
        // TODO: Implémenter l'export PDF
        showAlert("Information", "La fonctionnalité d'export PDF sera implémentée prochainement");
    }

    private void showAlert(String title, String content) {
        Alert.AlertType type = title.startsWith("Erreur") ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION;
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}