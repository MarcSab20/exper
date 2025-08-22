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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javafx.stage.Stage;

public class MiseAJourController {
    @FXML
    private Label lastUpdateLabel;
    @FXML
    private TableView<UpdateRecord> updatesTable;
    @FXML
    private TableColumn<UpdateRecord, String> tableColumn;
    @FXML
    private TableColumn<UpdateRecord, Date> dateColumn;
    @FXML
    private TableColumn<UpdateRecord, String> statusColumn;
    @FXML
    private TableColumn<UpdateRecord, String> descriptionColumn;
    @FXML
    private TableColumn<UpdateRecord, Integer> updatedColumn;
    @FXML
    private TableColumn<UpdateRecord, Integer> insertedColumn;
    @FXML
    private ComboBox<String> tableComboBox;
    @FXML
    private TextField filePathField;
    @FXML
    private ProgressBar updateProgress;
    @FXML
    private Label statusLabel;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private CSVProcessor.UpdateResult lastUpdateResult;

    @FXML
    public void initialize() {
        // Initialiser les colonnes de la table
        tableColumn.setCellValueFactory(new PropertyValueFactory<>("tableName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("updateDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        updatedColumn.setCellValueFactory(new PropertyValueFactory<>("recordsUpdated"));
        insertedColumn.setCellValueFactory(new PropertyValueFactory<>("recordsInserted"));
        
        // Formater la date dans la colonne
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
        
        // Initialiser les tables disponibles
        tableComboBox.getItems().addAll(
            "personnel",
            "formations"
        );
        tableComboBox.setValue("personnel"); // Valeur par défaut
        
        // Initialiser la barre de progression
        updateProgress.setVisible(false);
        updateProgress.setProgress(0);
        
        // Définir un écouteur pour le changement de table
        tableComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                updateLastUpdateLabel();
            }
        });
        
        // Charger l'historique des mises à jour
        loadUpdateHistory();
        
        // Mettre à jour le label de dernière mise à jour
        updateLastUpdateLabel();
        
        // Ajouter un écouteur pour la sélection d'un enregistrement dans la table
        updatesTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                if (newSelection != null && newSelection.hasErrors()) {
                    showDetails(newSelection);
                }
            }
        );
    }

    @FXML
    private void browseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers CSV", "*.csv")
        );
        fileChooser.setTitle("Sélectionner un fichier CSV");
        
        // Obtenir le parent pour centrer le dialogue
        Stage stage = (Stage) filePathField.getScene().getWindow();
        
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            filePathField.setText(file.getAbsolutePath());
            statusLabel.setText("Fichier sélectionné: " + file.getName());
        }
    }

    @FXML
    private void startUpdate() {
        String selectedTable = tableComboBox.getValue();
        String filePath = filePathField.getText();
        
        if (selectedTable == null || selectedTable.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Sélection requise", 
                      "Veuillez sélectionner une table");
            return;
        }
        
        if (filePath == null || filePath.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Fichier manquant", 
                      "Veuillez sélectionner un fichier CSV");
            return;
        }
        
        File csvFile = new File(filePath);
        if (!csvFile.exists() || !csvFile.isFile()) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Fichier invalide", 
                      "Le fichier sélectionné n'existe pas ou n'est pas valide");
            return;
        }
        
        // Vérifier que l'utilisateur souhaite procéder à la mise à jour
        if (!confirmUpdate(selectedTable, csvFile.getName())) {
            return;
        }
        
        // Déterminer la colonne clé primaire selon la table
        String primaryKeyColumn;
        switch (selectedTable.toLowerCase()) {
            case "personnel":
                primaryKeyColumn = "nom"; // Clé pour la table Personnel
                break;
            case "formations":
                primaryKeyColumn = "id_formation"; 
                break;
            case "stages":
                primaryKeyColumn = "id_stage"; 
                break;
            case "qualifications":
                primaryKeyColumn = "id_qualification"; 
                break;
            default:
                showAlert(Alert.AlertType.ERROR, "Erreur", "Table non supportée", 
                          "La table sélectionnée n'est pas prise en charge");
                return;
        }
        
        // Préparer l'interface pour la mise à jour
        updateProgress.setVisible(true);
        updateProgress.setProgress(0);
        statusLabel.setText("Validation du fichier...");
        
        // Désactiver les contrôles pendant la mise à jour
        setControlsDisabled(true);
        
        // Démarrer la mise à jour dans un thread séparé
        Thread updateThread = new Thread(() -> {
            try {
                // Valider d'abord le schéma du CSV
                if (!CSVProcessor.validateCSVSchema(csvFile, selectedTable)) {
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "Erreur", "Format incompatible", 
                                  "Le format du fichier CSV n'est pas compatible avec la structure de la table " + selectedTable);
                        setControlsDisabled(false);
                        updateProgress.setVisible(false);
                        statusLabel.setText("Échec de la validation du fichier");
                    });
                    return;
                }
                
                Platform.runLater(() -> statusLabel.setText("Mise à jour en cours..."));
                
                // Effectuer la mise à jour
                lastUpdateResult = CSVProcessor.processCSVUpdate(csvFile, selectedTable, primaryKeyColumn, 
                    progress -> Platform.runLater(() -> updateProgress.setProgress(progress)));
                
                // Enregistrer le résultat dans l'historique
                final String status = lastUpdateResult.hasErrors() ? "Partiel" : "Succès";
                final String description = createUpdateDescription(lastUpdateResult);
                
                int updateId = DatabaseManager.logUpdate(
                    selectedTable,
                    status,
                    description,
                    lastUpdateResult.getRecordsUpdated(),
                    lastUpdateResult.getRecordsInserted()
                );
                
                // Mettre également à jour l'historique des modifications
                String detailsModification = String.format(
                    "Mise à jour CSV #%d: %d enregistrements mis à jour, %d ajoutés",
                    updateId,
                    lastUpdateResult.getRecordsUpdated(),
                    lastUpdateResult.getRecordsInserted()
                );
                
                DatabaseManager.logModification(
                    selectedTable,
                    "Mise à jour CSV",
                    getUserName(),
                    detailsModification
                );
                
                // Enregistrer la connexion de l'utilisateur
                DatabaseManager.logConnexion(
                    getUserName(),
                    "Mise à jour CSV de " + selectedTable,
                    status
                );
                
                // Mettre à jour l'interface utilisateur
                Platform.runLater(() -> {
                    updateProgress.setVisible(false);
                    
                    if (lastUpdateResult.hasErrors()) {
                        showUpdateResultDialog(lastUpdateResult);
                        statusLabel.setText("Mise à jour terminée avec des avertissements");
                    } else {
                        showAlert(Alert.AlertType.INFORMATION, "Succès", "Mise à jour terminée", 
                                  String.format("La mise à jour a été effectuée avec succès.\n%d enregistrements mis à jour\n%d enregistrements ajoutés", 
                                  lastUpdateResult.getRecordsUpdated(), lastUpdateResult.getRecordsInserted()));
                        statusLabel.setText("Mise à jour terminée avec succès!");
                    }
                    
                    loadUpdateHistory();
                    updateLastUpdateLabel();
                    setControlsDisabled(false);
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Erreur", "Échec de la mise à jour", 
                              "La mise à jour a échoué: " + e.getMessage());
                    updateProgress.setVisible(false);
                    statusLabel.setText("Échec de la mise à jour");
                    setControlsDisabled(false);
                    
                    // Enregistrer l'échec dans l'historique
                    DatabaseManager.logUpdate(
                        selectedTable,
                        "Échec",
                        "Erreur: " + e.getMessage(),
                        0,
                        0
                    );
                    
                    // Journaliser l'erreur
                    DatabaseManager.logConnexion(
                        getUserName(),
                        "Tentative de mise à jour CSV de " + selectedTable,
                        "Échec: " + e.getMessage()
                    );
                    
                    loadUpdateHistory();
                });
            }
        });
        
        updateThread.setDaemon(true);
        updateThread.start();
    }

    private void loadUpdateHistory() {
        List<UpdateRecord> updates = DatabaseManager.getUpdateHistory();
        updatesTable.setItems(FXCollections.observableArrayList(updates));
    }

    private void updateLastUpdateLabel() {
        String selectedTable = tableComboBox.getValue();
        if (selectedTable != null && !selectedTable.isEmpty()) {
            UpdateRecord lastUpdate = DatabaseManager.getLastUpdateForTable(selectedTable);
            
            if (lastUpdate != null) {
                lastUpdateLabel.setText(dateFormat.format(lastUpdate.getUpdateDate()) + 
                                        " - Statut: " + lastUpdate.getStatus());
            } else {
                lastUpdateLabel.setText("Aucune mise à jour effectuée pour cette table");
            }
        }
    }
    
    private void setControlsDisabled(boolean disabled) {
        tableComboBox.setDisable(disabled);
        filePathField.setDisable(disabled);
        updatesTable.setDisable(disabled);
    }
    
    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private boolean confirmUpdate(String tableName, String fileName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Confirmer la mise à jour");
        alert.setContentText("Vous êtes sur le point de mettre à jour la table '" + tableName + 
                            "' avec le fichier '" + fileName + "'.\n\nVoulez-vous continuer?");
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    private void showUpdateResultDialog(CSVProcessor.UpdateResult result) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Résultat de la mise à jour");
        alert.setHeaderText("Mise à jour terminée avec des avertissements");
        
        TextArea textArea = new TextArea(result.getErrors());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefHeight(200);
        
        // Créer une présentation du résultat
        String summary = String.format(
            "Mise à jour terminée avec des avertissements.\n\n" +
            "Enregistrements mis à jour: %d\n" +
            "Enregistrements ajoutés: %d\n" +
            "Total: %d\n\n" +
            "Détails des erreurs:",
            result.getRecordsUpdated(), result.getRecordsInserted(), result.getTotalRecords()
        );
        
        VBox vbox = new VBox();
        vbox.setSpacing(10);
        Label label = new Label(summary);
        vbox.getChildren().addAll(label, textArea);
        
        alert.getDialogPane().setContent(vbox);
        alert.getDialogPane().setPrefWidth(550);
        
        alert.showAndWait();
    }
    
    private void showDetails(UpdateRecord record) {
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
    
    private String createUpdateDescription(CSVProcessor.UpdateResult result) {
        StringBuilder description = new StringBuilder();
        
        description.append(String.format("Total: %d enregistrements traités (%d mis à jour, %d ajoutés)",
                           result.getTotalRecords(), result.getRecordsUpdated(), result.getRecordsInserted()));
        
        if (result.hasErrors()) {
            description.append("\n\nAvertissements:\n").append(result.getErrors());
        }
        
        return description.toString();
    }
    
    private String getUserName() {
        // Si vous avez une classe UserSession avec un mécanisme d'authentification
        try {
            return UserSession.getCurrentUser();
        } catch (Exception e) {
            // Fallback si la méthode n'est pas disponible ou en cas d'erreur
            return System.getProperty("user.name", "utilisateur");
        }
    }
}