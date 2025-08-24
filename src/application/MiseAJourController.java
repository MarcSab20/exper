package application;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MiseAJourController {
    private static final Logger LOGGER = Logger.getLogger(MiseAJourController.class.getName());
    
    @FXML private ComboBox<String> tableComboBox;
    @FXML private Label lastUpdateLabel;
    @FXML private Label tableInfoLabel;
    @FXML private TableView<UpdateRecord> updatesTable;
    @FXML private TableColumn<UpdateRecord, String> tableColumn;
    @FXML private TableColumn<UpdateRecord, Date> dateColumn;
    @FXML private TableColumn<UpdateRecord, String> statusColumn;
    @FXML private TableColumn<UpdateRecord, String> descriptionColumn;
    @FXML private TableColumn<UpdateRecord, Integer> insertedColumn;
    @FXML private TableColumn<UpdateRecord, Integer> updatedColumn;
    @FXML private TableColumn<UpdateRecord, Integer> unchangedColumn;
    @FXML private TextField filePathField;
    @FXML private ProgressBar updateProgress;
    @FXML private Label statusLabel;
    @FXML private Button validateSchemaButton;
    @FXML private Button startUpdateButton;
    @FXML private Button generateTemplateButton;
    @FXML private Button showSchemaButton;
    @FXML private Button loadUpdateHistory;
    @FXML private Button showFullHistory;
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private String currentService;
    private TableSchemaManager.SchemaValidationResult lastValidation;
    private static final int ROWS_PER_PAGE = 20;
    
    @FXML
    public void initialize() {
        currentService = UserSession.getCurrentService();
        
        initializeColumns();
        initializeTableComboBox();
        setupEventListeners();
        loadUpdateHistory();
        updateProgress.setVisible(false);
        validateSchemaButton.setDisable(true);
        startUpdateButton.setDisable(true);
        
        setupHelpButtons();
    }
    
    private void setupHelpButtons() {
        if (generateTemplateButton != null) {
            generateTemplateButton.setDisable(true);
        }
        if (showSchemaButton != null) {
            showSchemaButton.setDisable(true);
        }
    }
    
 // Remplacez votre méthode initializeColumns() dans MiseAJourController.java par cette version :

    private void initializeColumns() {
        // Changez le type générique de EnhancedUpdateRecord vers UpdateRecord
        // Et utilisez les nouvelles méthodes property()
        
        tableColumn.setCellValueFactory(cellData -> cellData.getValue().tableNameProperty());
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("updateDate"));
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        insertedColumn.setCellValueFactory(cellData -> cellData.getValue().recordsInsertedProperty().asObject());
        updatedColumn.setCellValueFactory(cellData -> cellData.getValue().recordsUpdatedProperty().asObject());
        
        // Pour la colonne "unchangedColumn", créez une propriété calculée car elle n'existe pas dans UpdateRecord
        unchangedColumn.setCellValueFactory(cellData -> {
            // Calculer une valeur par défaut ou laisser vide pour UpdateRecord
            return new SimpleIntegerProperty(0).asObject();
        });
        
        // Formater la colonne date
        dateColumn.setCellFactory(column -> new TableCell<UpdateRecord, Date>() {
            @Override
            protected void updateItem(Date item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(dateFormat.format(item));
                }
            }
        });
        
        // Colorier la colonne statut
        statusColumn.setCellFactory(column -> new TableCell<UpdateRecord, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item.toLowerCase()) {
                        case "succès":
                            setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                            break;
                        case "échec":
                            setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                            break;
                        case "partiel":
                            setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                            break;
                        case "périmé":
                            setStyle("-fx-text-fill: gray; -fx-font-weight: bold;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            }
        });
        setupPagination();
    }
    
    private void setupPagination() {
        // Ajouter un listener pour limiter l'affichage des lignes
        updatesTable.setRowFactory(tv -> {
            TableRow<UpdateRecord> row = new TableRow<>();
            
            // Ajouter un menu contextuel pour voir les détails
            ContextMenu contextMenu = new ContextMenu();
            MenuItem detailsItem = new MenuItem("Voir les détails");
            detailsItem.setOnAction(event -> {
                if (!row.isEmpty()) {
                    showUpdateDetails(row.getItem());
                }
            });
            contextMenu.getItems().add(detailsItem);
            
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(contextMenu)
            );
            
            return row;
        });
    }
    
    private void initializeTableComboBox() {
        List<String> availableTables = ServicePermissions.getTablesForService(currentService);
        
        // Créer une liste avec descriptions conviviales
        List<String> tableDescriptions = new ArrayList<>();
        for (String table : availableTables) {
            String description = ServicePermissions.getTableDescription(table) + " (" + table + ")";
            tableDescriptions.add(description);
        }
        
        tableComboBox.setItems(FXCollections.observableArrayList(tableDescriptions));
        
        if (!tableDescriptions.isEmpty()) {
            tableComboBox.setValue(tableDescriptions.get(0));
            updateTableInfo();
        }
    }
    
    private void setupEventListeners() {
        tableComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                updateTableInfo();
                boolean hasFile = !filePathField.getText().trim().isEmpty();
                validateSchemaButton.setDisable(!hasFile);
                startUpdateButton.setDisable(true);
                if (generateTemplateButton != null) generateTemplateButton.setDisable(false);
                if (showSchemaButton != null) showSchemaButton.setDisable(false);
            }
        });
        
        filePathField.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean hasFile = newValue != null && !newValue.trim().isEmpty();
            boolean hasTable = tableComboBox.getValue() != null;
            validateSchemaButton.setDisable(!hasFile || !hasTable);
            startUpdateButton.setDisable(true);
            lastValidation = null;
        });
        
        updatesTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    showUpdateDetails(newSelection);
                }
            }
        );
    }
    
    private void updateTableInfo() {
        String selectedTable = getSelectedTableName();
        if (selectedTable != null) {
            EnhancedUpdateRecord lastUpdate = getLastUpdateForTable(selectedTable);
            
            if (lastUpdate != null) {
                String timeAgo = calculateTimeAgo(lastUpdate.getUpdateDate());
                String statusText = dateFormat.format(lastUpdate.getUpdateDate()) + 
                                  " (" + timeAgo + ") - Statut: " + lastUpdate.getStatus();
                
                // Vérifier si la table est périmée (plus de 30 jours)
                long daysAgo = ChronoUnit.DAYS.between(
                    lastUpdate.getUpdateDate().toInstant(), 
                    new Date().toInstant()
                );
                
                if (daysAgo > 30) {
                    statusText += " ⚠️ TABLE PÉRIMÉE";
                    lastUpdateLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                } else {
                    lastUpdateLabel.setStyle("-fx-text-fill: black;");
                }
                
                lastUpdateLabel.setText(statusText);
            } else {
                lastUpdateLabel.setText("Aucune mise à jour effectuée pour cette table");
                lastUpdateLabel.setStyle("-fx-text-fill: gray;");
            }
            
            // Afficher des informations sur la table avec le nombre de colonnes réel
            Set<String> tableColumns = TableSchemaManager.getTableColumnNames(selectedTable);
            Set<String> requiredColumns = TableSchemaManager.getRequiredColumns(selectedTable);
            
            String tableInfo = String.format("Table: %s | Colonnes: %d (dont %d obligatoires) | Clé: %s | Service: %s",
                selectedTable,
                tableColumns.size(),
                requiredColumns.size(),
                ServicePermissions.getPrimaryKeyColumn(selectedTable),
                currentService
            );
            tableInfoLabel.setText(tableInfo);
        }
    }
    
    private String calculateTimeAgo(Date date) {
        long diffInMillis = System.currentTimeMillis() - date.getTime();
        long days = diffInMillis / (24 * 60 * 60 * 1000);
        
        if (days == 0) {
            return "aujourd'hui";
        } else if (days == 1) {
            return "il y a 1 jour";
        } else if (days < 30) {
            return "il y a " + days + " jours";
        } else if (days < 365) {
            long months = days / 30;
            return "il y a " + months + " mois";
        } else {
            long years = days / 365;
            return "il y a " + years + " an" + (years > 1 ? "s" : "");
        }
    }
    
    private String getSelectedTableName() {
        String selected = tableComboBox.getValue();
        if (selected == null) return null;
        
        // Extraire le nom de table entre parenthèses
        int start = selected.lastIndexOf('(');
        int end = selected.lastIndexOf(')');
        if (start != -1 && end != -1 && end > start) {
            return selected.substring(start + 1, end);
        }
        return null;
    }
    
    @FXML
    private void browseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers CSV", "*.csv")
        );
        fileChooser.setTitle("Sélectionner un fichier CSV");
        
        Stage stage = (Stage) filePathField.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        
        if (file != null) {
            filePathField.setText(file.getAbsolutePath());
            statusLabel.setText("Fichier sélectionné: " + file.getName());
        }
    }
    
    @FXML
    private void validateSchema() {
        String selectedTable = getSelectedTableName();
        String filePath = filePathField.getText();
        
        if (selectedTable == null || filePath.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Sélection incomplète", 
                      "Veuillez sélectionner une table et un fichier");
            return;
        }
        
        File csvFile = new File(filePath);
        if (!csvFile.exists()) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Fichier introuvable", 
                      "Le fichier sélectionné n'existe pas");
            return;
        }
        
        try {
            lastValidation = CSVProcessor.validateCSVSchema(csvFile, selectedTable);
            
            if (lastValidation.isValid()) {
                showAlert(Alert.AlertType.INFORMATION, "Validation réussie", 
                          "Schéma compatible", 
                          String.format("✅ Le fichier CSV est compatible!\n\nStatistiques:\n- Colonnes CSV: %d\n- Colonnes table: %d\n- Correspondances: %d", 
                                       lastValidation.getTotalCsvColumns(),
                                       lastValidation.getTotalTableColumns(),
                                       lastValidation.getMatchingColumns()));
                startUpdateButton.setDisable(false);
                statusLabel.setText("✅ Schéma validé - Prêt pour la mise à jour");
            } else {
                showSchemaValidationError(lastValidation);
                startUpdateButton.setDisable(true);
                statusLabel.setText("❌ Schéma incompatible");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la validation du schéma", e);
            showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur de validation", 
                      "Impossible de valider le schéma: " + e.getMessage());
            startUpdateButton.setDisable(true);
        }
    }
    
    private void showSchemaValidationError(TableSchemaManager.SchemaValidationResult validation) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Schéma incompatible");
        alert.setHeaderText("Le fichier CSV n'est pas compatible avec la table sélectionnée");
        
        // Créer un contenu avec le rapport détaillé
        TextArea reportArea = new TextArea(validation.getDetailedReport());
        reportArea.setEditable(false);
        reportArea.setWrapText(true);
        reportArea.setPrefRowCount(15);
        reportArea.setPrefColumnCount(80);
        
        VBox content = new VBox(10);
        content.getChildren().add(new Label("Rapport détaillé de validation:"));
        content.getChildren().add(reportArea);
        
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(700);
        alert.getDialogPane().setPrefHeight(500);
        alert.showAndWait();
    }
    
    @FXML
    private void generateTemplate() {
        String selectedTable = getSelectedTableName();
        if (selectedTable == null) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Sélection requise", 
                      "Veuillez sélectionner une table");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer le template CSV");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers CSV", "*.csv")
        );
        fileChooser.setInitialFileName("template_" + selectedTable + ".csv");
        
        Stage stage = (Stage) tableComboBox.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);
        
        if (file != null) {
            try {
                CSVProcessor.generateCSVTemplate(selectedTable, file);
                showAlert(Alert.AlertType.INFORMATION, "Template généré", 
                          "Succès", 
                          "Le template CSV a été généré avec succès dans:\n" + file.getAbsolutePath() + 
                          "\n\nVous pouvez maintenant l'ouvrir dans Excel, le remplir avec vos données et l'utiliser pour la mise à jour.");
                HistoryManager.logCreation("Mise à jour", 
                        "Génération de template CSV pour " + selectedTable);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erreur lors de la génération du template", e);
                showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur de génération", 
                          "Impossible de générer le template: " + e.getMessage());
            }
        }
    }
    
    @FXML
    private void showSchema() {
        String selectedTable = getSelectedTableName();
        if (selectedTable == null) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Sélection requise", 
                      "Veuillez sélectionner une table");
            return;
        }
        
        try {
            String schemaInfo = TableSchemaManager.getFormattedTableSchema(selectedTable);
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Schéma de la table " + selectedTable);
            alert.setHeaderText("Structure détaillée de la table");
            
            TextArea schemaArea = new TextArea(schemaInfo);
            schemaArea.setEditable(false);
            schemaArea.setWrapText(false);
            schemaArea.setPrefRowCount(20);
            schemaArea.setPrefColumnCount(100);
            schemaArea.setStyle("-fx-font-family: monospace");
            
            VBox content = new VBox(10);
            content.getChildren().add(new Label("Colonnes et types de données:"));
            content.getChildren().add(schemaArea);
            
            alert.getDialogPane().setContent(content);
            alert.getDialogPane().setPrefWidth(800);
            alert.getDialogPane().setPrefHeight(600);
            alert.showAndWait();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'affichage du schéma", e);
            showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur d'affichage", 
                      "Impossible d'afficher le schéma: " + e.getMessage());
        }
    }
    

    /**
     * Affiche tout l'historique dans une nouvelle fenêtre
     */
    @FXML
    private void showFullHistory() {
        try {
            // Créer une nouvelle fenêtre pour l'historique complet
            Stage historyStage = new Stage();
            historyStage.setTitle("Historique complet des mises à jour");
            historyStage.initModality(Modality.APPLICATION_MODAL);
            
            VBox content = new VBox(10);
            content.setPadding(new Insets(20));
            
            // Créer un nouveau TableView pour l'historique complet
            TableView<UpdateRecord> fullHistoryTable = new TableView<>();
            
            // Configurer les colonnes
            TableColumn<UpdateRecord, String> tableCol = new TableColumn<>("Table");
            tableCol.setCellValueFactory(cellData -> cellData.getValue().tableNameProperty());
            
            TableColumn<UpdateRecord, Date> dateCol = new TableColumn<>("Date");
            dateCol.setCellValueFactory(new PropertyValueFactory<>("updateDate"));
            dateCol.setCellFactory(column -> new TableCell<UpdateRecord, Date>() {
                @Override
                protected void updateItem(Date item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(dateFormat.format(item));
                    }
                }
            });
            
            TableColumn<UpdateRecord, String> statusCol = new TableColumn<>("Statut");
            statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
            statusCol.setCellFactory(column -> new TableCell<UpdateRecord, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        switch (item.toLowerCase()) {
                            case "succès":
                                setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                                break;
                            case "échec":
                                setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                                break;
                            case "partiel":
                                setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                                break;
                        }
                    }
                }
            });
            
            TableColumn<UpdateRecord, Integer> insertedCol = new TableColumn<>("Nouveaux");
            insertedCol.setCellValueFactory(cellData -> cellData.getValue().recordsInsertedProperty().asObject());
            
            TableColumn<UpdateRecord, Integer> updatedCol = new TableColumn<>("Modifiés");
            updatedCol.setCellValueFactory(cellData -> cellData.getValue().recordsUpdatedProperty().asObject());
            
            fullHistoryTable.getColumns().addAll(tableCol, dateCol, statusCol, insertedCol, updatedCol);
            
            // Charger toutes les données
            List<EnhancedUpdateRecord> allEnhancedUpdates = EnhancedDatabaseManager.getEnhancedUpdateHistory();
            List<UpdateRecord> allUpdates = new ArrayList<>();
            
            for (EnhancedUpdateRecord enhanced : allEnhancedUpdates) {
                UpdateRecord update = new UpdateRecord(
                    enhanced.getId(),
                    enhanced.getTableName(),
                    new Timestamp(enhanced.getUpdateDate().getTime()),
                    enhanced.getStatus(),
                    enhanced.getDescription(),
                    enhanced.getRecordsUpdated(),
                    enhanced.getRecordsInserted()
                );
                allUpdates.add(update);
            }
            
            fullHistoryTable.setItems(FXCollections.observableArrayList(allUpdates));
            
            // Configurer le menu contextuel pour les détails
            fullHistoryTable.setRowFactory(tv -> {
                TableRow<UpdateRecord> row = new TableRow<>();
                ContextMenu contextMenu = new ContextMenu();
                MenuItem detailsItem = new MenuItem("Voir les détails");
                detailsItem.setOnAction(event -> {
                    if (!row.isEmpty()) {
                        showUpdateDetails(row.getItem());
                    }
                });
                contextMenu.getItems().add(detailsItem);
                
                row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(contextMenu)
                );
                
                return row;
            });
            
            Label countLabel = new Label("Total: " + allUpdates.size() + " mises à jour");
            countLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            
            Button closeButton = new Button("Fermer");
            closeButton.setOnAction(e -> historyStage.close());
            
            content.getChildren().addAll(countLabel, fullHistoryTable, closeButton);
            VBox.setVgrow(fullHistoryTable, Priority.ALWAYS);
            
            Scene scene = new Scene(content, 800, 600);
            historyStage.setScene(scene);
            historyStage.show();
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'affichage de l'historique complet", e);
            showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur d'affichage", 
                     "Impossible d'afficher l'historique complet: " + e.getMessage());
        }
    }
    
    /**
     * NOUVEAU bouton d'analyse des matricules avant mise à jour
     */
    @FXML
    private void analyzerMatricules() {
        String selectedTable = getSelectedTableName();
        String filePath = filePathField.getText();
        
        if (selectedTable == null || filePath.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Sélection incomplète", 
                      "Veuillez sélectionner une table et un fichier");
            return;
        }
        
        File csvFile = new File(filePath);
        if (!csvFile.exists()) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Fichier introuvable", 
                      "Le fichier sélectionné n'existe pas");
            return;
        }
        
        try {
            // Analyser les matricules avec la nouvelle méthode
            CSVProcessor.AnalysisResult analysis = CSVProcessor.analyzeCSVMatricules(csvFile, selectedTable);
            
            // Afficher le rapport d'analyse amélioré
            showEnhancedAnalysisDialog(analysis, selectedTable);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'analyse des matricules", e);
            showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur d'analyse", 
                      "Impossible d'analyser les matricules: " + e.getMessage());
        }
    }
    
    /**
     * NOUVELLE MÉTHODE : Dialogue d'analyse amélioré
     */
    private void showEnhancedAnalysisDialog(CSVProcessor.AnalysisResult analysis, String tableName) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Analyse des matricules - " + tableName);
        alert.setHeaderText("Rapport de compatibilité des matricules");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Statistiques générales avec icônes
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(15);
        statsGrid.setVgap(8);
        
        statsGrid.add(new Label("📄 Matricules dans le CSV:"), 0, 0);
        statsGrid.add(new Label(String.valueOf(analysis.getCsvMatricules().size())), 1, 0);
        
        statsGrid.add(new Label("✅ Matricules valides en DB:"), 0, 1);
        statsGrid.add(new Label(String.valueOf(analysis.getValidMatricules().size())), 1, 1);
        
        statsGrid.add(new Label("❌ Matricules manquants:"), 0, 2);
        Label missingCountLabel = new Label(String.valueOf(analysis.getMissingMatricules().size()));
        if (analysis.getMissingMatricules().size() > 0) {
            missingCountLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        } else {
            missingCountLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        }
        statsGrid.add(missingCountLabel, 1, 2);
        
        // Calculer le pourcentage de validité
        double validPercentage = analysis.getCsvMatricules().size() > 0 ? 
            ((double)(analysis.getCsvMatricules().size() - analysis.getMissingMatricules().size()) / 
             analysis.getCsvMatricules().size()) * 100.0 : 0.0;
        
        statsGrid.add(new Label("📊 Taux de validité:"), 0, 3);
        Label percentageLabel = new Label(String.format("%.1f%%", validPercentage));
        if (validPercentage >= 90) {
            percentageLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else if (validPercentage >= 70) {
            percentageLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        } else {
            percentageLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        }
        statsGrid.add(percentageLabel, 1, 3);
        
        content.getChildren().add(statsGrid);
        
        // Affichage conditionnel selon les résultats
        if (analysis.getMissingMatricules().isEmpty()) {
            content.getChildren().add(new Separator());
            
            Label successLabel = new Label("🎉 Excellente nouvelle ! Tous les matricules du CSV existent dans identite_personnelle");
            successLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold; -fx-wrap-text: true;");
            content.getChildren().add(successLabel);
            
            Label recommendationLabel = new Label("➡️ Vous pouvez procéder à la mise à jour sans problème.");
            recommendationLabel.setStyle("-fx-text-fill: green; -fx-wrap-text: true;");
            content.getChildren().add(recommendationLabel);
            
        } else {
            content.getChildren().add(new Separator());
            
            Label warningLabel = new Label("⚠️ Matricules manquants détectés :");
            warningLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            content.getChildren().add(warningLabel);
            
            TextArea missingArea = new TextArea();
            missingArea.setEditable(false);
            missingArea.setPrefRowCount(8);
            missingArea.setWrapText(true);
            
            StringBuilder missingText = new StringBuilder();
            missingText.append("Les matricules suivants ne sont pas trouvés dans identite_personnelle:\n\n");
            
            int count = 0;
            for (String matricule : analysis.getMissingMatricules()) {
                if (count > 0 && count % 10 == 0) missingText.append("\n");
                missingText.append(matricule).append("  ");
                count++;
                if (count >= 100) {
                    missingText.append("\n\n... et ").append(analysis.getMissingMatricules().size() - 100).append(" autres");
                    break;
                }
            }
            missingArea.setText(missingText.toString());
            content.getChildren().add(missingArea);
            
            // Recommandations
            content.getChildren().add(new Separator());
            Label actionsLabel = new Label("💡 Options disponibles :");
            actionsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0066cc;");
            content.getChildren().add(actionsLabel);
            
            VBox actionsBox = new VBox(5);
            actionsBox.getChildren().addAll(
                new Label("✅ RECOMMANDÉ: Utiliser la validation automatique (les matricules invalides seront ignorés)"),
                new Label("🔧 ALTERNATIF: Corriger le fichier CSV pour supprimer les matricules inexistants"),
                new Label("➕ AVANCÉ: Ajouter d'abord les matricules manquants dans identite_personnelle")
            );
            
            // Styliser les options
            for (int i = 0; i < actionsBox.getChildren().size(); i++) {
                Label optionLabel = (Label) actionsBox.getChildren().get(i);
                optionLabel.setWrapText(true);
                optionLabel.setPrefWidth(500);
                if (i == 0) {
                    optionLabel.setStyle("-fx-text-fill: green;");
                } else {
                    optionLabel.setStyle("-fx-text-fill: #666666;");
                }
            }
            
            content.getChildren().add(actionsBox);
        }
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(Math.min(600, content.getPrefHeight() + 50));
        
        alert.getDialogPane().setContent(scrollPane);
        alert.getDialogPane().setPrefWidth(650);
        
        alert.showAndWait();
    }
    
    /**
     * Affiche les résultats de l'analyse des matricules
     */
    private void showAnalysisDialog(CSVProcessor.AnalysisResult analysis) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Analyse des matricules");
        alert.setHeaderText("Résultats de l'analyse de compatibilité");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Statistiques générales
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(15);
        statsGrid.setVgap(8);
        
        statsGrid.add(new Label("Matricules dans le CSV:"), 0, 0);
        statsGrid.add(new Label(String.valueOf(analysis.getCsvMatricules().size())), 1, 0);
        
        statsGrid.add(new Label("Matricules valides en DB:"), 0, 1);
        statsGrid.add(new Label(String.valueOf(analysis.getValidMatricules().size())), 1, 1);
        
        statsGrid.add(new Label("Matricules manquants:"), 0, 2);
        Label missingCountLabel = new Label(String.valueOf(analysis.getMissingMatricules().size()));
        if (analysis.getMissingMatricules().size() > 0) {
            missingCountLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        } else {
            missingCountLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        }
        statsGrid.add(missingCountLabel, 1, 2);
        
        content.getChildren().add(statsGrid);
        
        // Si il y a des matricules manquants, les afficher
        if (!analysis.getMissingMatricules().isEmpty()) {
            content.getChildren().add(new Separator());
            
            Label warningLabel = new Label("⚠️ Matricules manquants dans identite_personnelle :");
            warningLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            content.getChildren().add(warningLabel);
            
            TextArea missingArea = new TextArea();
            missingArea.setEditable(false);
            missingArea.setPrefRowCount(8);
            missingArea.setWrapText(true);
            
            StringBuilder missingText = new StringBuilder();
            int count = 0;
            for (String matricule : analysis.getMissingMatricules()) {
                if (count > 0) missingText.append(", ");
                missingText.append(matricule);
                count++;
                if (count >= 50) { // Limiter l'affichage
                    missingText.append("\n... et ").append(analysis.getMissingMatricules().size() - 50).append(" autres");
                    break;
                }
            }
            missingArea.setText(missingText.toString());
            content.getChildren().add(missingArea);
            
            // Options d'action
            content.getChildren().add(new Separator());
            Label actionsLabel = new Label("Actions possibles:");
            actionsLabel.setStyle("-fx-font-weight: bold;");
            content.getChildren().add(actionsLabel);
            
            VBox actionsBox = new VBox(5);
            actionsBox.getChildren().addAll(
                new Label("1. Nettoyer le fichier CSV pour supprimer les matricules inexistants"),
                new Label("2. Ajouter d'abord les matricules manquants dans identite_personnelle"),
                new Label("3. Utiliser l'option 'Ignorer matricules invalides' lors de la mise à jour")
            );
            content.getChildren().add(actionsBox);
        } else {
            Label successLabel = new Label("✅ Tous les matricules du CSV existent dans identite_personnelle");
            successLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            content.getChildren().add(successLabel);
        }
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(500);
        
        alert.getDialogPane().setContent(scrollPane);
        alert.getDialogPane().setPrefWidth(600);
        
        alert.showAndWait();
    }
    
    /**
     * MODIFICATION de la méthode startUpdate pour utiliser la nouvelle validation
     */
    @FXML
    private void startUpdateWithFK() {
        if (lastValidation == null || !lastValidation.isValid()) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Validation requise", 
                      "Veuillez d'abord valider le schéma du fichier");
            return;
        }
        
        String selectedTable = getSelectedTableName();
        String filePath = filePathField.getText();
        File csvFile = new File(filePath);
        
        // NOUVEAU : Demander confirmation pour les tables avec clés étrangères
        if (hasMatriculeForeignKey(selectedTable)) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirmation - Table avec clés étrangères");
            confirmAlert.setHeaderText("Cette table contient des contraintes de clés étrangères");
            confirmAlert.setContentText(
                "La table '" + selectedTable + "' a des contraintes sur les matricules.\n\n" +
                "Options disponibles:\n" +
                "• Continuer : Les matricules invalides seront ignorés\n" +
                "• Analyser d'abord : Voir quels matricules posent problème\n" +
                "• Annuler : Arrêter la mise à jour\n\n" +
                "Voulez-vous continuer la mise à jour ?"
            );
            
            ButtonType analyzeButton = new ButtonType("Analyser d'abord");
            ButtonType continueButton = new ButtonType("Continuer");
            ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
            
            confirmAlert.getButtonTypes().setAll(analyzeButton, continueButton, cancelButton);
            
            Optional<ButtonType> result = confirmAlert.showAndWait();
            
            if (result.isPresent()) {
                if (result.get() == analyzeButton) {
                    analyzerMatricules();
                    return;
                } else if (result.get() == cancelButton) {
                    return;
                }
                // Si "Continuer" est sélectionné, on continue avec la mise à jour
            } else {
                return; // Dialog fermé
            }
        }
        
        // Préparer l'interface
        updateProgress.setVisible(true);
        updateProgress.setProgress(0);
        statusLabel.setText("Mise à jour en cours avec validation des clés étrangères...");
        setControlsDisabled(true);
        
        // Démarrer la mise à jour dans un thread séparé
        Thread updateThread = new Thread(() -> {
            try {
                // NOUVEAU : Utiliser la version avec validation des clés étrangères
                CSVProcessor.EnhancedUpdateResult result = 
                    CSVProcessor.processEnhancedCSVUpdateSecureWithValidation(csvFile, selectedTable, 
                        progress -> Platform.runLater(() -> updateProgress.setProgress(progress)));
                
                // Enregistrer le résultat
                String status = result.hasErrors() ? "Partiel" : "Succès";
                String description = createUpdateDescription(result);
                
                int updateId = EnhancedDatabaseManager.logEnhancedUpdate(
                    selectedTable,
                    status,
                    result.getRecordsInserted(),
                    result.getRecordsUpdated(),
                    result.getRecordsUnchanged(),
                    currentService
                );
                
                // Mettre à jour l'interface
                Platform.runLater(() -> {
                    updateProgress.setVisible(false);
                    
                    if (result.hasErrors() || result.hasWarnings()) {
                        showUpdateResultDialog(result);
                        statusLabel.setText("Mise à jour terminée avec des avertissements");
                    } else if (!result.hasChanges()) {
                        showAlert(Alert.AlertType.INFORMATION, "Aucune modification", 
                                  "Mise à jour terminée", 
                                  "Aucune modification détectée. Tous les enregistrements sont identiques.");
                        statusLabel.setText("Aucune modification nécessaire");
                    } else {
                        showAlert(Alert.AlertType.INFORMATION, "Succès", "Mise à jour terminée", 
                                  String.format("✅ Mise à jour effectuée avec succès!\n\n📊 Résumé:\n%d nouveaux enregistrements\n%d enregistrements modifiés\n%d enregistrements inchangés\n\nTotal traité: %d", 
                                  result.getRecordsInserted(), result.getRecordsUpdated(), result.getRecordsUnchanged(), result.getTotalRecords()));
                        statusLabel.setText("Mise à jour terminée avec succès!");
                    }
                    
                    loadUpdateHistory();
                    updateTableInfo();
                    setControlsDisabled(false);
                    TableSchemaManager.clearCache();
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Erreur", "Échec de la mise à jour", 
                              "La mise à jour a échoué:\n\n" + e.getMessage() + 
                              "\n\nVérifiez le format de votre fichier CSV et les matricules, puis réessayez.");
                    updateProgress.setVisible(false);
                    statusLabel.setText("Échec de la mise à jour");
                    setControlsDisabled(false);
                    
                    EnhancedDatabaseManager.logEnhancedUpdate(
                        selectedTable,
                        "Échec",
                        0, 0, 0,
                        currentService
                    );
                    
                    loadUpdateHistory();
                });
            }
        });
        
        updateThread.setDaemon(true);
        updateThread.start();
    }
    
    /**
     * Vérifie si une table a des contraintes de clé étrangère sur matricule
     */
    private boolean hasMatriculeForeignKey(String tableName) {
        return Arrays.asList("grade_actuel", "formation_actuelle", "specialite", 
                            "parametres_corporels", "dotation_particuliere_config",
                            "infos_specifiques_general", "personnel_naviguant")
                     .contains(tableName.toLowerCase());
    }
    
    

    /**
     * Amélioration de la méthode de mise à jour pour utiliser REPLACE INTO
     */
    private void executeEnhancedUpdate(File csvFile, String tableName) {
        try {
            // Utiliser une approche REPLACE INTO pour éviter les conflits
            Connection conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);
            
            try {
                // Vider d'abord la table si c'est une mise à jour complète
                String truncateQuery = "DELETE FROM " + tableName;
                try (PreparedStatement stmt = conn.prepareStatement(truncateQuery)) {
                    stmt.executeUpdate();
                }
                
                // Puis insérer les nouvelles données
                CSVProcessor.EnhancedUpdateResult result = 
                    CSVProcessor.processEnhancedCSVUpdateSecureWithValidation(csvFile, tableName, 
                        progress -> Platform.runLater(() -> updateProgress.setProgress(progress)));
                
                conn.commit();
                
                // Afficher le résultat
                Platform.runLater(() -> {
                    updateProgress.setVisible(false);
                    
                    if (result.hasErrors()) {
                        showUpdateResultDialog(result);
                    } else {
                        showAlert(Alert.AlertType.INFORMATION, "Succès", "Mise à jour terminée", 
                                  String.format("✅ %d enregistrements traités avec succès!", 
                                              result.getTotalRecords()));
                    }
                    
                    loadUpdateHistory();
                    updateTableInfo();
                    setControlsDisabled(false);
                });
                
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (Exception e) {
            Platform.runLater(() -> {
                updateProgress.setVisible(false);
                showAlert(Alert.AlertType.ERROR, "Erreur", "Échec de la mise à jour", 
                          "La mise à jour a échoué: " + e.getMessage());
                setControlsDisabled(false);
            });
        }
    }

    /**
     * Correction pour les requêtes multi-tables
     */
    private void debugMultiTableQuery() {
        // Ajouter cette méthode pour débugger les requêtes multi-tables
        String currentService = UserSession.getCurrentService();
        List<String> tables = ServicePermissions.getTablesForService(currentService);
        
        LOGGER.info("=== DEBUG REQUÊTES MULTI-TABLES ===");
        LOGGER.info("Service actuel: " + currentService);
        LOGGER.info("Tables disponibles: " + String.join(", ", tables));
        
        // Vérifier la présence de la colonne matricule dans chaque table
        try (Connection conn = DatabaseManager.getConnection()) {
            for (String tableName : tables) {
                Set<String> columns = TableSchemaManager.getTableColumnNames(tableName);
                boolean hasMatricule = columns.contains("matricule");
                LOGGER.info("Table " + tableName + " - Matricule présent: " + hasMatricule + 
                           " - Colonnes: " + columns.size());
                
                if (!hasMatricule) {
                    LOGGER.warning("⚠️ Table " + tableName + " n'a pas de colonne 'matricule' pour les jointures");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du debug des tables", e);
        }
    }
    
    /**
     * NOUVELLE MÉTHODE : Affichage amélioré des résultats avec gestion des clés étrangères
     */
    private void showEnhancedUpdateResultDialog(CSVProcessor.EnhancedUpdateResult result, boolean hadForeignKeys) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        
        if (result.hasErrors()) {
            alert.setAlertType(Alert.AlertType.WARNING);
            alert.setTitle("Mise à jour terminée avec des avertissements");
            alert.setHeaderText("La mise à jour s'est terminée avec quelques problèmes");
        } else {
            alert.setTitle("Mise à jour terminée avec succès");
            alert.setHeaderText("La mise à jour s'est déroulée sans erreur");
        }
        
        // Créer un contenu personnalisé
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // Résumé des statistiques
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(15);
        statsGrid.setVgap(8);
        
        statsGrid.add(new Label("Nouveaux enregistrements:"), 0, 0);
        Label insertedLabel = new Label(String.valueOf(result.getRecordsInserted()));
        insertedLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        statsGrid.add(insertedLabel, 1, 0);
        
        statsGrid.add(new Label("Enregistrements modifiés:"), 0, 1);
        Label updatedLabel = new Label(String.valueOf(result.getRecordsUpdated()));
        updatedLabel.setStyle("-fx-text-fill: blue; -fx-font-weight: bold;");
        statsGrid.add(updatedLabel, 1, 1);
        
        statsGrid.add(new Label("Enregistrements inchangés:"), 0, 2);
        statsGrid.add(new Label(String.valueOf(result.getRecordsUnchanged())), 1, 2);
        
        statsGrid.add(new Label("Total traité:"), 0, 3);
        Label totalLabel = new Label(String.valueOf(result.getTotalRecords()));
        totalLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        statsGrid.add(totalLabel, 1, 3);
        
        content.getChildren().add(statsGrid);
        
        // Section des avertissements si il y en a
        if (result.hasWarnings()) {
            content.getChildren().add(new Separator());
            
            if (hadForeignKeys) {
                Label warningTitle = new Label("🔍 Validation des matricules :");
                warningTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #ff8c00;");
                content.getChildren().add(warningTitle);
            } else {
                Label warningTitle = new Label("⚠️ Avertissements :");
                warningTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #ff8c00;");
                content.getChildren().add(warningTitle);
            }
            
            TextArea warningsArea = new TextArea();
            warningsArea.setEditable(false);
            warningsArea.setWrapText(true);
            warningsArea.setPrefRowCount(8);
            warningsArea.setMaxHeight(200);
            
            StringBuilder warningsText = new StringBuilder();
            for (String warning : result.getWarnings()) {
                warningsText.append("• ").append(warning).append("\n");
            }
            warningsArea.setText(warningsText.toString());
            content.getChildren().add(warningsArea);
        }
        
        // Section des erreurs si il y en a
        if (result.hasErrors()) {
            content.getChildren().add(new Separator());
            
            Label errorTitle = new Label("❌ Erreurs :");
            errorTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: red;");
            content.getChildren().add(errorTitle);
            
            TextArea errorsArea = new TextArea();
            errorsArea.setEditable(false);
            errorsArea.setWrapText(true);
            errorsArea.setPrefRowCount(6);
            errorsArea.setMaxHeight(150);
            
            StringBuilder errorsText = new StringBuilder();
            for (String error : result.getErrors()) {
                errorsText.append("• ").append(error).append("\n");
            }
            errorsArea.setText(errorsText.toString());
            content.getChildren().add(errorsArea);
        }
        
        // Message de conclusion
        if (hadForeignKeys && result.hasWarnings() && !result.hasErrors()) {
            Label conclusionLabel = new Label("✅ Les matricules invalides ont été ignorés automatiquement. Aucune erreur générée.");
            conclusionLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold; -fx-wrap-text: true;");
            content.getChildren().add(conclusionLabel);
        }
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(Math.min(600, content.getPrefHeight() + 50));
        
        alert.getDialogPane().setContent(scrollPane);
        alert.getDialogPane().setPrefWidth(650);
        
        alert.showAndWait();
    }


    /**
     * Méthode pour tester une requête simple avant une requête complexe
     */
    private void testSimpleQuery(String tableName) {
        try (Connection conn = DatabaseManager.getConnection()) {
            String testQuery = "SELECT COUNT(*) FROM " + tableName + " LIMIT 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(testQuery)) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    LOGGER.info("Table " + tableName + " - Nombre d'enregistrements: " + count);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors du test de la table " + tableName, e);
        }
    }
    
    @FXML
    private void startUpdate() {
    if (lastValidation == null || !lastValidation.isValid()) {
        showAlert(Alert.AlertType.ERROR, "Erreur", "Validation requise", 
                  "Veuillez d'abord valider le schéma du fichier");
        return;
    }
    
    String selectedTable = getSelectedTableName();
    String filePath = filePathField.getText();
    File csvFile = new File(filePath);
    
    if (!confirmUpdate(selectedTable, csvFile.getName())) {
        return;
    }
    
    // Préparer l'interface
    updateProgress.setVisible(true);
    updateProgress.setProgress(0);
    statusLabel.setText("Mise à jour en cours...");
    setControlsDisabled(true);
    
    // Démarrer la mise à jour dans un thread séparé
    Thread updateThread = new Thread(() -> {
        try {
            // UTILISER SEULEMENT LA MÉTHODE DE BASE - SANS WithValidation
            CSVProcessor.EnhancedUpdateResult result = 
                CSVProcessor.processEnhancedCSVUpdateSecureWithValidation(csvFile, selectedTable, 
                    progress -> Platform.runLater(() -> updateProgress.setProgress(progress)));
            
            // Enregistrer le résultat
            String status = result.hasErrors() ? "Partiel" : "Succès";
            String description = createUpdateDescription(result);
            
            int updateId = EnhancedDatabaseManager.logEnhancedUpdate(
                selectedTable,
                status,
                result.getRecordsInserted(),
                result.getRecordsUpdated(),
                result.getRecordsUnchanged(),
                currentService
            );
            
            // Mettre à jour l'interface
            Platform.runLater(() -> {
                updateProgress.setVisible(false);
                
                if (result.hasErrors() || result.hasWarnings()) {
                    showUpdateResultDialog(result);
                    statusLabel.setText("Mise à jour terminée avec des avertissements");
                } else if (!result.hasChanges()) {
                    showAlert(Alert.AlertType.INFORMATION, "Aucune modification", 
                              "Mise à jour terminée", 
                              "Aucune modification détectée. Tous les enregistrements sont identiques.");
                    statusLabel.setText("Aucune modification nécessaire");
                } else {
                    showAlert(Alert.AlertType.INFORMATION, "Succès", "Mise à jour terminée", 
                              String.format("✅ Mise à jour effectuée avec succès!\n\n📊 Résumé:\n%d nouveaux enregistrements\n%d enregistrements modifiés\n%d enregistrements inchangés\n\nTotal traité: %d", 
                              result.getRecordsInserted(), result.getRecordsUpdated(), result.getRecordsUnchanged(), result.getTotalRecords()));
                    statusLabel.setText("Mise à jour terminée avec succès!");
                }
                
                loadUpdateHistory();
                updateTableInfo();
                setControlsDisabled(false);
                TableSchemaManager.clearCache();
            });
            
        } catch (Exception e) {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "Erreur", "Échec de la mise à jour", 
                          "La mise à jour a échoué:\n\n" + e.getMessage() + 
                          "\n\nVérifiez le format de votre fichier CSV et réessayez.");
                updateProgress.setVisible(false);
                statusLabel.setText("Échec de la mise à jour");
                setControlsDisabled(false);
                
                EnhancedDatabaseManager.logEnhancedUpdate(
                    selectedTable,
                    "Échec",
                    0, 0, 0,
                    currentService
                );
                
                loadUpdateHistory();
            });
        }
    });
    
    updateThread.setDaemon(true);
    updateThread.start();
}

    
    @FXML
    private void loadUpdateHistory() {
        try {
            Platform.runLater(() -> {
                try {
                    // Récupérer l'historique mis à jour
                    List<EnhancedUpdateRecord> enhancedUpdates = EnhancedDatabaseManager.getEnhancedUpdateHistory();
                    
                    // Convertir les EnhancedUpdateRecord en UpdateRecord
                    List<UpdateRecord> updates = new ArrayList<>();
                    for (EnhancedUpdateRecord enhanced : enhancedUpdates) {
                        UpdateRecord update = new UpdateRecord(
                            enhanced.getId(),
                            enhanced.getTableName(),
                            new Timestamp(enhanced.getUpdateDate().getTime()),
                            enhanced.getStatus(),
                            enhanced.getDescription(), // Gardé pour les détails
                            enhanced.getRecordsUpdated(),
                            enhanced.getRecordsInserted()
                        );
                        updates.add(update);
                    }
                    
                    // Limiter l'affichage aux dernières mises à jour
                    int maxItems = Math.min(updates.size(), ROWS_PER_PAGE);
                    List<UpdateRecord> limitedUpdates = updates.subList(0, maxItems);
                    
                    // Mettre à jour la table
                    updatesTable.setItems(FXCollections.observableArrayList(limitedUpdates));
                    updatesTable.refresh();
                    
                    // Afficher un message s'il y a plus d'éléments
                    if (updates.size() > ROWS_PER_PAGE) {
                        statusLabel.setText(String.format("Affichage des %d dernières mises à jour sur %d total", 
                                                         maxItems, updates.size()));
                    }
                    
                    LOGGER.info("Historique rechargé: " + limitedUpdates.size() + " enregistrements affichés sur " + updates.size());
                    
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Erreur lors du rechargement de l'historique", e);
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement de l'historique", e);
        }
    }
    
    private EnhancedUpdateRecord getLastUpdateForTable(String tableName) {
        return EnhancedDatabaseManager.getLastEnhancedUpdateForTable(tableName);
    }
    
    private void setControlsDisabled(boolean disabled) {
        tableComboBox.setDisable(disabled);
        filePathField.setDisable(disabled);
        validateSchemaButton.setDisable(disabled);
        startUpdateButton.setDisable(disabled);
        updatesTable.setDisable(disabled);
        if (generateTemplateButton != null) generateTemplateButton.setDisable(disabled);
        if (showSchemaButton != null) showSchemaButton.setDisable(disabled);
    }
    
    private boolean confirmUpdate(String tableName, String fileName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Confirmer la mise à jour");
        alert.setContentText("Vous êtes sur le point de mettre à jour la table '" + 
                            ServicePermissions.getTableDescription(tableName) + 
                            "' avec le fichier '" + fileName + "'.\n\n⚠️ Cette opération va modifier les données de la base.\n\nVoulez-vous continuer?");
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    private void showUpdateResultDialog(CSVProcessor.EnhancedUpdateResult result) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Résultat de la mise à jour");
        alert.setHeaderText("Mise à jour terminée avec des avertissements");
        
        TextArea resultArea = new TextArea(result.getDetailedSummary());
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setPrefHeight(200);
        
        VBox vbox = new VBox(10);
        vbox.getChildren().add(new Label("Résumé détaillé:"));
        vbox.getChildren().add(resultArea);
        
        alert.getDialogPane().setContent(vbox);
        alert.getDialogPane().setPrefWidth(600);
        alert.showAndWait();
    }
    
    private void showUpdateDetails(UpdateRecord updateRecord) {
        if (updateRecord.getDescription() != null && !updateRecord.getDescription().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Détails de la mise à jour");
            alert.setHeaderText("Mise à jour du " + dateFormat.format(updateRecord.getUpdateDate()));
            
            // Créer un contenu formaté
            VBox content = new VBox(10);
            
            // Informations principales
            GridPane infoGrid = new GridPane();
            infoGrid.setHgap(10);
            infoGrid.setVgap(5);
            
            infoGrid.add(new Label("Table:"), 0, 0);
            infoGrid.add(new Label(updateRecord.getTableName()), 1, 0);
            
            infoGrid.add(new Label("Statut:"), 0, 1);
            Label statusLabel = new Label(updateRecord.getStatus());
            switch (updateRecord.getStatus().toLowerCase()) {
                case "succès":
                    statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    break;
                case "échec":
                    statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    break;
                case "partiel":
                    statusLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
                    break;
            }
            infoGrid.add(statusLabel, 1, 1);
            
            infoGrid.add(new Label("Nouveaux:"), 0, 2);
            infoGrid.add(new Label(String.valueOf(updateRecord.getRecordsInserted())), 1, 2);
            
            infoGrid.add(new Label("Modifiés:"), 0, 3);
            infoGrid.add(new Label(String.valueOf(updateRecord.getRecordsUpdated())), 1, 3);
            
            content.getChildren().add(infoGrid);
            
            // Description détaillée
            Label descLabel = new Label("Description:");
            descLabel.setStyle("-fx-font-weight: bold;");
            content.getChildren().add(descLabel);
            
            TextArea textArea = new TextArea(updateRecord.getDescription());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefHeight(150);
            content.getChildren().add(textArea);
            
            alert.getDialogPane().setContent(content);
            alert.getDialogPane().setPrefWidth(500);
            alert.getDialogPane().setPrefHeight(400);
            
            alert.showAndWait();
        }
    }
    
    private String createUpdateDescription(CSVProcessor.EnhancedUpdateResult result) {
        StringBuilder description = new StringBuilder();
        
        description.append(String.format("Total: %d enregistrements traités\n", result.getTotalRecords()));
        description.append(String.format("- %d nouveaux\n", result.getRecordsInserted()));
        description.append(String.format("- %d modifiés\n", result.getRecordsUpdated()));
        description.append(String.format("- %d inchangés\n", result.getRecordsUnchanged()));
        
        if (result.hasWarnings()) {
            description.append("\nAvertissements:\n");
            for (String warning : result.getWarnings()) {
                description.append("- ").append(warning).append("\n");
            }
        }
        
        if (result.hasErrors()) {
            description.append("\nErreurs:\n");
            for (String error : result.getErrors()) {
                description.append("- ").append(error).append("\n");
            }
        }
        
        return description.toString();
    }
    
    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}