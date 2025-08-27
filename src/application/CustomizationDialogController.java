package application;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.collections.FXCollections;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contrôleur pour le dialogue de personnalisation des graphiques
 */
public class CustomizationDialogController {
    private static final Logger LOGGER = Logger.getLogger(CustomizationDialogController.class.getName());
    
    private final String userService;
    private final List<String> availableTables;
    private final Runnable onConfigurationChanged;
    
    private Stage dialogStage;
    private List<ChartPersistenceService.ChartConfig> currentConfigs;
    
    public CustomizationDialogController(String userService, List<String> availableTables, 
                                       Runnable onConfigurationChanged) {
        this.userService = userService;
        this.availableTables = availableTables;
        this.onConfigurationChanged = onConfigurationChanged;
        this.currentConfigs = ChartPersistenceService.loadChartConfigs(userService);
    }
    
    /**
     * Affiche le dialogue de personnalisation
     */
    public void showDialog() {
        dialogStage = new Stage();
        dialogStage.setTitle("Personnaliser le Dashboard - " + userService);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setResizable(true);
        
        VBox root = createDialogContent();
        
        Scene scene = new Scene(root, 900, 700);
        scene.getStylesheets().add(getClass().getResource("dashboardCss.css").toExternalForm());
        
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }
    
    /**
     * Crée le contenu principal du dialogue
     */
    private VBox createDialogContent() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: #f8f9fa;");
        
        // En-tête
        Label title = new Label("🎨 Personnalisation du Dashboard");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        Label subtitle = new Label("Gérez vos graphiques personnalisés et créez de nouveaux affichages");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d;");
        
        VBox header = new VBox(5, title, subtitle);
        header.setAlignment(Pos.CENTER);
        
        // Contenu principal avec onglets
        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
            createExistingChartsTab(),
            createSingleVariableTab(),
            createCrossTableTab()
        );
        
        // Boutons de contrôle
        HBox buttons = createControlButtons();
        
        root.getChildren().addAll(header, new Separator(), tabPane, buttons);
        
        return root;
    }
    
    /**
     * Crée l'onglet des graphiques existants
     */
    private Tab createExistingChartsTab() {
        Tab tab = new Tab("📊 Graphiques actuels");
        tab.setClosable(false);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label info = new Label("Gérez vos graphiques existants");
        info.setStyle("-fx-font-size: 14px; -fx-text-fill: #495057;");
        
        // Liste des graphiques existants
        ListView<ChartPersistenceService.ChartConfig> chartsList = new ListView<>();
        chartsList.setItems(FXCollections.observableArrayList(currentConfigs));
        chartsList.setPrefHeight(300);
        
        chartsList.setCellFactory(listView -> new ListCell<ChartPersistenceService.ChartConfig>() {
            @Override
            protected void updateItem(ChartPersistenceService.ChartConfig config, boolean empty) {
                super.updateItem(config, empty);
                if (empty || config == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox container = new HBox(10);
                    container.setAlignment(Pos.CENTER_LEFT);
                    container.setPadding(new Insets(8));
                    
                    String typeIcon = getChartTypeIcon(config.getChartType());
                    Label icon = new Label(typeIcon);
                    icon.setStyle("-fx-font-size: 18px;");
                    
                    VBox info = new VBox(2);
                    Label title = new Label(config.getChartTitle());
                    title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                    
                    String details = config.isCrossTable() 
                        ? String.format("%s.%s × %s.%s", config.getTableName(), config.getColumnName(),
                                      config.getTableName2(), config.getColumnName2())
                        : String.format("%s.%s", config.getTableName(), config.getColumnName());
                    
                    Label subtitle = new Label(details);
                    subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
                    
                    info.getChildren().addAll(title, subtitle);
                    
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    
                    Button deleteBtn = new Button("🗑️");
                    deleteBtn.getStyleClass().add("delete-button");
                    deleteBtn.setOnAction(e -> deleteChart(config, chartsList));
                    deleteBtn.setDisable(config.isDefault()); // Ne pas supprimer les graphiques par défaut
                    
                    container.getChildren().addAll(icon, info, spacer, deleteBtn);
                    setGraphic(container);
                }
            }
        });
        
        content.getChildren().addAll(info, chartsList);
        tab.setContent(new ScrollPane(content));
        
        return tab;
    }
    
    /**
     * Crée l'onglet pour les graphiques à variable unique
     */
    private Tab createSingleVariableTab() {
        Tab tab = new Tab("📈 Graphique simple");
        tab.setClosable(false);
        
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        Label info = new Label("Créez un graphique basé sur une seule colonne");
        info.setStyle("-fx-font-size: 14px; -fx-text-fill: #495057;");
        
        // Formulaire de création
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.getStyleClass().add("form-grid");
        
        // Table
        Label tableLabel = new Label("Table:");
        tableLabel.getStyleClass().add("form-label");
        ComboBox<String> tableCombo = new ComboBox<>();
        tableCombo.setItems(FXCollections.observableArrayList(availableTables));
        tableCombo.setPromptText("Sélectionnez une table...");
        tableCombo.setPrefWidth(250);
        
        // Colonne
        Label columnLabel = new Label("Colonne:");
        columnLabel.getStyleClass().add("form-label");
        ComboBox<String> columnCombo = new ComboBox<>();
        columnCombo.setPromptText("Sélectionnez une colonne...");
        columnCombo.setPrefWidth(250);
        columnCombo.setDisable(true);
        
        // Type de graphique
        Label typeLabel = new Label("Type:");
        typeLabel.getStyleClass().add("form-label");
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.setItems(FXCollections.observableArrayList(ChartFactory.ChartType.getDisplayNames()));
        typeCombo.setValue("Barres");
        typeCombo.setPrefWidth(250);
        
        // Titre personnalisé
        Label titleLabel = new Label("Titre:");
        titleLabel.getStyleClass().add("form-label");
        TextField titleField = new TextField();
        titleField.setPromptText("Titre du graphique (optionnel)");
        titleField.setPrefWidth(250);
        
        // Événement de changement de table
        tableCombo.setOnAction(e -> {
            String selectedTable = tableCombo.getValue();
            if (selectedTable != null) {
                loadColumnsForTable(selectedTable, columnCombo);
                columnCombo.setDisable(false);
                
                // Générer un titre automatique si vide
                if (titleField.getText().isEmpty()) {
                    titleField.setText("Répartition par " + selectedTable.replace("_", " "));
                }
            }
        });
        
        form.add(tableLabel, 0, 0);
        form.add(tableCombo, 1, 0);
        form.add(columnLabel, 0, 1);
        form.add(columnCombo, 1, 1);
        form.add(typeLabel, 0, 2);
        form.add(typeCombo, 1, 2);
        form.add(titleLabel, 0, 3);
        form.add(titleField, 1, 3);
        
        // Bouton de création
        Button createBtn = new Button("✨ Créer le graphique");
        createBtn.getStyleClass().addAll("primary-button", "large-button");
        createBtn.setOnAction(e -> createSingleVariableChart(
            tableCombo.getValue(), columnCombo.getValue(), 
            typeCombo.getValue(), titleField.getText()
        ));
        
        VBox formContainer = new VBox(20, info, form, createBtn);
        formContainer.setAlignment(Pos.TOP_CENTER);
        
        content.getChildren().add(formContainer);
        tab.setContent(new ScrollPane(content));
        
        return tab;
    }
    
    /**
     * Crée l'onglet pour les graphiques croisés
     */
    private Tab createCrossTableTab() {
        Tab tab = new Tab("🔗 Graphique croisé");
        tab.setClosable(false);
        
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));
        
        Label info = new Label("Créez un graphique en croisant deux variables de tables différentes");
        info.setStyle("-fx-font-size: 14px; -fx-text-fill: #495057;");
        
        // Formulaire de création croisée
        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.getStyleClass().add("form-grid");
        
        // Première variable
        Label var1Label = new Label("Variable 1:");
        var1Label.getStyleClass().add("form-section-header");
        
        ComboBox<String> table1Combo = new ComboBox<>();
        table1Combo.setItems(FXCollections.observableArrayList(availableTables));
        table1Combo.setPromptText("Table 1...");
        table1Combo.setPrefWidth(200);
        
        ComboBox<String> column1Combo = new ComboBox<>();
        column1Combo.setPromptText("Colonne 1...");
        column1Combo.setPrefWidth(200);
        column1Combo.setDisable(true);
        
        // Deuxième variable
        Label var2Label = new Label("Variable 2:");
        var2Label.getStyleClass().add("form-section-header");
        
        ComboBox<String> table2Combo = new ComboBox<>();
        table2Combo.setItems(FXCollections.observableArrayList(availableTables));
        table2Combo.setPromptText("Table 2...");
        table2Combo.setPrefWidth(200);
        
        ComboBox<String> column2Combo = new ComboBox<>();
        column2Combo.setPromptText("Colonne 2...");
        column2Combo.setPrefWidth(200);
        column2Combo.setDisable(true);
        
        // Type de croisement
        Label crossTypeLabel = new Label("Type de croisement:");
        crossTypeLabel.getStyleClass().add("form-label");
        
        ComboBox<String> crossTypeCombo = new ComboBox<>();
        crossTypeCombo.setItems(FXCollections.observableArrayList(
            "Barres empilées", "Barres groupées", "Carte de chaleur", "Nuage de points"
        ));
        crossTypeCombo.setValue("Barres empilées");
        crossTypeCombo.setPrefWidth(200);
        
        // Titre
        TextField crossTitleField = new TextField();
        crossTitleField.setPromptText("Titre du graphique croisé");
        crossTitleField.setPrefWidth(400);
        
        // Événements
        table1Combo.setOnAction(e -> {
            if (table1Combo.getValue() != null) {
                loadColumnsForTable(table1Combo.getValue(), column1Combo);
                column1Combo.setDisable(false);
                updateCrossTitle(table1Combo, column1Combo, table2Combo, column2Combo, crossTitleField);
            }
        });
        
        table2Combo.setOnAction(e -> {
            if (table2Combo.getValue() != null) {
                loadColumnsForTable(table2Combo.getValue(), column2Combo);
                column2Combo.setDisable(false);
                updateCrossTitle(table1Combo, column1Combo, table2Combo, column2Combo, crossTitleField);
            }
        });
        
        column1Combo.setOnAction(e -> 
            updateCrossTitle(table1Combo, column1Combo, table2Combo, column2Combo, crossTitleField));
        column2Combo.setOnAction(e -> 
            updateCrossTitle(table1Combo, column1Combo, table2Combo, column2Combo, crossTitleField));
        
        // Arrangement du formulaire
        form.add(var1Label, 0, 0, 2, 1);
        form.add(new Label("Table 1:"), 0, 1);
        form.add(table1Combo, 1, 1);
        form.add(new Label("Colonne 1:"), 0, 2);
        form.add(column1Combo, 1, 2);
        
        form.add(var2Label, 0, 3, 2, 1);
        form.add(new Label("Table 2:"), 0, 4);
        form.add(table2Combo, 1, 4);
        form.add(new Label("Colonne 2:"), 0, 5);
        form.add(column2Combo, 1, 5);
        
        form.add(crossTypeLabel, 0, 6);
        form.add(crossTypeCombo, 1, 6);
        form.add(new Label("Titre:"), 0, 7);
        form.add(crossTitleField, 1, 7);
        
        // Bouton de création
        Button createCrossBtn = new Button("🎯 Créer le graphique croisé");
        createCrossBtn.getStyleClass().addAll("primary-button", "large-button");
        createCrossBtn.setOnAction(e -> createCrossTableChart(
            table1Combo.getValue(), column1Combo.getValue(),
            table2Combo.getValue(), column2Combo.getValue(),
            crossTypeCombo.getValue(), crossTitleField.getText()
        ));
        
        VBox formContainer = new VBox(20, info, form, createCrossBtn);
        formContainer.setAlignment(Pos.TOP_CENTER);
        
        content.getChildren().add(formContainer);
        tab.setContent(new ScrollPane(content));
        
        return tab;
    }
    
    /**
     * Crée les boutons de contrôle du dialogue
     */
    private HBox createControlButtons() {
        HBox buttons = new HBox(15);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(20, 0, 0, 0));
        
        Button resetBtn = new Button("🔄 Réinitialiser");
        resetBtn.getStyleClass().add("secondary-button");
        resetBtn.setOnAction(e -> resetToDefaults());
        
        Button closeBtn = new Button("✅ Fermer");
        closeBtn.getStyleClass().add("primary-button");
        closeBtn.setOnAction(e -> dialogStage.close());
        
        buttons.getChildren().addAll(resetBtn, closeBtn);
        
        return buttons;
    }
    
    /**
     * Charge les colonnes pour une table donnée
     */
    private void loadColumnsForTable(String tableName, ComboBox<String> columnCombo) {
        columnCombo.getItems().clear();
        List<String> columns = TableColumnManager.getColumnsForTable(tableName);
        
        // Filtrer les colonnes appropriées pour les graphiques
        List<String> graphableColumns = columns.stream()
            .filter(col -> !col.equalsIgnoreCase("id") && !col.toLowerCase().startsWith("id_"))
            .sorted()
            .toList();
        
        columnCombo.setItems(FXCollections.observableArrayList(graphableColumns));
    }
    
    /**
     * Met à jour automatiquement le titre du graphique croisé
     */
    private void updateCrossTitle(ComboBox<String> table1Combo, ComboBox<String> column1Combo,
                                ComboBox<String> table2Combo, ComboBox<String> column2Combo,
                                TextField titleField) {
        if (titleField.getText().isEmpty() || titleField.getText().startsWith("Croisement")) {
            String col1 = column1Combo.getValue();
            String col2 = column2Combo.getValue();
            
            if (col1 != null && col2 != null) {
                titleField.setText(String.format("Croisement %s × %s", col1, col2));
            }
        }
    }
    
    /**
     * Crée un graphique à variable unique
     */
    private void createSingleVariableChart(String tableName, String columnName, String chartType, String title) {
        if (tableName == null || columnName == null || chartType == null) {
            showErrorAlert("Paramètres manquants", "Veuillez remplir tous les champs obligatoires.");
            return;
        }
        
        if (title == null || title.trim().isEmpty()) {
            title = String.format("Répartition par %s", columnName);
        }
        
        // Générer un ID unique
        String chartId = "custom_" + tableName + "_" + columnName + "_" + System.currentTimeMillis();
        String typeCode = ChartFactory.ChartType.getCodeFromDisplayName(chartType);
        
        // Créer la configuration
        ChartPersistenceService.ChartConfig config = new ChartPersistenceService.ChartConfig(
            chartId, typeCode, title, tableName, columnName, null, null, false, false, 
            currentConfigs.size()
        );
        
        // Sauvegarder
        ChartPersistenceService.saveChartConfig(userService, config);
        currentConfigs.add(config);
        
        showInformation("Graphique créé", "Le graphique '" + title + "' a été créé avec succès.");
        
        // Notifier le changement
        if (onConfigurationChanged != null) {
            onConfigurationChanged.run();
        }
        
        LOGGER.info("Graphique simple créé: " + title);
    }
    
    /**
     * Crée un graphique croisé
     */
    private void createCrossTableChart(String table1, String column1, String table2, String column2, 
                                     String chartType, String title) {
        if (table1 == null || column1 == null || table2 == null || column2 == null || chartType == null) {
            showErrorAlert("Paramètres manquants", "Veuillez remplir tous les champs obligatoires.");
            return;
        }
        
        if (table1.equals(table2)) {
            showErrorAlert("Tables identiques", "Veuillez sélectionner deux tables différentes.");
            return;
        }
        
        if (title == null || title.trim().isEmpty()) {
            title = String.format("Croisement %s × %s", column1, column2);
        }
        
        // Générer un ID unique
        String chartId = "cross_" + table1 + "_" + column1 + "_" + table2 + "_" + column2 + "_" + System.currentTimeMillis();
        String typeCode = getCrossChartTypeCode(chartType);
        
        // Créer la configuration
        ChartPersistenceService.ChartConfig config = new ChartPersistenceService.ChartConfig(
            chartId, typeCode, title, table1, column1, table2, column2, true, false, 
            currentConfigs.size()
        );
        
        // Sauvegarder
        ChartPersistenceService.saveChartConfig(userService, config);
        currentConfigs.add(config);
        
        showInformation("Graphique croisé créé", "Le graphique '" + title + "' a été créé avec succès.");
        
        // Notifier le changement
        if (onConfigurationChanged != null) {
            onConfigurationChanged.run();
        }
        
        LOGGER.info("Graphique croisé créé: " + title);
    }
    
    /**
     * Supprime un graphique
     */
    private void deleteChart(ChartPersistenceService.ChartConfig config, ListView<ChartPersistenceService.ChartConfig> listView) {
        if (config.isDefault()) {
            showErrorAlert("Suppression interdite", "Impossible de supprimer un graphique par défaut.");
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmer la suppression");
        confirmAlert.setHeaderText("Supprimer le graphique");
        confirmAlert.setContentText("Êtes-vous sûr de vouloir supprimer '" + config.getChartTitle() + "' ?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            ChartPersistenceService.deleteChartConfig(userService, config.getChartId());
            currentConfigs.remove(config);
            listView.setItems(FXCollections.observableArrayList(currentConfigs));
            
            showInformation("Suppression réussie", "Le graphique a été supprimé avec succès.");
            
            // Notifier le changement
            if (onConfigurationChanged != null) {
                onConfigurationChanged.run();
            }
        }
    }
    
    /**
     * Remet à zéro la configuration (graphiques par défaut uniquement)
     */
    private void resetToDefaults() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Réinitialisation");
        confirmAlert.setHeaderText("Remettre à zéro la configuration");
        confirmAlert.setContentText("Cette action supprimera tous vos graphiques personnalisés. Continuer ?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Supprimer tous les graphiques non-par défaut
            currentConfigs.removeIf(config -> {
                if (!config.isDefault()) {
                    ChartPersistenceService.deleteChartConfig(userService, config.getChartId());
                    return true;
                }
                return false;
            });
            
            showInformation("Réinitialisation réussie", "Les graphiques par défaut ont été restaurés.");
            
            // Notifier le changement
            if (onConfigurationChanged != null) {
                onConfigurationChanged.run();
            }
            
            dialogStage.close();
        }
    }
    
    /**
     * Obtient l'icône pour un type de graphique
     */
    private String getChartTypeIcon(String chartType) {
        switch (chartType.toLowerCase()) {
            case "pie": return "🥧";
            case "bar": return "📊";
            case "line": return "📈";
            case "area": return "🏔️";
            case "scatter": return "🎯";
            case "stacked": return "📚";
            case "grouped": return "🏗️";
            case "heatmap": return "🔥";
            default: return "📊";
        }
    }
    
    /**
     * Obtient le code de type pour un graphique croisé
     */
    private String getCrossChartTypeCode(String displayName) {
        switch (displayName) {
            case "Barres empilées": return "stacked";
            case "Barres groupées": return "grouped";
            case "Carte de chaleur": return "heatmap";
            case "Nuage de points": return "scatter";
            default: return "stacked";
        }
    }
    
    /**
     * Affiche une alerte d'erreur
     */
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Affiche une information
     */
    private void showInformation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}