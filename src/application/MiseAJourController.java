package application;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
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
import javafx.stage.Stage;

public class MiseAJourController {
    private static final Logger LOGGER = Logger.getLogger(MiseAJourController.class.getName());
    
    @FXML private ComboBox<String> tableComboBox;
    @FXML private Label lastUpdateLabel;
    @FXML private Label tableInfoLabel;
    @FXML private TableView<EnhancedUpdateRecord> updatesTable;
    @FXML private TableColumn<EnhancedUpdateRecord, String> tableColumn;
    @FXML private TableColumn<EnhancedUpdateRecord, Date> dateColumn;
    @FXML private TableColumn<EnhancedUpdateRecord, String> statusColumn;
    @FXML private TableColumn<EnhancedUpdateRecord, String> descriptionColumn;
    @FXML private TableColumn<EnhancedUpdateRecord, Integer> insertedColumn;
    @FXML private TableColumn<EnhancedUpdateRecord, Integer> updatedColumn;
    @FXML private TableColumn<EnhancedUpdateRecord, Integer> unchangedColumn;
    @FXML private TextField filePathField;
    @FXML private ProgressBar updateProgress;
    @FXML private Label statusLabel;
    @FXML private Button validateSchemaButton;
    @FXML private Button startUpdateButton;
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private String currentService;
    private CSVProcessor.ValidationResult lastValidation;

    @FXML
    public void initialize() {
        currentService = UserSession.getCurrentService(); // Supposons que cette méthode existe
        
        initializeColumns();
        initializeTableComboBox();
        setupEventListeners();
        loadUpdateHistory();
        updateProgress.setVisible(false);
        validateSchemaButton.setDisable(true);
        startUpdateButton.setDisable(true);
    }
    
    private void initializeColumns() {
        tableColumn.setCellValueFactory(new PropertyValueFactory<>("tableName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("updateDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        insertedColumn.setCellValueFactory(new PropertyValueFactory<>("recordsInserted"));
        updatedColumn.setCellValueFactory(new PropertyValueFactory<>("recordsUpdated"));
        unchangedColumn.setCellValueFactory(new PropertyValueFactory<>("recordsUnchanged"));
        
        // Formater la colonne date
        dateColumn.setCellFactory(column -> new TableCell<EnhancedUpdateRecord, Date>() {
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
        statusColumn.setCellFactory(column -> new TableCell<EnhancedUpdateRecord, String>() {
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
        }
    }
    
    private void setupEventListeners() {
        tableComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                updateTableInfo();
                validateSchemaButton.setDisable(filePathField.getText().isEmpty());
                startUpdateButton.setDisable(true);
            }
        });
        
        filePathField.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean hasFile = newValue != null && !newValue.trim().isEmpty();
            validateSchemaButton.setDisable(!hasFile || tableComboBox.getValue() == null);
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
            
            // Afficher des informations sur la table
            String tableInfo = String.format("Table: %s | Clé primaire: %s | Service: %s",
                selectedTable,
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
                          "Le fichier CSV est compatible avec la table " + selectedTable);
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
    
    private void showSchemaValidationError(CSVProcessor.ValidationResult validation) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Schéma incompatible");
        alert.setHeaderText("Le fichier CSV n'est pas compatible avec la table sélectionnée");
        
        VBox content = new VBox(10);
        
        if (!validation.getMissingColumns().isEmpty()) {
            Label missingLabel = new Label("Colonnes manquantes dans le CSV:");
            missingLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
            TextArea missingArea = new TextArea(String.join(", ", validation.getMissingColumns()));
            missingArea.setEditable(false);
            missingArea.setPrefRowCount(2);
            content.getChildren().addAll(missingLabel, missingArea);
        }
        
        if (!validation.getExtraColumns().isEmpty()) {
            Label extraLabel = new Label("Colonnes en trop dans le CSV:");
            extraLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: orange;");
            TextArea extraArea = new TextArea(String.join(", ", validation.getExtraColumns()));
            extraArea.setEditable(false);
            extraArea.setPrefRowCount(2);
            content.getChildren().addAll(extraLabel, extraArea);
        }
        
        Label expectedLabel = new Label("Colonnes attendues:");
        expectedLabel.setStyle("-fx-font-weight: bold;");
        TextArea expectedArea = new TextArea(String.join(", ", validation.getTableColumns()));
        expectedArea.setEditable(false);
        expectedArea.setPrefRowCount(3);
        content.getChildren().addAll(expectedLabel, expectedArea);
        
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(600);
        alert.showAndWait();
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
                    
                    if (result.hasErrors()) {
                        showUpdateResultDialog(result);
                        statusLabel.setText("Mise à jour terminée avec des avertissements");
                    } else if (!result.hasChanges()) {
                        showAlert(Alert.AlertType.INFORMATION, "Aucune modification", 
                                  "Mise à jour terminée", 
                                  "Aucune modification détectée. Tous les enregistrements sont identiques.");
                        statusLabel.setText("Aucune modification nécessaire");
                    } else {
                        showAlert(Alert.AlertType.INFORMATION, "Succès", "Mise à jour terminée", 
                                  String.format("Mise à jour effectuée avec succès.\n%d nouveaux enregistrements\n%d enregistrements modifiés\n%d enregistrements inchangés", 
                                  result.getRecordsInserted(), result.getRecordsUpdated(), result.getRecordsUnchanged()));
                        statusLabel.setText("Mise à jour terminée avec succès!");
                    }
                    
                    loadUpdateHistory();
                    updateTableInfo();
                    setControlsDisabled(false);
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Erreur", "Échec de la mise à jour", 
                              "La mise à jour a échoué: " + e.getMessage());
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
                    
                    loadUpdateHistory();
                });
            }
        });
        
        updateThread.setDaemon(true);
        updateThread.start();
    }
    
    private void loadUpdateHistory() {
        List<EnhancedUpdateRecord> updates = EnhancedDatabaseManager.getEnhancedUpdateHistory();
        updatesTable.setItems(FXCollections.observableArrayList(updates));
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
    }
    
    private boolean confirmUpdate(String tableName, String fileName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Confirmer la mise à jour");
        alert.setContentText("Vous êtes sur le point de mettre à jour la table '" + 
                            ServicePermissions.getTableDescription(tableName) + 
                            "' avec le fichier '" + fileName + "'.\n\nVoulez-vous continuer?");
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    private void showUpdateResultDialog(CSVProcessor.EnhancedUpdateResult result) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Résultat de la mise à jour");
        alert.setHeaderText("Mise à jour terminée avec des avertissements");
        
        String summary = String.format(
            "Nouveaux enregistrements: %d\n" +
            "Enregistrements modifiés: %d\n" +
            "Enregistrements inchangés: %d\n" +
            "Total traité: %d\n\n" +
            "Détails des erreurs:",
            result.getRecordsInserted(), 
            result.getRecordsUpdated(), 
            result.getRecordsUnchanged(),
            result.getTotalRecords()
        );
        
        VBox vbox = new VBox(10);
        Label summaryLabel = new Label(summary);
        TextArea errorArea = new TextArea(result.getErrors());
        errorArea.setEditable(false);
        errorArea.setWrapText(true);
        errorArea.setPrefHeight(150);
        
        vbox.getChildren().addAll(summaryLabel, errorArea);
        alert.getDialogPane().setContent(vbox);
        alert.getDialogPane().setPrefWidth(550);
        
        alert.showAndWait();
    }
    
    private void showUpdateDetails(EnhancedUpdateRecord record) {
        if (record.getDescription() != null && !record.getDescription().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Détails de la mise à jour");
            alert.setHeaderText("Mise à jour du " + dateFormat.format(record.getUpdateDate()));
            
            TextArea textArea = new TextArea(record.getDescription());
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
        
        if (result.hasErrors()) {
            description.append("\nAvertissements:\n").append(result.getErrors());
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