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
import javafx.scene.layout.Priority;
import javafx.geometry.Insets;
import javafx.scene.paint.Color;
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
 * Contrôleur pour l'écran de gestion logistique qui gère les stocks et la maintenance
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
    
    // Éléments de l'interface définis dans le FXML
    @FXML private TableView<Stock> stocksTable;
    @FXML private TableColumn<Stock, String> statutStockColumn;
    @FXML private TableColumn<Stock, String> designationStockColumn;
    @FXML private TableColumn<Stock, Integer> quantiteColumn;
    @FXML private TableColumn<Stock, String> etatColumn;
    @FXML private TableColumn<Stock, String> descriptionStockColumn;
    @FXML private TableColumn<Stock, Integer> valeurCritiqueColumn;
    @FXML private Button btnAjouterStock;
    
    @FXML private TableView<Maintenance> maintenanceTable;
    @FXML private TableColumn<Maintenance, String> statutMaintenanceColumn;
    @FXML private TableColumn<Maintenance, String> dateMaintenanceColumn;
    @FXML private TableColumn<Maintenance, String> designationMaintenanceColumn;
    @FXML private TableColumn<Maintenance, String> typeMaintenanceColumn;
    @FXML private TableColumn<Maintenance, String> descriptionMaintenanceColumn;
    @FXML private TableColumn<Maintenance, Boolean> effectueeColumn;
    @FXML private Button btnAjouterMaintenance;
    @FXML private Label stocksCritiquesCount;
    @FXML private Label maintenancesUrgentesCount;
    @FXML private Label totalEquipementsCount;
    
    @FXML private Button btnMajStocks;
    @FXML private Button btnMajMaintenance;
    @FXML private Button btnValiderStocks;
    @FXML private Button btnValiderMaintenance;
    
    
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
            
            // Configurer les tableaux et charger les données
            initializeStocksTable();
            initializeMaintenanceTable();
            loadDataFromDatabase();
            
            // Initialiser les boutons
            setupButtons();
            btnAjouterStock.setDisable(true);
            btnAjouterMaintenance.setDisable(true);
            
            // Mettre à jour les statuts des maintenances
            updateAllMaintenanceStatuses();
            
            // Rafraîchir les tables
            refreshTables();
            
         // AJOUT : Mettre à jour les indicateurs de statut
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
     * Configure le tableau des stocks
     */
    private void initializeStocksTable() {
        // Configuration des colonnes du tableau des stocks
        statutStockColumn.setCellValueFactory(new PropertyValueFactory<>("statut"));
        designationStockColumn.setCellValueFactory(new PropertyValueFactory<>("designation"));
        quantiteColumn.setCellValueFactory(new PropertyValueFactory<>("quantite"));
        etatColumn.setCellValueFactory(new PropertyValueFactory<>("etat"));
        descriptionStockColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        valeurCritiqueColumn.setCellValueFactory(new PropertyValueFactory<>("valeurCritique"));
        
        // Style pour la colonne statut
        configureStatutStockColumn();
        
        // Associer les données
        stocksTable.setItems(stocks);
        
        // Configuration du menu contextuel et des actions sur les lignes
        configureStockTableRowFactory();
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
            
            // Menu contextuel
            ContextMenu contextMenu = createStockContextMenu(row);
            
            // N'afficher le menu contextuel qu'en mode édition
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(
                    javafx.beans.binding.Bindings.createBooleanBinding(
                        () -> editModeStocks && !row.isEmpty(), 
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
     * Crée le menu contextuel pour une ligne du tableau des stocks
     */
    private ContextMenu createStockContextMenu(TableRow<Stock> row) {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem editItem = new MenuItem("Modifier");
        MenuItem deleteItem = new MenuItem("Supprimer");
        
        editItem.setOnAction(event -> {
            if (!row.isEmpty() && editModeStocks) {
                showStockEditDialog(row.getItem());
            }
        });
        
        deleteItem.setOnAction(event -> {
            if (!row.isEmpty() && editModeStocks) {
                deleteStock(row.getItem());
            }
        });
        
        contextMenu.getItems().addAll(editItem, deleteItem);
        
        return contextMenu;
    }
    
    /**
     * Supprime un stock du tableau
     */
    private void deleteStock(Stock stock) {
        if (stock != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Confirmation de suppression");
            confirmation.setHeaderText("Supprimer un stock");
            confirmation.setContentText("Êtes-vous sûr de vouloir supprimer ce stock : " + stock.getDesignation() + " ?");
            
            Optional<ButtonType> result = confirmation.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                stocks.remove(stock);
                stocksTable.refresh();
                updateStatusIndicators();
                LOGGER.info("Stock supprimé: " + stock.getDesignation());
            }
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
     * Exporte les stocks en PDF
     */
    @FXML
    private void exporterStocksEnPDF() {
        try {
            Document document = new Document();
            File file = new File("Stocks_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
            PdfWriter.getInstance(document, new FileOutputStream(file));
            
            document.open();
            
            // Titre
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph("État des Stocks - Service Logistique", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            
            Paragraph date = new Paragraph("Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            date.setAlignment(Element.ALIGN_CENTER);
            document.add(date);
            document.add(new Paragraph(" "));
            
            // Statistiques résumées
            Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            document.add(new Paragraph("Résumé", sectionFont));
            
            Map<String, Integer> stats = new HashMap<>();
            stats.put("Total", stocks.size());
            stats.put("Normaux", 0);
            stats.put("Attention", 0);
            stats.put("Faibles", 0);
            stats.put("Critiques", 0);
            
            for (Stock stock : stocks) {
                switch (stock.getStatut()) {
                    case STATUT_VERT:
                        stats.put("Normaux", stats.get("Normaux") + 1);
                        break;
                    case STATUT_ORANGE:
                        stats.put("Attention", stats.get("Attention") + 1);
                        break;
                    case STATUT_ROUGE:
                        stats.put("Faibles", stats.get("Faibles") + 1);
                        break;
                    case STATUT_VIOLET:
                        stats.put("Critiques", stats.get("Critiques") + 1);
                        break;
                }
            }
            
            for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                document.add(new Paragraph(entry.getKey() + ": " + entry.getValue()));
            }
            document.add(new Paragraph(" "));
            
            // Tableau détaillé
            document.add(new Paragraph("Détail des Stocks", sectionFont));
            document.add(new Paragraph(" "));
            
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            
            // En-têtes
            String[] headers = {"Statut", "Désignation", "Quantité", "État", "Description", "Seuil Critique"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header));
                cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            // Données
            for (Stock stock : stocks) {
                // Statut avec couleur
                PdfPCell statutCell = new PdfPCell(new Phrase(getStatutText(stock.getStatut())));
                switch (stock.getStatut()) {
                    case STATUT_VERT:
                        statutCell.setBackgroundColor(BaseColor.GREEN);
                        break;
                    case STATUT_ORANGE:
                        statutCell.setBackgroundColor(BaseColor.ORANGE);
                        break;
                    case STATUT_ROUGE:
                        statutCell.setBackgroundColor(BaseColor.RED);
                        break;
                    case STATUT_VIOLET:
                        statutCell.setBackgroundColor(BaseColor.MAGENTA);
                        break;
                }
                table.addCell(statutCell);
                
                table.addCell(stock.getDesignation());
                table.addCell(String.valueOf(stock.getQuantite()));
                table.addCell(stock.getEtat());
                table.addCell(stock.getDescription());
                table.addCell(String.valueOf(stock.getValeurCritique()));
            }
            
            document.add(table);
            document.close();
            
            showAlert(Alert.AlertType.INFORMATION, "Export réussi", 
                     "Export des stocks réalisé avec succès", 
                     "Fichier: " + file.getAbsolutePath());
            
            HistoryManager.logCreation("Accueil Logistique", "Export PDF des stocks");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'export des stocks", e);
            showAlert(Alert.AlertType.ERROR, "Erreur d'export", 
                     "Impossible d'exporter les stocks", e.getMessage());
        }
    }

    /**
     * Exporte les maintenances en PDF
     */
    @FXML
    private void exporterMaintenanceEnPDF() {
        try {
            Document document = new Document();
            File file = new File("Maintenances_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
            PdfWriter.getInstance(document, new FileOutputStream(file));
            
            document.open();
            
            // Titre
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph("Planning de Maintenance - Service Logistique", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            
            Paragraph date = new Paragraph("Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            date.setAlignment(Element.ALIGN_CENTER);
            document.add(date);
            document.add(new Paragraph(" "));
            
            // Statistiques résumées
            Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            document.add(new Paragraph("Résumé", sectionFont));
            
            Map<String, Integer> stats = new HashMap<>();
            stats.put("Total", maintenances.size());
            stats.put("Effectuées", 0);
            stats.put("Programmées", 0);
            stats.put("Proches", 0);
            stats.put("Urgentes", 0);
            stats.put("En retard", 0);
            
            for (Maintenance maintenance : maintenances) {
                if (maintenance.isEffectuee()) {
                    stats.put("Effectuées", stats.get("Effectuées") + 1);
                } else {
                    switch (maintenance.getStatut()) {
                        case STATUT_VERT:
                            stats.put("Programmées", stats.get("Programmées") + 1);
                            break;
                        case STATUT_ORANGE:
                            stats.put("Proches", stats.get("Proches") + 1);
                            break;
                        case STATUT_ROUGE:
                            stats.put("Urgentes", stats.get("Urgentes") + 1);
                            break;
                        case STATUT_VIOLET:
                            stats.put("En retard", stats.get("En retard") + 1);
                            break;
                    }
                }
            }
            
            for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                document.add(new Paragraph(entry.getKey() + ": " + entry.getValue()));
            }
            document.add(new Paragraph(" "));
            
            // Tableau détaillé
            document.add(new Paragraph("Détail des Maintenances", sectionFont));
            document.add(new Paragraph(" "));
            
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            
            // En-têtes
            String[] headers = {"Statut", "Date", "Désignation", "Type", "Description", "Effectuée"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header));
                cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }
            
            // Données
            for (Maintenance maintenance : maintenances) {
                // Statut avec couleur
                PdfPCell statutCell = new PdfPCell(new Phrase(getStatutMaintenanceText(maintenance.getStatut(), maintenance.isEffectuee())));
                if (maintenance.isEffectuee()) {
                    statutCell.setBackgroundColor(BaseColor.BLUE);
                } else {
                    switch (maintenance.getStatut()) {
                        case STATUT_VERT:
                            statutCell.setBackgroundColor(BaseColor.GREEN);
                            break;
                        case STATUT_ORANGE:
                            statutCell.setBackgroundColor(BaseColor.ORANGE);
                            break;
                        case STATUT_ROUGE:
                            statutCell.setBackgroundColor(BaseColor.RED);
                            break;
                        case STATUT_VIOLET:
                            statutCell.setBackgroundColor(BaseColor.MAGENTA);
                            break;
                    }
                }
                table.addCell(statutCell);
                
                table.addCell(maintenance.getDate());
                table.addCell(maintenance.getDesignation());
                table.addCell(maintenance.getType());
                table.addCell(maintenance.getDescription());
                table.addCell(maintenance.isEffectuee() ? "Oui" : "Non");
            }
            
            document.add(table);
            document.close();
            
            showAlert(Alert.AlertType.INFORMATION, "Export réussi", 
                     "Export des maintenances réalisé avec succès", 
                     "Fichier: " + file.getAbsolutePath());
            
            HistoryManager.logCreation("Accueil Logistique", "Export PDF des maintenances");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'export des maintenances", e);
            showAlert(Alert.AlertType.ERROR, "Erreur d'export", 
                     "Impossible d'exporter les maintenances", e.getMessage());
        }
    }

    /**
     * Convertit le statut en texte lisible
     */
    private String getStatutText(String statut) {
        switch (statut) {
            case STATUT_VERT: return "Normal";
            case STATUT_ORANGE: return "Attention";
            case STATUT_ROUGE: return "Faible";
            case STATUT_VIOLET: return "Critique";
            default: return "Inconnu";
        }
    }

    /**
     * Convertit le statut de maintenance en texte lisible
     */
    private String getStatutMaintenanceText(String statut, boolean effectuee) {
        if (effectuee) return "Effectuée";
        
        switch (statut) {
            case STATUT_VERT: return "Programmée";
            case STATUT_ORANGE: return "Proche";
            case STATUT_ROUGE: return "Urgente";
            case STATUT_VIOLET: return "En retard";
            case STATUT_BLEU: return "Effectuée";
            default: return "Inconnu";
        }
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
                deleteMaintenance(row.getItem());
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
     * Supprime une maintenance du tableau
     */
    private void deleteMaintenance(Maintenance maintenance) {
        if (maintenance != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Confirmation de suppression");
            confirmation.setHeaderText("Supprimer une maintenance");
            confirmation.setContentText("Êtes-vous sûr de vouloir supprimer cette maintenance : " + maintenance.getDesignation() + " ?");
            
            Optional<ButtonType> result = confirmation.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                maintenances.remove(maintenance);
                maintenanceTable.refresh();
                updateStatusIndicators();
                LOGGER.info("Maintenance supprimée: " + maintenance.getDesignation());
            }
        }
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
     * Charge les stocks depuis la base de données
     */
    private void loadStocksFromDatabase() {
        stocks.clear();
        
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM stocks")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String designation = rs.getString("designation");
                    int quantite = rs.getInt("quantite");
                    String etat = rs.getString("etat");
                    String description = rs.getString("description");
                    int valeurCritique = rs.getInt("valeur_critique");
                    String statut = rs.getString("statut");
                    
                    stocks.add(new Stock(id, designation, quantite, etat, description, valeurCritique, statut));
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
    	    // Nous gardons le bouton Ajouter toujours visible, mais désactivé quand pas en mode édition
    	    btnAjouterStock.setDisable(!editModeStocks);
    	    
    	    LOGGER.info("Mode édition stocks: " + (editModeStocks ? "activé" : "désactivé"));
    }
    
    /**
     * Active/désactive le mode d'édition pour les maintenances
     */
    private void toggleEditModeMaintenance() {
    	editModeMaintenance = !editModeMaintenance;
        btnMajMaintenance.setText(editModeMaintenance ? "Terminer" : "Mettre à jour");
        btnValiderMaintenance.setVisible(editModeMaintenance);
        // Nous gardons le bouton Ajouter toujours visible, mais désactivé quand pas en mode édition
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
        
        TextField quantiteField = new TextField(stock.getQuantite() > 0 ? String.valueOf(stock.getQuantite()) : "");
        quantiteField.setPromptText("Quantité");
        
        TextField etatField = new TextField(stock.getEtat());
        etatField.setPromptText("État");
        
        TextArea descriptionField = new TextArea(stock.getDescription());
        descriptionField.setPromptText("Description");
        
        TextField valeurCritiqueField = new TextField(stock.getValeurCritique() > 0 ? String.valueOf(stock.getValeurCritique()) : "");
        valeurCritiqueField.setPromptText("Valeur critique");
        
        // Créer la disposition en grille
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        grid.add(new Label("Désignation:"), 0, 0);
        grid.add(designationField, 1, 0);
        grid.add(new Label("Quantité:"), 0, 1);
        grid.add(quantiteField, 1, 1);
        grid.add(new Label("État:"), 0, 2);
        grid.add(etatField, 1, 2);
        grid.add(new Label("Valeur critique:"), 0, 3);
        grid.add(valeurCritiqueField, 1, 3);
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
                    
                    int quantite = Integer.parseInt(quantiteField.getText().trim());
                    int valeurCritique = Integer.parseInt(valeurCritiqueField.getText().trim());
                    
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
                    
                    // Calculer le statut
                    updatedStock.setStatut(calculateStockStatus(quantite, valeurCritique));
                    
                    return updatedStock;
                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Erreur de format", 
                            "La quantité et la valeur critique doivent être des nombres entiers", null);
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
            } else {
                // Mettre à jour l'objet existant avec les nouvelles valeurs
                existingStock.setDesignation(updatedStock.getDesignation());
                existingStock.setQuantite(updatedStock.getQuantite());
                existingStock.setEtat(updatedStock.getEtat());
                existingStock.setDescription(updatedStock.getDescription());
                existingStock.setValeurCritique(updatedStock.getValeurCritique());
                existingStock.setStatut(updatedStock.getStatut());
                LOGGER.info("Stock modifié: " + updatedStock.getDesignation());
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
            } else {
                // Mettre à jour l'objet existant avec les nouvelles valeurs
                existingMaintenance.setDate(updatedMaintenance.getDate());
                existingMaintenance.setDesignation(updatedMaintenance.getDesignation());
                existingMaintenance.setType(updatedMaintenance.getType());
                existingMaintenance.setDescription(updatedMaintenance.getDescription());
                existingMaintenance.setEffectuee(updatedMaintenance.isEffectuee());
                existingMaintenance.setStatut(updatedMaintenance.getStatut());
                LOGGER.info("Maintenance modifiée: " + updatedMaintenance.getDesignation());
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
     * Enregistre les stocks dans la base de données
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
     * Met à jour ou insère les stocks dans la base de données
     */
    private void updateOrInsertStocks() throws SQLException {
        String upsertQuery = "INSERT INTO stocks (id, designation, quantite, etat, description, valeur_critique, statut) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                           "ON DUPLICATE KEY UPDATE designation=VALUES(designation), quantite=VALUES(quantite), " +
                           "etat=VALUES(etat), description=VALUES(description), valeur_critique=VALUES(valeur_critique), " +
                           "statut=VALUES(statut)";
        
        try (PreparedStatement upsertStatement = connection.prepareStatement(upsertQuery, Statement.RETURN_GENERATED_KEYS)) {
            for (Stock stock : stocks) {
                // Mettre à jour le statut avant l'enregistrement
                stock.setStatut(calculateStockStatus(stock.getQuantite(), stock.getValeurCritique()));
                
                upsertStatement.setInt(1, stock.getId());
                upsertStatement.setString(2, stock.getDesignation());
                upsertStatement.setInt(3, stock.getQuantite());
                upsertStatement.setString(4, stock.getEtat());
                upsertStatement.setString(5, stock.getDescription());
                upsertStatement.setInt(6, stock.getValeurCritique());
                upsertStatement.setString(7, stock.getStatut());
                
                upsertStatement.executeUpdate();
                
                // Si c'est un nouvel élément, récupérer l'ID généré
                if (stock.getId() <= 0) {
                    try (ResultSet rs = upsertStatement.getGeneratedKeys()) {
                        if (rs.next()) {
                            stock.setId(rs.getInt(1));
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Génère un rapport de statut complet
     */
    @FXML
    private void genererRapportStatut() {
        try {
            // Créer un document
            Document document = new Document();
            File file = new File("Rapport_Statut_Logistique_" + 
                               LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
            PdfWriter.getInstance(document, new FileOutputStream(file));
            
            document.open();
            
            // Titre
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph("Rapport de Statut Logistique", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            
            Paragraph date = new Paragraph("Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            date.setAlignment(Element.ALIGN_CENTER);
            document.add(date);
            document.add(new Paragraph(" "));
            
            // Section Stocks
            Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            document.add(new Paragraph("1. État des Stocks", sectionFont));
            document.add(new Paragraph(" "));
            
            // Statistiques stocks
            Map<String, Integer> stockStats = new HashMap<>();
            stockStats.put("Total", stocks.size());
            stockStats.put("Critiques", 0);
            stockStats.put("Faibles", 0);
            stockStats.put("Normaux", 0);
            
            for (Stock stock : stocks) {
                switch (stock.getStatut()) {
                    case STATUT_VIOLET:
                        stockStats.put("Critiques", stockStats.get("Critiques") + 1);
                        break;
                    case STATUT_ROUGE:
                        stockStats.put("Faibles", stockStats.get("Faibles") + 1);
                        break;
                    case STATUT_ORANGE:
                    case STATUT_VERT:
                        stockStats.put("Normaux", stockStats.get("Normaux") + 1);
                        break;
                }
            }
            
            PdfPTable stockTable = new PdfPTable(2);
            stockTable.setWidthPercentage(50);
            stockTable.addCell("Statut");
            stockTable.addCell("Nombre");
            
            for (Map.Entry<String, Integer> entry : stockStats.entrySet()) {
                stockTable.addCell(entry.getKey());
                stockTable.addCell(entry.getValue().toString());
            }
            
            document.add(stockTable);
            document.add(new Paragraph(" "));
            
            // Section Maintenances
            document.add(new Paragraph("2. État des Maintenances", sectionFont));
            document.add(new Paragraph(" "));
            
            // Statistiques maintenances
            Map<String, Integer> maintenanceStats = new HashMap<>();
            maintenanceStats.put("Total", maintenances.size());
            maintenanceStats.put("Effectuées", 0);
            maintenanceStats.put("En retard", 0);
            maintenanceStats.put("Urgentes", 0);
            maintenanceStats.put("Programmées", 0);
            
            for (Maintenance maintenance : maintenances) {
                if (maintenance.isEffectuee()) {
                    maintenanceStats.put("Effectuées", maintenanceStats.get("Effectuées") + 1);
                } else {
                    switch (maintenance.getStatut()) {
                        case STATUT_VIOLET:
                            maintenanceStats.put("En retard", maintenanceStats.get("En retard") + 1);
                            break;
                        case STATUT_ROUGE:
                            maintenanceStats.put("Urgentes", maintenanceStats.get("Urgentes") + 1);
                            break;
                        case STATUT_ORANGE:
                        case STATUT_VERT:
                            maintenanceStats.put("Programmées", maintenanceStats.get("Programmées") + 1);
                            break;
                    }
                }
            }
            
            PdfPTable maintenanceTable = new PdfPTable(2);
            maintenanceTable.setWidthPercentage(50);
            maintenanceTable.addCell("Statut");
            maintenanceTable.addCell("Nombre");
            
            for (Map.Entry<String, Integer> entry : maintenanceStats.entrySet()) {
                maintenanceTable.addCell(entry.getKey());
                maintenanceTable.addCell(entry.getValue().toString());
            }
            
            document.add(maintenanceTable);
            
            document.close();
            
            showAlert(Alert.AlertType.INFORMATION, "Rapport généré", 
                     "Le rapport de statut a été généré avec succès", 
                     "Fichier: " + file.getAbsolutePath());
            
            LOGGER.info("Rapport de statut généré: " + file.getAbsolutePath());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la génération du rapport", e);
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                     "Impossible de générer le rapport", e.getMessage());
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
        String upsertQuery = "INSERT INTO maintenances (id, date_maintenance, designation, type_maintenance, description, effectuee, statut) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                           "ON DUPLICATE KEY UPDATE date_maintenance=VALUES(date_maintenance), designation=VALUES(designation), " +
                           "type_maintenance=VALUES(type_maintenance), description=VALUES(description), effectuee=VALUES(effectuee), " +
                           "statut=VALUES(statut)";
        
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