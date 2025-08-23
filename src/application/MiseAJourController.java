package application;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.Scene;

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
    
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private String currentService;
    private TableSchemaManager.SchemaValidationResult lastValidation;

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
        descriptionColumn.setCellValueFactory(cellData -> cellData.getValue().descriptionProperty());
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
                CSVProcessor.EnhancedUpdateResult result = 
                    CSVProcessor.processEnhancedCSVUpdate(csvFile, selectedTable, 
                        progress -> Platform.runLater(() -> updateProgress.setProgress(progress)));
                
                // Enregistrer le résultat
                String status = result.hasErrors() ? "Partiel" : "Succès";
                String description = createUpdateDescription(result);
                
                int updateId = EnhancedDatabaseManager.logEnhancedUpdate(
                    selectedTable,
                    status,
                    description,
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
                    
                    // CORRECTION : Forcer le rechargement de l'historique
                    loadUpdateHistory();
                    updateTableInfo();
                    setControlsDisabled(false);
                    
                    // Effacer le cache pour forcer la relecture du schéma
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
                    
                    // Enregistrer l'échec
                    EnhancedDatabaseManager.logEnhancedUpdate(
                        selectedTable,
                        "Échec",
                        "Erreur: " + e.getMessage(),
                        0, 0, 0,
                        currentService
                    );
                    
                    // CORRECTION : Recharger l'historique même en cas d'échec
                    loadUpdateHistory();
                });
            }
        });
        
        updateThread.setDaemon(true);
        updateThread.start();
    }
    
    // CORRECTION : Améliorer la méthode de chargement de l'historique
    private void loadUpdateHistory() {
        try {
            // Forcer un délai pour s'assurer que l'enregistrement est bien en base
            Platform.runLater(() -> {
                try {
                    // Récupérer l'historique mis à jour
                    List<EnhancedUpdateRecord> updates = EnhancedDatabaseManager.getEnhancedUpdateHistory();
                    
                    // Mettre à jour la table
                    updatesTable.setItems(FXCollections.observableArrayList(updates));
                    updatesTable.refresh();
                    
                    LOGGER.info("Historique rechargé: " + updates.size() + " enregistrements");
                    
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
    
    private void showUpdateDetails(UpdateRecord newSelection) {
        if (newSelection.getDescription() != null && !newSelection.getDescription().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Détails de la mise à jour");
            alert.setHeaderText("Mise à jour du " + dateFormat.format(newSelection.getUpdateDate()));
            
            TextArea textArea = new TextArea(newSelection.getDescription());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefHeight(150);
            
            alert.getDialogPane().setContent(textArea);
            alert.getDialogPane().setPrefWidth(500);
            
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