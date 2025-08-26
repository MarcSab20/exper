package application;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.fxml.Initializable;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.util.Callback;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Modality;
import javafx.scene.control.ButtonBar;
import javafx.event.ActionEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ResourceBundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Phrase;

/**
 * Contrôleur amélioré pour l'écran de gestion logistique
 * Nouvelles fonctionnalités:
 * - Gestion des mouvements de stock (approvisionnement/retrait)
 * - Fiche détaillée d'équipement
 * - Suppression sécurisée
 * - Historique complet des opérations
 */
public class AccueilLogController implements Initializable {
    // Logger pour le traçage des erreurs et informations
    private static final Logger LOGGER = Logger.getLogger(AccueilLogController.class.getName());
    
    // Constantes pour les statuts
    private static final String STATUT_VERT = "VERT";
    private static final String STATUT_ORANGE = "ORANGE";
    private static final String STATUT_ROUGE = "ROUGE";
    private static final String STATUT_VIOLET = "VIOLET";
    private static final String STATUT_BLEU = "BLEU";
    
    // Constantes pour les seuils de stock
    private static final double SEUIL_CRITIQUE = 1.0;
    private static final double SEUIL_ALERTE = 1.2;
    private static final double SEUIL_ATTENTION = 1.5;
    
    // Constantes pour les seuils de maintenance (en jours)
    private static final int MAINTENANCE_PROCHE = 7;
    private static final int MAINTENANCE_TRES_PROCHE = 3;
    
    // Format de date standard
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    // Éléments de l'interface définis dans le FXML
    @FXML private TableView<Stock> stocksTable;
    @FXML private TableColumn<Stock, String> statutStockColumn;
    @FXML private TableColumn<Stock, String> designationStockColumn;
    @FXML private TableColumn<Stock, Integer> quantiteColumn;
    @FXML private TableColumn<Stock, String> etatColumn;
    @FXML private TableColumn<Stock, Integer> valeurCritiqueColumn;
    @FXML private TableColumn<Stock, String> dateCreationColumn; // Nouvelle colonne
    
    // Boutons pour les stocks
    @FXML private Button btnAjouterStock;
    @FXML private Button btnMajStocks;
    @FXML private Button btnValiderStocks;
    @FXML private Button btnApprovisionnement; // Nouveau
    @FXML private Button btnRetrait; // Nouveau
    @FXML private Button btnFicheEquipement; // Nouveau
    
    @FXML private TableView<Maintenance> maintenanceTable;
    @FXML private TableColumn<Maintenance, String> statutMaintenanceColumn;
    @FXML private TableColumn<Maintenance, String> dateMaintenanceColumn;
    @FXML private TableColumn<Maintenance, String> designationMaintenanceColumn;
    @FXML private TableColumn<Maintenance, String> typeMaintenanceColumn;
    @FXML private TableColumn<Maintenance, String> descriptionMaintenanceColumn;
    @FXML private TableColumn<Maintenance, Boolean> effectueeColumn;
    
    // Boutons pour les maintenances
    @FXML private Button btnAjouterMaintenance;
    @FXML private Button btnMajMaintenance;
    @FXML private Button btnValiderMaintenance;
    @FXML private Button btnSupprimerMaintenance; 
    @FXML private Button btnSupprimerStock;
    
    @FXML private Label stocksCritiquesCount;
    @FXML private Label maintenancesUrgentesCount;
    @FXML private Label totalEquipementsCount;
    
    // Collections de données
    private final ObservableList<Stock> stocks = FXCollections.observableArrayList();
    private final ObservableList<Maintenance> maintenances = FXCollections.observableArrayList();
    
    // Connexion à la base de données
    private Connection connection;
    
    // États d'édition
    private boolean editModeStocks = false;
    private boolean editModeMaintenance = false;

    /**
     * Initialise le contrôleur
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            // Établir la connexion à la base de données
            connectToDatabase();
            
            // Initialiser les tables nécessaires
            StockMovementManager.initializeTables(connection);
            
            // Configurer les tableaux et charger les données
            initializeStocksTable();
            initializeMaintenanceTable();
            loadDataFromDatabase();
            
            // Initialiser les boutons
            setupButtons();
            
            // Mettre à jour les statuts des maintenances
            updateAllMaintenanceStatuses();
            
            // Rafraîchir les tables
            refreshTables();
            
            // Mettre à jour les indicateurs de statut
            updateStatusIndicators();
            
            // Vérifier les alertes critiques au démarrage
            checkCriticalAlerts();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation", e);
            showAlert(Alert.AlertType.ERROR, "Erreur d'initialisation", 
                     "Une erreur est survenue lors de l'initialisation de l'application", 
                     e.getMessage());
        }
    }
    
    /**
     * Établit la connexion à la base de données
     */
    private void connectToDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/master", "marco", "29Papa278.");
            LOGGER.info("Connexion à la base de données établie avec succès");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur de connexion à la base de données", e);
            showAlert(Alert.AlertType.ERROR, "Erreur de connexion", 
                     "Impossible de se connecter à la base de données", 
                     e.getMessage());
        }
    }

    /**
     * Configure les boutons de l'interface
     */
    private void setupButtons() {
        // Initialisation des boutons
        btnValiderStocks.setVisible(false);
        btnValiderMaintenance.setVisible(false);
        
        // Configuration des boutons d'ajout
        btnAjouterStock.setOnAction(e -> showStockEditDialog(null));
        btnAjouterMaintenance.setOnAction(e -> showMaintenanceEditDialog(null));
        
        // Configuration des boutons de mise à jour
        btnMajStocks.setOnAction(e -> toggleEditModeStocks());
        btnMajMaintenance.setOnAction(e -> toggleEditModeMaintenance());
        
        // Configuration des boutons de validation
        btnValiderStocks.setOnAction(e -> saveStocksToDatabase());
        btnValiderMaintenance.setOnAction(e -> saveMaintenanceToDatabase());
        
        // NOUVEAUX BOUTONS
        btnApprovisionnement.setOnAction(e -> effectuerMouvement("APPROVISIONNEMENT"));
        btnRetrait.setOnAction(e -> effectuerMouvement("RETRAIT"));
        btnFicheEquipement.setOnAction(e -> afficherFicheEquipement());
        btnSupprimerMaintenance.setOnAction(e -> supprimerMaintenanceSelectionnee());
        btnSupprimerStock.setOnAction(e -> supprimerStockSelectionne());
        btnSupprimerStock.setDisable(true);
        
        // Désactiver les nouveaux boutons par défaut
        btnApprovisionnement.setDisable(true);
        btnRetrait.setDisable(true);
        btnFicheEquipement.setDisable(true);
        btnAjouterStock.setDisable(true);
        btnAjouterMaintenance.setDisable(true);
    }
    
    /**
     * NOUVELLE FONCTIONNALITÉ: Effectue un mouvement de stock (approvisionnement ou retrait)
     */
    private void effectuerMouvement(String typeMouvement) {
        Stock stockSelectionne = stocksTable.getSelectionModel().getSelectedItem();
        if (stockSelectionne == null) {
            showAlert(Alert.AlertType.WARNING, "Sélection requise", 
                     "Veuillez sélectionner un équipement", null);
            return;
        }
        
        showMovementDialog(stockSelectionne, typeMouvement);
    }
    
    /**
     * NOUVELLE FONCTIONNALITÉ: Affiche la boîte de dialogue pour un mouvement de stock
     */
    private void showMovementDialog(Stock stock, String typeMouvement) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(typeMouvement.equals("APPROVISIONNEMENT") ? "Approvisionnement" : "Retrait");
        dialog.setHeaderText("Mouvement de stock pour : " + stock.getDesignation());
        
        // Configurer les boutons
        ButtonType executeButtonType = new ButtonType("Exécuter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(executeButtonType, ButtonType.CANCEL);
        
        // Créer les champs du formulaire
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        Label stockInfoLabel = new Label(String.format("Stock actuel: %d unités | Seuil critique: %d", 
                                                      stock.getQuantite(), stock.getValeurCritique()));
        stockInfoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        Spinner<Integer> quantiteSpinner = new Spinner<>(1, 
                                                        typeMouvement.equals("RETRAIT") ? stock.getQuantite() : 9999, 
                                                        1);
        quantiteSpinner.setEditable(true);
        quantiteSpinner.setPrefWidth(120);
        
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Description du mouvement...");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setPrefWidth(300);
        
        // Ajouter une vérification en temps réel pour les retraits
        if (typeMouvement.equals("RETRAIT")) {
            Label warningLabel = new Label("");
            warningLabel.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");
            
            quantiteSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal > stock.getQuantite()) {
                    warningLabel.setText("⚠️ Quantité insuffisante en stock!");
                    dialog.getDialogPane().lookupButton(executeButtonType).setDisable(true);
                } else {
                    int nouvelleQuantite = stock.getQuantite() - newVal;
                    if (nouvelleQuantite < stock.getValeurCritique()) {
                        warningLabel.setText("⚠️ Cette opération passera le stock sous le seuil critique!");
                        warningLabel.setStyle("-fx-text-fill: orange; -fx-font-size: 12px;");
                    } else {
                        warningLabel.setText("");
                    }
                    dialog.getDialogPane().lookupButton(executeButtonType).setDisable(false);
                }
            });
            
            grid.add(warningLabel, 1, 3);
        }
        
        grid.add(stockInfoLabel, 0, 0, 2, 1);
        grid.add(new Label("Quantité:"), 0, 1);
        grid.add(quantiteSpinner, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(descriptionArea, 1, 2);
        
        dialog.getDialogPane().setContent(grid);
        
        // Conversion du résultat
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == executeButtonType) {
                int quantite = quantiteSpinner.getValue();
                String description = descriptionArea.getText().trim();
                
                if (description.isEmpty()) {
                    showAlert(Alert.AlertType.ERROR, "Description requise", 
                             "Veuillez saisir une description pour ce mouvement", null);
                    return false;
                }
                
                // Exécuter le mouvement
                boolean success = StockMovementManager.effectuerMouvement(
                    connection, stock.getId(), typeMouvement, quantite, description);
                
                if (success) {
                    // Recharger les données
                    loadStocksFromDatabase();
                    stocksTable.refresh();
                    updateStatusIndicators();
                    
                    showAlert(Alert.AlertType.INFORMATION, "Mouvement effectué", 
                             String.format("Mouvement %s de %d unités effectué avec succès", 
                                          typeMouvement.toLowerCase(), quantite), null);
                    return true;
                } else {
                    showAlert(Alert.AlertType.ERROR, "Erreur", 
                             "Erreur lors de l'exécution du mouvement", null);
                    return false;
                }
            }
            return false;
        });
        
        dialog.showAndWait();
    }
    
    /**
     * NOUVELLE FONCTIONNALITÉ: Affiche la fiche détaillée d'un équipement
     */
    private void afficherFicheEquipement() {
        Stock stockSelectionne = stocksTable.getSelectionModel().getSelectedItem();
        if (stockSelectionne == null) {
            showAlert(Alert.AlertType.WARNING, "Sélection requise", 
                     "Veuillez sélectionner un équipement", null);
            return;
        }
        
        // Récupérer la fiche complète
        EquipmentCard fiche = StockMovementManager.getEquipmentCard(connection, stockSelectionne);
        
        showEquipmentCardDialog(fiche);
    }
    
    /**
     * NOUVELLE FONCTIONNALITÉ: Affiche la boîte de dialogue avec la fiche d'équipement
     */
    private void showEquipmentCardDialog(EquipmentCard fiche) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Fiche d'équipement");
        dialog.setHeaderText("Fiche complète : " + fiche.getStock().getDesignation());
        
        // Créer le contenu détaillé
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        // Informations générales
        Label infoGenerales = new Label("INFORMATIONS GÉNÉRALES");
        infoGenerales.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");
        
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(5);
        infoGrid.setPadding(new Insets(5, 0, 10, 10));
        
        infoGrid.add(new Label("Date de création:"), 0, 0);
        infoGrid.add(new Label(fiche.getFormattedDateCreation()), 1, 0);
        infoGrid.add(new Label("Quantité initiale:"), 0, 1);
        infoGrid.add(new Label(String.valueOf(fiche.getQuantiteInitiale())), 1, 1);
        infoGrid.add(new Label("Quantité actuelle:"), 0, 2);
        infoGrid.add(new Label(String.valueOf(fiche.getStock().getQuantite())), 1, 2);
        infoGrid.add(new Label("État:"), 0, 3);
        infoGrid.add(new Label(fiche.getStock().getEtat()), 1, 3);
        infoGrid.add(new Label("Seuil critique:"), 0, 4);
        infoGrid.add(new Label(String.valueOf(fiche.getStock().getValeurCritique())), 1, 4);
        
        // Style des labels
        for (int i = 0; i < 5; i++) {
            Label label = (Label) infoGrid.getChildren().get(i * 2);
            label.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057;");
        }
        
        // Résumé des mouvements
        Label resumeMouvements = new Label("RÉSUMÉ DES MOUVEMENTS");
        resumeMouvements.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");
        
        GridPane resumeGrid = new GridPane();
        resumeGrid.setHgap(10);
        resumeGrid.setVgap(5);
        resumeGrid.setPadding(new Insets(5, 0, 10, 10));
        
        resumeGrid.add(new Label("Nombre de mouvements:"), 0, 0);
        resumeGrid.add(new Label(String.valueOf(fiche.getNombreMouvements())), 1, 0);
        resumeGrid.add(new Label("Total approvisionnements:"), 0, 1);
        resumeGrid.add(new Label("+" + fiche.getTotalApprovisionements()), 1, 1);
        resumeGrid.add(new Label("Total retraits:"), 0, 2);
        resumeGrid.add(new Label("-" + fiche.getTotalRetraits()), 1, 2);
        resumeGrid.add(new Label("Quantité calculée:"), 0, 3);
        resumeGrid.add(new Label(String.valueOf(fiche.getQuantiteCalculee())), 1, 3);
        
        // Style des labels du résumé
        for (int i = 0; i < 4; i++) {
            Label label = (Label) resumeGrid.getChildren().get(i * 2);
            label.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057;");
        }
        
        // Avertissement si discordance
        if (fiche.hasDiscrepancy()) {
            Label warning = new Label("⚠️ ATTENTION: Discordance détectée entre quantité calculée et quantité enregistrée!");
            warning.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            content.getChildren().add(warning);
        }
        
        // Historique des mouvements
        if (!fiche.getMouvements().isEmpty()) {
            Label historiqueLabel = new Label("HISTORIQUE DES MOUVEMENTS (derniers 10)");
            historiqueLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #2c3e50;");
            
            VBox historiqueBox = new VBox(3);
            historiqueBox.setPadding(new Insets(5, 0, 0, 10));
            
            List<StockMovement> mouvements = fiche.getMouvements();
            int maxMouvements = Math.min(10, mouvements.size());
            
            for (int i = 0; i < maxMouvements; i++) {
                StockMovement mouvement = mouvements.get(i);
                Label mouvementLabel = new Label(String.format("%s - %s %s (%d → %d) - %s",
                    mouvement.getFormattedDate(),
                    mouvement.getTypeIcon(),
                    mouvement.getQuantiteWithSign(),
                    mouvement.getQuantiteAvant(),
                    mouvement.getQuantiteApres(),
                    mouvement.getDescription()));
                mouvementLabel.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
                historiqueBox.getChildren().add(mouvementLabel);
            }
            
            if (mouvements.size() > 10) {
                Label moreLabel = new Label("... et " + (mouvements.size() - 10) + " autres mouvements");
                moreLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #6c757d;");
                historiqueBox.getChildren().add(moreLabel);
            }
            
            content.getChildren().addAll(resumeMouvements, resumeGrid, historiqueLabel, historiqueBox);
        }
        
        content.getChildren().addAll(infoGenerales, infoGrid);
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setPrefWidth(500);
        
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setPrefWidth(550);
        
        // Bouton pour exporter la fiche
        ButtonType exportButton = new ButtonType("Exporter PDF", ButtonBar.ButtonData.LEFT);
        dialog.getButtonTypes().add(exportButton);
        
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == exportButton) {
            exporterFichePDF(fiche);
        }
    }
    
    /**
     * NOUVELLE FONCTIONNALITÉ: Exporte la fiche d'équipement en PDF
     */
    private void exporterFichePDF(EquipmentCard fiche) {
        try {
            Document document = new Document();
            String fileName = "Fiche_" + fiche.getStock().getDesignation().replaceAll("[^a-zA-Z0-9]", "_") + 
                             "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";
            File file = new File(fileName);
            PdfWriter.getInstance(document, new FileOutputStream(file));
            
            document.open();
            
            // Titre
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph("FICHE D'ÉQUIPEMENT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            
            Paragraph subtitle = new Paragraph(fiche.getStock().getDesignation(), titleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitle);
            
            document.add(new Paragraph(" "));
            
            // Contenu détaillé
            document.add(new Paragraph(fiche.generateSummary()));
            
            document.close();
            
            showAlert(Alert.AlertType.INFORMATION, "Export réussi", 
                     "Fiche d'équipement exportée avec succès", 
                     "Fichier: " + file.getAbsolutePath());
            
            HistoryManager.logCreation("Accueil Logistique", "Export fiche équipement: " + fiche.getStock().getDesignation());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'export de la fiche", e);
            showAlert(Alert.AlertType.ERROR, "Erreur d'export", 
                     "Impossible d'exporter la fiche", e.getMessage());
        }
    }
    
    /**
     * NOUVELLE FONCTIONNALITÉ: Supprime la maintenance sélectionnée
     */
    private void supprimerMaintenanceSelectionnee() {
        Maintenance maintenanceSelectionnee = maintenanceTable.getSelectionModel().getSelectedItem();
        if (maintenanceSelectionnee == null) {
            showAlert(Alert.AlertType.WARNING, "Sélection requise", 
                     "Veuillez sélectionner une maintenance à supprimer", null);
            return;
        }
        
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation de suppression");
        confirmation.setHeaderText("Supprimer une maintenance");
        confirmation.setContentText("Êtes-vous sûr de vouloir supprimer cette maintenance ?\n\n" +
                                   "Désignation: " + maintenanceSelectionnee.getDesignation() + "\n" +
                                   "Date: " + maintenanceSelectionnee.getDate() + "\n" +
                                   "Type: " + maintenanceSelectionnee.getType());
        
        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (supprimerMaintenanceFromDatabase(maintenanceSelectionnee)) {
                maintenances.remove(maintenanceSelectionnee);
                maintenanceTable.refresh();
                updateStatusIndicators();
                
                showAlert(Alert.AlertType.INFORMATION, "Suppression réussie", 
                         "La maintenance a été supprimée avec succès", null);
                         
                // Enregistrer dans l'historique
                String details = String.format("Suppression maintenance: %s (Date: %s, Type: %s)", 
                                              maintenanceSelectionnee.getDesignation(),
                                              maintenanceSelectionnee.getDate(),
                                              maintenanceSelectionnee.getType());
                HistoryManager.logDeletion("Maintenances", details);
            } else {
                showAlert(Alert.AlertType.ERROR, "Erreur de suppression", 
                         "Impossible de supprimer la maintenance", null);
            }
        }
    }
    
    /**
     * Supprime une maintenance de la base de données
     */
    private boolean supprimerMaintenanceFromDatabase(Maintenance maintenance) {
        String sql = "DELETE FROM maintenances WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, maintenance.getId());
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression de la maintenance", e);
            return false;
        }
    }
    
    /**
     * Rafraîchit les tables de l'interface
     */
    private void refreshTables() {
        stocksTable.refresh();
        maintenanceTable.refresh();
    }
    
    /**
     * Met à jour tous les statuts des maintenances
     */
    private void updateAllMaintenanceStatuses() {
        for (Maintenance maintenance : maintenances) {
            updateMaintenanceStatus(maintenance);
        }
    }

    /**
     * Configure le tableau des stocks avec la nouvelle colonne date de création
     */
    private void initializeStocksTable() {
        // Configuration des colonnes du tableau des stocks
        statutStockColumn.setCellValueFactory(new PropertyValueFactory<>("statut"));
        designationStockColumn.setCellValueFactory(new PropertyValueFactory<>("designation"));
        quantiteColumn.setCellValueFactory(new PropertyValueFactory<>("quantite"));
        etatColumn.setCellValueFactory(new PropertyValueFactory<>("etat"));
        valeurCritiqueColumn.setCellValueFactory(new PropertyValueFactory<>("valeurCritique"));
        // NOUVELLE COLONNE: Date de création
        dateCreationColumn.setCellValueFactory(cellData -> {
            Stock stock = cellData.getValue();
            LocalDateTime dateCreation = stock.getDateCreation();
            return new SimpleStringProperty(dateCreation != null ? 
                                          dateCreation.format(DATETIME_FORMATTER) : "N/A");
        });
        
        // Style pour la colonne statut
        configureStatutStockColumn();
        
        // Associer les données
        stocksTable.setItems(stocks);
        
        // Configuration du menu contextuel et des actions sur les lignes
        configureStockTableRowFactory();
        
        // NOUVELLE FONCTIONNALITÉ: Gérer la sélection pour activer/désactiver les boutons
        stocksTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = newSelection != null;
            btnApprovisionnement.setDisable(!hasSelection);
            btnRetrait.setDisable(!hasSelection);
            btnFicheEquipement.setDisable(!hasSelection);
            btnSupprimerStock.setDisable(!hasSelection || !editModeStocks); // NOUVEAU
        });
    }
    
    /**
     * Configure le style de la colonne statut pour les stocks
     */
    private void configureStatutStockColumn() {
        statutStockColumn.setCellFactory(column -> new TableCell<Stock, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText("●");
                    
                    // Application du style selon le statut
                    switch (item) {
                        case STATUT_VERT:
                            setStyle("-fx-text-fill: green; -fx-font-size: 16px;");
                            break;
                        case STATUT_ORANGE:
                            setStyle("-fx-text-fill: orange; -fx-font-size: 16px;");
                            break;
                        case STATUT_ROUGE:
                            setStyle("-fx-text-fill: red; -fx-font-size: 16px;");
                            break;
                        case STATUT_VIOLET:
                            setStyle("-fx-text-fill: purple; -fx-font-size: 16px;");
                            break;
                        default:
                            setStyle("");
                            break;
                    }
                }
            }
        });
    }
    
    /**
     * Configure le comportement des lignes dans le tableau des stocks
     */
    private void configureStockTableRowFactory() {
        stocksTable.setRowFactory(tv -> {
            TableRow<Stock> row = new TableRow<>();
            
            // Gestion du double-clic pour édition
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && editModeStocks && !row.isEmpty()) {
                    showStockEditDialog(row.getItem());
                }
            });
            
            // Menu contextuel AMÉLIORÉ
            ContextMenu contextMenu = createStockContextMenu(row);
            
            // N'afficher le menu contextuel qu'en mode édition ou pour certaines actions
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(
                    javafx.beans.binding.Bindings.createBooleanBinding(
                        () -> !row.isEmpty(), 
                        row.emptyProperty()
                    )
                )
                .then(contextMenu)
                .otherwise((ContextMenu)null)
            );
            
            return row;
        });
    }
    
    /**
     * Crée le menu contextuel AMÉLIORÉ pour une ligne du tableau des stocks
     */
    private ContextMenu createStockContextMenu(TableRow<Stock> row) {
        ContextMenu contextMenu = new ContextMenu();
        
        // Actions disponibles selon le mode
        if (editModeStocks) {
            MenuItem editItem = new MenuItem("Modifier");
            MenuItem deleteItem = new MenuItem("Supprimer");
            
            editItem.setOnAction(event -> {
                if (!row.isEmpty()) {
                    showStockEditDialog(row.getItem());
                }
            });
            
            deleteItem.setOnAction(event -> {
                if (!row.isEmpty()) {
                    supprimerStockAvecConfirmation(row.getItem());
                }
            });
            
            contextMenu.getItems().addAll(editItem, deleteItem);
        }
        
        // Actions toujours disponibles
        MenuItem ficheItem = new MenuItem("📄 Voir la fiche");
        MenuItem approvisionnerItem = new MenuItem("📦 Approvisionner");
        MenuItem retirerItem = new MenuItem("📤 Retirer");
        
        ficheItem.setOnAction(event -> {
            if (!row.isEmpty()) {
                stocksTable.getSelectionModel().select(row.getItem());
                afficherFicheEquipement();
            }
        });
        
        approvisionnerItem.setOnAction(event -> {
            if (!row.isEmpty()) {
                showMovementDialog(row.getItem(), "APPROVISIONNEMENT");
            }
        });
        
        retirerItem.setOnAction(event -> {
            if (!row.isEmpty()) {
                showMovementDialog(row.getItem(), "RETRAIT");
            }
        });
        
        if (editModeStocks) {
            contextMenu.getItems().add(new SeparatorMenuItem());
        }
        contextMenu.getItems().addAll(ficheItem, approvisionnerItem, retirerItem);
        
        return contextMenu;
    }
    
    /**
     * NOUVELLE FONCTIONNALITÉ: Supprime un stock avec confirmation et gestion complète
     */
    private void supprimerStockAvecConfirmation(Stock stock) {
        if (stock == null) return;
        
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation de suppression");
        confirmation.setHeaderText("Supprimer un équipement");
        confirmation.setContentText("Êtes-vous sûr de vouloir supprimer cet équipement ?\n\n" +
                                   "Désignation: " + stock.getDesignation() + "\n" +
                                   "Quantité actuelle: " + stock.getQuantite() + "\n" +
                                   "État: " + stock.getEtat() + "\n\n" +
                                   "⚠️ Cette action supprimera également tout l'historique des mouvements associé.");
        
        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean success = StockMovementManager.supprimerStock(connection, stock.getId(), stock.getDesignation());
            
            if (success) {
                stocks.remove(stock);
                stocksTable.refresh();
                updateStatusIndicators();
                
                showAlert(Alert.AlertType.INFORMATION, "Suppression réussie", 
                         "L'équipement et son historique ont été supprimés avec succès", null);
            } else {
                showAlert(Alert.AlertType.ERROR, "Erreur de suppression", 
                         "Impossible de supprimer l'équipement", null);
            }
        }
    }
    
    /**
     * Met à jour les indicateurs de statut rapide
     */
    private void updateStatusIndicators() {
        // Compter les stocks critiques et faibles
        int stocksCritiques = 0;
        for (Stock stock : stocks) {
            if (STATUT_VIOLET.equals(stock.getStatut()) || STATUT_ROUGE.equals(stock.getStatut())) {
                stocksCritiques++;
            }
        }
        
        // Compter les maintenances urgentes
        int maintenancesUrgentes = 0;
        for (Maintenance maintenance : maintenances) {
            if (!maintenance.isEffectuee() && 
                (STATUT_VIOLET.equals(maintenance.getStatut()) || STATUT_ROUGE.equals(maintenance.getStatut()))) {
                maintenancesUrgentes++;
            }
        }
        
        // Mettre à jour l'interface
        if (stocksCritiquesCount != null) {
            stocksCritiquesCount.setText(String.valueOf(stocksCritiques));
        }
        if (maintenancesUrgentesCount != null) {
            maintenancesUrgentesCount.setText(String.valueOf(maintenancesUrgentes));
        }
        if (totalEquipementsCount != null) {
            totalEquipementsCount.setText(String.valueOf(stocks.size()));
        }
    }

    /**
     * Configure le tableau des maintenances
     */
    private void initializeMaintenanceTable() {
        // Configuration des colonnes du tableau de maintenance
        statutMaintenanceColumn.setCellValueFactory(new PropertyValueFactory<>("statut"));
        dateMaintenanceColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        designationMaintenanceColumn.setCellValueFactory(new PropertyValueFactory<>("designation"));
        typeMaintenanceColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        descriptionMaintenanceColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        effectueeColumn.setCellValueFactory(new PropertyValueFactory<>("effectuee"));
        
        // Style pour la colonne statut
        configureStatutMaintenanceColumn();
        
        // Configuration de la colonne "Effectuée" avec des cases à cocher
        configureEffectueeColumn();
        
        // Associer les données
        maintenanceTable.setItems(maintenances);
        
        // Configuration du menu contextuel et des actions sur les lignes
        configureMaintenanceTableRowFactory();
    }
    
    /**
     * Configure le style de la colonne statut pour les maintenances
     */
    private void configureStatutMaintenanceColumn() {
        statutMaintenanceColumn.setCellFactory(column -> new TableCell<Maintenance, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText("●");
                    
                    // Application du style selon le statut
                    switch (item) {
                        case STATUT_VERT:
                            setStyle("-fx-text-fill: green; -fx-font-size: 16px;");
                            break;
                        case STATUT_ORANGE:
                            setStyle("-fx-text-fill: orange; -fx-font-size: 16px;");
                            break;
                        case STATUT_ROUGE:
                            setStyle("-fx-text-fill: red; -fx-font-size: 16px;");
                            break;
                        case STATUT_VIOLET:
                            setStyle("-fx-text-fill: purple; -fx-font-size: 16px;");
                            break;
                        case STATUT_BLEU:
                            setStyle("-fx-text-fill: blue; -fx-font-size: 16px;");
                            break;
                        default:
                            setStyle("");
                            break;
                    }
                }
            }
        });
    }
    
    /**
     * Configure la colonne "Effectuée" avec des cases à cocher
     */
    private void configureEffectueeColumn() {
        effectueeColumn.setCellFactory(column -> new CheckBoxTableCell<Maintenance, Boolean>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    CheckBox checkBox = new CheckBox();
                    checkBox.setSelected(item);
                    checkBox.setDisable(!editModeMaintenance);
                    checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                        Maintenance maintenance = getTableView().getItems().get(getIndex());
                        maintenance.setEffectuee(newVal);
                        if (newVal) {
                            maintenance.setStatut(STATUT_BLEU);
                        } else {
                            updateMaintenanceStatus(maintenance);
                        }
                        getTableView().refresh();
                        updateStatusIndicators();
                    });
                    setGraphic(checkBox);
                } else {
                    setGraphic(null);
                }
            }
        });
    }
    
    /**
     * Configure le comportement des lignes dans le tableau des maintenances
     */
    private void configureMaintenanceTableRowFactory() {
        maintenanceTable.setRowFactory(tv -> {
            TableRow<Maintenance> row = new TableRow<>();
            
            // Gestion du double-clic pour édition
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && editModeMaintenance && !row.isEmpty()) {
                    showMaintenanceEditDialog(row.getItem());
                }
            });
            
            // Menu contextuel
            ContextMenu contextMenu = createMaintenanceContextMenu(row);
            
            // N'afficher le menu contextuel qu'en mode édition
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(
                    javafx.beans.binding.Bindings.createBooleanBinding(
                        () -> editModeMaintenance && !row.isEmpty(), 
                        row.emptyProperty()
                    )
                )
                .then(contextMenu)
                .otherwise((ContextMenu)null)
            );
            
            return row;
        });
    }
    
    /**
     * Crée le menu contextuel pour une ligne du tableau des maintenances
     */
    private ContextMenu createMaintenanceContextMenu(TableRow<Maintenance> row) {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem editItem = new MenuItem("Modifier");
        MenuItem deleteItem = new MenuItem("Supprimer");
        
        editItem.setOnAction(event -> {
            if (!row.isEmpty() && editModeMaintenance) {
                showMaintenanceEditDialog(row.getItem());
            }
        });
        
        deleteItem.setOnAction(event -> {
            if (!row.isEmpty() && editModeMaintenance) {
                maintenanceTable.getSelectionModel().select(row.getItem());
                supprimerMaintenanceSelectionnee();
            }
        });
        
        contextMenu.getItems().addAll(editItem, deleteItem);
        
        return contextMenu;
    }
    
    /**
     * Vérifie et affiche les alertes critiques au démarrage
     */
    @FXML
    private void checkCriticalAlerts() {
        List<String> alertes = new ArrayList<>();
        
        // Vérifier les stocks critiques
        for (Stock stock : stocks) {
            if (STATUT_VIOLET.equals(stock.getStatut())) {
                alertes.add("STOCK CRITIQUE: " + stock.getDesignation() + 
                           " (Quantité: " + stock.getQuantite() + 
                           ", Seuil: " + stock.getValeurCritique() + ")");
            } else if (STATUT_ROUGE.equals(stock.getStatut())) {
                alertes.add("STOCK FAIBLE: " + stock.getDesignation() + 
                           " (Quantité: " + stock.getQuantite() + 
                           ", Seuil: " + stock.getValeurCritique() + ")");
            }
        }
        
        // Vérifier les maintenances urgentes
        for (Maintenance maintenance : maintenances) {
            if (STATUT_VIOLET.equals(maintenance.getStatut())) {
                alertes.add("MAINTENANCE DÉPASSÉE: " + maintenance.getDesignation() + 
                           " (Date prévue: " + maintenance.getDate() + ")");
            } else if (STATUT_ROUGE.equals(maintenance.getStatut())) {
                alertes.add("MAINTENANCE URGENTE: " + maintenance.getDesignation() + 
                           " (Date prévue: " + maintenance.getDate() + ")");
            }
        }
        
        // Afficher les alertes si nécessaire
        if (!alertes.isEmpty()) {
            showCriticalAlertsDialog(alertes);
        }
    }

    /**
     * Affiche un dialogue d'alertes critiques
     */
    private void showCriticalAlertsDialog(List<String> alertes) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Alertes Critiques");
        alert.setHeaderText("Attention ! Des éléments nécessitent votre attention immédiate :");
        
        // Créer le contenu avec toutes les alertes
        VBox content = new VBox(10);
        for (String alerte : alertes) {
            Label alerteLabel = new Label("⚠️ " + alerte);
            alerteLabel.setWrapText(true);
            
            // Couleur selon le type d'alerte
            if (alerte.contains("CRITIQUE") || alerte.contains("DÉPASSÉE")) {
                alerteLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            } else {
                alerteLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
            }
            
            content.getChildren().add(alerteLabel);
        }
        
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(200);
        
        alert.getDialogPane().setContent(scrollPane);
        alert.getDialogPane().setPrefWidth(600);
        
        alert.showAndWait();
    }
    
    /**
     * Charge les données depuis la base de données
     */
    private void loadDataFromDatabase() {
        loadStocksFromDatabase();
        loadMaintenanceFromDatabase();
        updateStatusIndicators();
    }
    
    /**
     * Charge les stocks depuis la base de données avec les nouvelles colonnes
     */
    private void loadStocksFromDatabase() {
        stocks.clear();
        
        String sql = """
            SELECT id, designation, quantite, etat, description, valeur_critique, statut,
                   date_creation, quantite_initiale
            FROM stocks""";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String designation = rs.getString("designation");
                    int quantite = rs.getInt("quantite");
                    String etat = rs.getString("etat");
                    String description = rs.getString("description");
                    int valeurCritique = rs.getInt("valeur_critique");
                    String statut = rs.getString("statut");
                    
                    // Nouvelles colonnes
                    Timestamp dateCreationTs = rs.getTimestamp("date_creation");
                    LocalDateTime dateCreation = dateCreationTs != null ? 
                                                dateCreationTs.toLocalDateTime() : LocalDateTime.now();
                    int quantiteInitiale = rs.getInt("quantite_initiale");
                    
                    Stock stock = new Stock(id, designation, quantite, etat, description, 
                                          valeurCritique, statut, dateCreation, quantiteInitiale);
                    stocks.add(stock);
                }
            }
            LOGGER.info("Chargement des stocks terminé: " + stocks.size() + " éléments");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des stocks", e);
            showAlert(Alert.AlertType.ERROR, "Erreur de base de données", 
                     "Impossible de charger les stocks", 
                     e.getMessage());
        }
    }
    
    private void supprimerStockSelectionne() {
        Stock stockSelectionne = stocksTable.getSelectionModel().getSelectedItem();
        if (stockSelectionne == null) {
            showAlert(Alert.AlertType.WARNING, "Sélection requise", 
                     "Veuillez sélectionner un équipement à supprimer", null);
            return;
        }
        
        supprimerStockAvecConfirmation(stockSelectionne);
    }

    
    /**
     * Charge les maintenances depuis la base de données
     */
    private void loadMaintenanceFromDatabase() {
        maintenances.clear();
        
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM maintenances")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String date = rs.getDate("date_maintenance").toLocalDate().format(DATE_FORMATTER);
                    String designation = rs.getString("designation");
                    String type = rs.getString("type_maintenance");
                    String description = rs.getString("description");
                    boolean effectuee = rs.getBoolean("effectuee");
                    String statut = rs.getString("statut");
                    
                    maintenances.add(new Maintenance(id, date, designation, type, description, effectuee, statut));
                }
            }
            LOGGER.info("Chargement des maintenances terminé: " + maintenances.size() + " éléments");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des maintenances", e);
            showAlert(Alert.AlertType.ERROR, "Erreur de base de données", 
                     "Impossible de charger les maintenances", 
                     e.getMessage());
        }
    }
    
    /**
     * Active/désactive le mode d'édition pour les stocks
     */
    private void toggleEditModeStocks() {
        editModeStocks = !editModeStocks;
        btnMajStocks.setText(editModeStocks ? "Terminer" : "Mettre à jour");
        btnValiderStocks.setVisible(editModeStocks);
        btnAjouterStock.setDisable(!editModeStocks);
        
        // Gérer le bouton supprimer selon la sélection et le mode édition
        Stock selectedStock = stocksTable.getSelectionModel().getSelectedItem();
        btnSupprimerStock.setDisable(!(editModeStocks && selectedStock != null));
        
        LOGGER.info("Mode édition stocks: " + (editModeStocks ? "activé" : "désactivé"));
    }
    
    /**
     * Active/désactive le mode d'édition pour les maintenances
     */
    private void toggleEditModeMaintenance() {
        editModeMaintenance = !editModeMaintenance;
        btnMajMaintenance.setText(editModeMaintenance ? "Terminer" : "Mettre à jour");
        btnValiderMaintenance.setVisible(editModeMaintenance);
        btnAjouterMaintenance.setDisable(!editModeMaintenance);
        
        // Rafraîchir le tableau pour mettre à jour l'état activé/désactivé des cases à cocher
        maintenanceTable.refresh();
        
        LOGGER.info("Mode édition maintenances: " + (editModeMaintenance ? "activé" : "désactivé"));
    }
    
    /**
     * Affiche une boîte de dialogue pour éditer ou ajouter un stock
     */
    private void showStockEditDialog(Stock stock) {
        boolean isNew = (stock == null);
        stock = isNew ? new Stock() : stock;
        
        // Créer une boîte de dialogue
        Dialog<Stock> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Ajouter un stock" : "Modifier un stock");
        dialog.setHeaderText(isNew ? "Ajouter un nouveau stock" : "Modifier les informations du stock");
        
        // Configurer les boutons
        ButtonType saveButtonType = new ButtonType("Valider", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        // Créer les champs du formulaire
        TextField designationField = new TextField(stock.getDesignation());
        designationField.setPromptText("Désignation");
        
        Spinner<Integer> quantiteSpinner = new Spinner<>(0, 99999, 
                                                        stock.getQuantite() > 0 ? stock.getQuantite() : 1);
        quantiteSpinner.setEditable(true);
        
        TextField etatField = new TextField(stock.getEtat());
        etatField.setPromptText("État");
        
        TextArea descriptionField = new TextArea(stock.getDescription());
        descriptionField.setPromptText("Description");
        
        Spinner<Integer> valeurCritiqueSpinner = new Spinner<>(1, 99999, 
                                                              stock.getValeurCritique() > 0 ? stock.getValeurCritique() : 10);
        valeurCritiqueSpinner.setEditable(true);
        
        // Créer la disposition en grille
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        grid.add(new Label("Désignation:"), 0, 0);
        grid.add(designationField, 1, 0);
        grid.add(new Label("Quantité:"), 0, 1);
        grid.add(quantiteSpinner, 1, 1);
        grid.add(new Label("État:"), 0, 2);
        grid.add(etatField, 1, 2);
        grid.add(new Label("Valeur critique:"), 0, 3);
        grid.add(valeurCritiqueSpinner, 1, 3);
        grid.add(new Label("Description:"), 0, 4);
        grid.add(descriptionField, 1, 4);
        
        // Définir les dimensions préférées du TextArea
        descriptionField.setPrefRowCount(4);
        descriptionField.setPrefWidth(300);
        
        dialog.getDialogPane().setContent(grid);
        final Stock existingStock = stock;
        
        // Conversion du résultat
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    // Valider les entrées
                    if (designationField.getText().trim().isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Erreur de saisie", 
                                "La désignation ne peut pas être vide", null);
                        return null;
                    }
                    
                    int quantite = quantiteSpinner.getValue();
                    int valeurCritique = valeurCritiqueSpinner.getValue();
                    
                    if (quantite < 0) {
                        showAlert(Alert.AlertType.ERROR, "Erreur de saisie", 
                                "La quantité ne peut pas être négative", null);
                        return null;
                    }
                    
                    if (valeurCritique <= 0) {
                        showAlert(Alert.AlertType.ERROR, "Erreur de saisie", 
                                "La valeur critique doit être supérieure à zéro", null);
                        return null;
                    }
                    
                    // Mettre à jour l'objet stock avec les nouvelles valeurs
                    Stock updatedStock = isNew ? new Stock() : new Stock(existingStock.getId());
                    updatedStock.setDesignation(designationField.getText().trim());
                    updatedStock.setQuantite(quantite);
                    updatedStock.setEtat(etatField.getText().trim());
                    updatedStock.setDescription(descriptionField.getText().trim());
                    updatedStock.setValeurCritique(valeurCritique);
                    
                    // Pour un nouvel équipement, définir la quantité initiale
                    if (isNew) {
                        updatedStock.setQuantiteInitiale(quantite);
                        updatedStock.setDateCreation(LocalDateTime.now());
                    } else {
                        updatedStock.setQuantiteInitiale(existingStock.getQuantiteInitiale());
                        updatedStock.setDateCreation(existingStock.getDateCreation());
                    }
                    
                    // Calculer le statut
                    updatedStock.setStatut(calculateStockStatus(quantite, valeurCritique));
                    
                    return updatedStock;
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Erreur de saisie", 
                            "Erreur dans les données saisies", e.getMessage());
                    return null;
                }
            }
            return null;
        });
        
        // Afficher le dialogue et traiter le résultat
        Optional<Stock> result = dialog.showAndWait();
        
        result.ifPresent(updatedStock -> {
            if (isNew) {
                stocks.add(updatedStock);
                LOGGER.info("Nouveau stock ajouté: " + updatedStock.getDesignation());
                HistoryManager.logCreation("Stocks", "Ajout équipement: " + updatedStock.getDesignation());
            } else {
                // Mettre à jour l'objet existant avec les nouvelles valeurs
                existingStock.setDesignation(updatedStock.getDesignation());
                existingStock.setQuantite(updatedStock.getQuantite());
                existingStock.setEtat(updatedStock.getEtat());
                existingStock.setDescription(updatedStock.getDescription());
                existingStock.setValeurCritique(updatedStock.getValeurCritique());
                existingStock.setStatut(updatedStock.getStatut());
                LOGGER.info("Stock modifié: " + updatedStock.getDesignation());
                HistoryManager.logUpdate("Stocks", "Modification équipement: " + updatedStock.getDesignation());
            }
            // Rafraîchir l'affichage
            stocksTable.refresh();
            updateStatusIndicators();
        });
    }
    
    /**
     * Calcule le statut d'un stock en fonction de sa quantité et de sa valeur critique
     */
    private String calculateStockStatus(int quantite, int valeurCritique) {
        double ratio = (double) quantite / valeurCritique;
        
        if (ratio < SEUIL_CRITIQUE) {
            return STATUT_VIOLET;
        } else if (ratio < SEUIL_ALERTE) {
            return STATUT_ROUGE;
        } else if (ratio < SEUIL_ATTENTION) {
            return STATUT_ORANGE;
        } else {
            return STATUT_VERT;
        }
    }
    
    /**
     * Affiche une boîte de dialogue pour éditer ou ajouter une maintenance
     */
    private void showMaintenanceEditDialog(Maintenance maintenance) {
        boolean isNew = (maintenance == null);
        maintenance = isNew ? new Maintenance() : maintenance;
        
        // Créer une boîte de dialogue
        Dialog<Maintenance> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Ajouter une maintenance" : "Modifier une maintenance");
        dialog.setHeaderText(isNew ? "Ajouter une nouvelle maintenance planifiée" : "Modifier les informations de maintenance");
        
        // Configurer les boutons
        ButtonType saveButtonType = new ButtonType("Valider", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        // Créer les champs du formulaire
        DatePicker datePicker = new DatePicker();
        if (!isNew && maintenance.getDate() != null && !maintenance.getDate().isEmpty()) {
            datePicker.setValue(LocalDate.parse(maintenance.getDate()));
        } else {
            // Par défaut, proposer la date du jour
            datePicker.setValue(LocalDate.now());
        }
        
        TextField designationField = new TextField(maintenance.getDesignation());
        designationField.setPromptText("Désignation");
        
        ComboBox<String> typeComboBox = new ComboBox<>();
        typeComboBox.getItems().addAll(
            "Préventive", 
            "Corrective", 
            "Prédictive", 
            "Conditionnelle", 
            "Systématique"
        );
        typeComboBox.setEditable(true);
        typeComboBox.setValue(maintenance.getType());
        typeComboBox.setPromptText("Type de maintenance");
        
        TextArea descriptionField = new TextArea(maintenance.getDescription());
        descriptionField.setPromptText("Description");
        
        CheckBox effectueeCheckBox = new CheckBox("Maintenance effectuée");
        effectueeCheckBox.setSelected(maintenance.isEffectuee());
        
        // Créer la disposition en grille
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        grid.add(new Label("Date:"), 0, 0);
        grid.add(datePicker, 1, 0);
        grid.add(new Label("Désignation:"), 0, 1);
        grid.add(designationField, 1, 1);
        grid.add(new Label("Type:"), 0, 2);
        grid.add(typeComboBox, 1, 2);
        grid.add(new Label("Description:"), 0, 3);
        grid.add(descriptionField, 1, 3);
        grid.add(effectueeCheckBox, 0, 4, 2, 1);
        
        // Définir les dimensions préférées du TextArea
        descriptionField.setPrefRowCount(4);
        descriptionField.setPrefWidth(300);
        
        dialog.getDialogPane().setContent(grid);
        
        final Maintenance existingMaintenance = maintenance;
        // Conversion du résultat
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                LocalDate date = datePicker.getValue();
                if (date == null) {
                    showAlert(Alert.AlertType.ERROR, "Erreur de date", 
                            "Veuillez sélectionner une date", null);
                    return null;
                }
                
                if (designationField.getText().trim().isEmpty()) {
                    showAlert(Alert.AlertType.ERROR, "Erreur de saisie", 
                            "La désignation ne peut pas être vide", null);
                    return null;
                }
                
                // Mettre à jour l'objet maintenance avec les nouvelles valeurs
                Maintenance updatedMaintenance = isNew ? new Maintenance() : new Maintenance(existingMaintenance.getId());
                updatedMaintenance.setDate(date.format(DATE_FORMATTER));
                updatedMaintenance.setDesignation(designationField.getText().trim());
                updatedMaintenance.setType(typeComboBox.getValue());
                updatedMaintenance.setDescription(descriptionField.getText().trim());
                updatedMaintenance.setEffectuee(effectueeCheckBox.isSelected());
                
                // Déterminer le statut
                if (effectueeCheckBox.isSelected()) {
                    updatedMaintenance.setStatut(STATUT_BLEU);
                } else {
                    updateMaintenanceStatus(updatedMaintenance);
                }
                
                return updatedMaintenance;
            }
            return null;
        });
        
        // Afficher le dialogue et traiter le résultat
        Optional<Maintenance> result = dialog.showAndWait();
        
        result.ifPresent(updatedMaintenance -> {
            if (isNew) {
                maintenances.add(updatedMaintenance);
                LOGGER.info("Nouvelle maintenance ajoutée: " + updatedMaintenance.getDesignation());
                HistoryManager.logCreation("Maintenances", "Ajout maintenance: " + updatedMaintenance.getDesignation());
            } else {
                // Mettre à jour l'objet existant avec les nouvelles valeurs
                existingMaintenance.setDate(updatedMaintenance.getDate());
                existingMaintenance.setDesignation(updatedMaintenance.getDesignation());
                existingMaintenance.setType(updatedMaintenance.getType());
                existingMaintenance.setDescription(updatedMaintenance.getDescription());
                existingMaintenance.setEffectuee(updatedMaintenance.isEffectuee());
                existingMaintenance.setStatut(updatedMaintenance.getStatut());
                LOGGER.info("Maintenance modifiée: " + updatedMaintenance.getDesignation());
                HistoryManager.logUpdate("Maintenances", "Modification maintenance: " + updatedMaintenance.getDesignation());
            }
            // Rafraîchir l'affichage
            maintenanceTable.refresh();
            updateStatusIndicators();
        });
    }
    
    /**
     * Met à jour le statut d'une maintenance en fonction de sa date et de son état
     */
    private void updateMaintenanceStatus(Maintenance maintenance) {
        if (maintenance.isEffectuee()) {
            maintenance.setStatut(STATUT_BLEU);
            return;
        }
        
        try {
            LocalDate today = LocalDate.now();
            LocalDate maintenanceDate = LocalDate.parse(maintenance.getDate());
            
            long daysUntilMaintenance = ChronoUnit.DAYS.between(today, maintenanceDate);
            
            if (daysUntilMaintenance < 0) {
                maintenance.setStatut(STATUT_VIOLET); // Dépassée
            } else if (daysUntilMaintenance <= MAINTENANCE_TRES_PROCHE) {
                maintenance.setStatut(STATUT_ROUGE); // Très proche
            } else if (daysUntilMaintenance <= MAINTENANCE_PROCHE) {
                maintenance.setStatut(STATUT_ORANGE); // Proche
            } else {
                maintenance.setStatut(STATUT_VERT); // Loin
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la mise à jour du statut de maintenance", e);
            maintenance.setStatut(STATUT_ROUGE); // Par défaut, en cas d'erreur
        }
    }
    
    /**
     * Enregistre les stocks dans la base de données avec les nouvelles colonnes
     */
    private void saveStocksToDatabase() {
        try {
            // Commencer une transaction
            connection.setAutoCommit(false);
            
            // Récupérer la liste des IDs existants pour gestion de la suppression
            deleteRemovedStocks();
            
            // Mettre à jour ou insérer les stocks
            updateOrInsertStocks();
            
            // Valider la transaction
            connection.commit();
            connection.setAutoCommit(true);
            
            showAlert(Alert.AlertType.INFORMATION, "Succès", 
                     "Les données des stocks ont été enregistrées avec succès", null);
            
            // Rafraîchir le tableau pour refléter les changements
            stocksTable.refresh();
            
            updateStatusIndicators();
            
            // Désactiver le mode édition
            toggleEditModeStocks();
            
            LOGGER.info("Stocks enregistrés avec succès dans la base de données");
            
        } catch (SQLException e) {
            handleDatabaseError(e, "Impossible d'enregistrer les stocks");
        }
    }
    
    /**
     * Supprime les stocks qui ont été retirés de la liste
     */
    private void deleteRemovedStocks() throws SQLException {
        StringBuilder idListBuilder = new StringBuilder();
        for (Stock stock : stocks) {
            if (stock.getId() > 0) {
                if (idListBuilder.length() > 0) {
                    idListBuilder.append(",");
                }
                idListBuilder.append(stock.getId());
            }
        }
        
        if (idListBuilder.length() > 0) {
            String deleteQuery = "DELETE FROM stocks WHERE id NOT IN (" + idListBuilder.toString() + ")";
            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteQuery)) {
                deleteStatement.executeUpdate();
            }
        } else if (!stocks.isEmpty()) {
            // Si tous les éléments sont nouveaux, on peut éviter le NOT IN vide qui causerait une erreur
            try (PreparedStatement clearStatement = connection.prepareStatement("DELETE FROM stocks")) {
                clearStatement.executeUpdate();
            }
        }
    }
    
    /**
     * Met à jour ou insère les stocks dans la base de données avec les nouvelles colonnes
     */
    private void updateOrInsertStocks() throws SQLException {
        String upsertQuery = """
            INSERT INTO stocks (id, designation, quantite, etat, description, valeur_critique, statut, date_creation, quantite_initiale)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
                designation=VALUES(designation), 
                quantite=VALUES(quantite),
                etat=VALUES(etat), 
                description=VALUES(description), 
                valeur_critique=VALUES(valeur_critique),
                statut=VALUES(statut)""";
        
        try (PreparedStatement upsertStatement = connection.prepareStatement(upsertQuery, Statement.RETURN_GENERATED_KEYS)) {
            for (Stock stock : stocks) {
                // Mettre à jour le statut avant l'enregistrement
                stock.setStatut(calculateStockStatus(stock.getQuantite(), stock.getValeurCritique()));
                
                // Si c'est un nouvel élément (ID = 0), ne pas l'inclure dans l'INSERT avec ID
                if (stock.getId() <= 0) {
                    // Pour les nouveaux éléments, utiliser NULL pour l'ID auto-increment
                    String insertQuery = """
                        INSERT INTO stocks (designation, quantite, etat, description, valeur_critique, statut, date_creation, quantite_initiale)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
                        
                    try (PreparedStatement insertStatement = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                        insertStatement.setString(1, stock.getDesignation());
                        insertStatement.setInt(2, stock.getQuantite());
                        insertStatement.setString(3, stock.getEtat());
                        insertStatement.setString(4, stock.getDescription());
                        insertStatement.setInt(5, stock.getValeurCritique());
                        insertStatement.setString(6, stock.getStatut());
                        insertStatement.setTimestamp(7, Timestamp.valueOf(stock.getDateCreation()));
                        insertStatement.setInt(8, stock.getQuantiteInitiale());
                        
                        insertStatement.executeUpdate();
                        
                        // Récupérer l'ID généré
                        try (ResultSet rs = insertStatement.getGeneratedKeys()) {
                            if (rs.next()) {
                                stock.setId(rs.getInt(1));
                                LOGGER.info("Nouveau stock créé avec ID: " + stock.getId());
                            }
                        }
                    }
                } else {
                    // Pour les éléments existants, utiliser l'upsert normal
                    upsertStatement.setInt(1, stock.getId());
                    upsertStatement.setString(2, stock.getDesignation());
                    upsertStatement.setInt(3, stock.getQuantite());
                    upsertStatement.setString(4, stock.getEtat());
                    upsertStatement.setString(5, stock.getDescription());
                    upsertStatement.setInt(6, stock.getValeurCritique());
                    upsertStatement.setString(7, stock.getStatut());
                    upsertStatement.setTimestamp(8, Timestamp.valueOf(stock.getDateCreation()));
                    upsertStatement.setInt(9, stock.getQuantiteInitiale());
                    
                    upsertStatement.executeUpdate();
                }
            }
        }
    }

    
    /**
     * Enregistre les maintenances dans la base de données
     */
    private void saveMaintenanceToDatabase() {
        try {
            // Commencer une transaction
            connection.setAutoCommit(false);
            
            // Récupérer la liste des IDs existants pour gestion de la suppression
            deleteRemovedMaintenances();
            
            // Mettre à jour ou insérer les maintenances
            updateOrInsertMaintenances();
            
            // Valider la transaction
            connection.commit();
            connection.setAutoCommit(true);
            
            showAlert(Alert.AlertType.INFORMATION, "Succès", 
                     "Les données de maintenance ont été enregistrées avec succès", null);
            updateStatusIndicators();
            
            // Désactiver le mode édition
            toggleEditModeMaintenance();
            
            LOGGER.info("Maintenances enregistrées avec succès dans la base de données");
            
        } catch (SQLException e) {
            handleDatabaseError(e, "Impossible d'enregistrer les maintenances");
        }
    }
    
    /**
     * Supprime les maintenances qui ont été retirées de la liste
     */
    private void deleteRemovedMaintenances() throws SQLException {
        StringBuilder idListBuilder = new StringBuilder();
        for (Maintenance maintenance : maintenances) {
            if (maintenance.getId() > 0) {
                if (idListBuilder.length() > 0) {
                    idListBuilder.append(",");
                }
                idListBuilder.append(maintenance.getId());
            }
        }
        
        if (idListBuilder.length() > 0) {
            String deleteQuery = "DELETE FROM maintenances WHERE id NOT IN (" + idListBuilder.toString() + ")";
            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteQuery)) {
                deleteStatement.executeUpdate();
            }
        } else if (!maintenances.isEmpty()) {
            // Si tous les éléments sont nouveaux, on peut éviter le NOT IN vide qui causerait une erreur
            try (PreparedStatement clearStatement = connection.prepareStatement("DELETE FROM maintenances")) {
                clearStatement.executeUpdate();
            }
        }
    }
    
    /**
     * Met à jour ou insère les maintenances dans la base de données
     */
    private void updateOrInsertMaintenances() throws SQLException {
        String upsertQuery = """
            INSERT INTO maintenances (id, date_maintenance, designation, type_maintenance, description, effectuee, statut)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
                date_maintenance=VALUES(date_maintenance), 
                designation=VALUES(designation),
                type_maintenance=VALUES(type_maintenance), 
                description=VALUES(description), 
                effectuee=VALUES(effectuee),
                statut=VALUES(statut)""";
        
        try (PreparedStatement upsertStatement = connection.prepareStatement(upsertQuery, Statement.RETURN_GENERATED_KEYS)) {
            for (Maintenance maintenance : maintenances) {
                // Mettre à jour le statut avant l'enregistrement
                updateMaintenanceStatus(maintenance);
                
                upsertStatement.setInt(1, maintenance.getId());
                upsertStatement.setDate(2, java.sql.Date.valueOf(LocalDate.parse(maintenance.getDate())));
                upsertStatement.setString(3, maintenance.getDesignation());
                upsertStatement.setString(4, maintenance.getType());
                upsertStatement.setString(5, maintenance.getDescription());
                upsertStatement.setBoolean(6, maintenance.isEffectuee());
                upsertStatement.setString(7, maintenance.getStatut());
                
                upsertStatement.executeUpdate();
                
                // Si c'est un nouvel élément, récupérer l'ID généré
                if (maintenance.getId() <= 0) {
                    try (ResultSet rs = upsertStatement.getGeneratedKeys()) {
                        if (rs.next()) {
                            maintenance.setId(rs.getInt(1));
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Gère les erreurs de base de données
     */
    private void handleDatabaseError(SQLException e, String message) {
        try {
            // Annuler la transaction en cas d'erreur
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Erreur lors du rollback de la transaction", ex);
        }
        
        LOGGER.log(Level.SEVERE, message, e);
        showAlert(Alert.AlertType.ERROR, "Erreur de base de données", message, e.getMessage());
    }
    
    /**
     * Affiche une boîte de dialogue d'alerte
     */
    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}