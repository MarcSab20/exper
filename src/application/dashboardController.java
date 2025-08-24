package application;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.time.LocalDate;
import java.time.Period;

import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

public class dashboardController {

    @FXML private FlowPane chartsContainer;
    @FXML private Label messagesLabel;
    @FXML private Label salesLabel;
    @FXML private Label notificationsLabel;
    @FXML private Label scheduledLabel;
    @FXML private Label messagesCount, salesCount, notificationsCount, scheduledCount;
    @FXML private Label serviceInfoLabel;
    @FXML private VBox emptyStateMessage;
    @FXML private Text lastUpdateTime;
    @FXML private Text chartsCount;

    // Informations de connexion à la base de données
    private final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private final String DB_USER = "marco";
    private final String DB_PASSWORD = "29Papa278.";
    
    private String currentService;
    private List<String> availableTables;
    
    // Couleurs prédéfinies pour les graphiques
    private final String[] CHART_COLORS = {
        "#3498db", "#e74c3c", "#2ecc71", "#f39c12", "#9b59b6", 
        "#1abc9c", "#e67e22", "#34495e", "#16a085", "#27ae60",
        "#8e44ad", "#2980b9", "#f1c40f", "#d35400", "#c0392b"
    };
    
    @FXML
    public void initialize() {
        // Récupération du service et des tables accessibles
        currentService = UserSession.getCurrentService();
        availableTables = ServicePermissions.getTablesForService(currentService);
        
        // Configuration initiale du conteneur de graphiques
        setupChartsContainerWithOptions();
        
        try {
            // 1. Initialiser les informations de service
            initializeServiceInfo();
            
            // 2. Charger les données depuis la base de données
            loadDataFromDatabase();
            
            // 3. Mettre à jour les statistiques dans les boîtes
            updateStats();
            
            // 4. Créer les graphiques initiaux selon le service
            createServiceSpecificCharts();
            
            // 5. Finaliser l'initialisation (gestion état vide, actualisation, etc.)
            finalizeInitialization();
            
            System.out.println("Dashboard initialisé avec succès pour le service: " + currentService);
            
        } catch (SQLException e) {
            System.err.println("Erreur lors de l'initialisation du dashboard: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Erreur de connexion à la base de données", 
                    "Impossible de charger les données du dashboard.\n" + 
                    "Erreur: " + e.getMessage());
            
            // En cas d'erreur, afficher un dashboard minimal
            initializeMinimalDashboard();
        } catch (Exception e) {
            System.err.println("Erreur inattendue lors de l'initialisation: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Erreur d'initialisation", 
                    "Une erreur inattendue s'est produite.\n" + 
                    "Erreur: " + e.getMessage());
            initializeMinimalDashboard();
        }
    }
    
    
    /**
     * Méthode publique pour actualiser manuellement le dashboard
     */
    public void refreshDashboard() {
        try {
            // Recharger les données
            loadDataFromDatabase();
            
            // Actualiser les graphiques
            createServiceSpecificCharts();
            
            // Mettre à jour l'affichage
            updateEmptyState();
            updateLastRefreshTime();
            
            // Enregistrer l'action
            HistoryManager.logUpdate("Dashboard", 
                    "Actualisation manuelle du dashboard - Service: " + currentService);
            
            showInformation("Actualisation réussie", 
                    "Le dashboard a été actualisé avec succès.");
            
        } catch (SQLException e) {
            showErrorAlert("Erreur d'actualisation", 
                    "Impossible d'actualiser le dashboard: " + e.getMessage());
        }
    }
    
    /**
     * Initialise un dashboard minimal en cas d'erreur
     */
    private void initializeMinimalDashboard() {
        Platform.runLater(() -> {
            try {
                // Réinitialiser les compteurs à zéro
                if (messagesCount != null) messagesCount.setText("0");
                if (salesCount != null) salesCount.setText("0");
                if (notificationsCount != null) notificationsCount.setText("0");
                if (scheduledCount != null) scheduledCount.setText("0");
                
                // Définir les labels par défaut
                if (messagesLabel != null) messagesLabel.setText("Données");
                if (salesLabel != null) salesLabel.setText("Indisponibles");
                if (notificationsLabel != null) notificationsLabel.setText("Erreur");
                if (scheduledLabel != null) scheduledLabel.setText("Connexion");
                
                // Vider le conteneur de graphiques
                if (chartsContainer != null) {
                    chartsContainer.getChildren().clear();
                }
                
                // Afficher un message d'erreur dans le dashboard
                addErrorMessage();
                
                // Mettre à jour l'état
                updateEmptyState();
                updateLastRefreshTime();
                
            } catch (Exception e) {
                System.err.println("Erreur lors de l'initialisation minimale: " + e.getMessage());
            }
        });
    }

    /**
     * Ajoute un message d'erreur visible dans le dashboard
     */
    private void addErrorMessage() {
        VBox errorContainer = new VBox(15);
        errorContainer.setAlignment(Pos.CENTER);
        errorContainer.setStyle(
            "-fx-background-color: #fff5f5;" +
            "-fx-border-color: #fed7d7;" +
            "-fx-border-width: 2px;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 30px;" +
            "-fx-min-width: 400px;" +
            "-fx-min-height: 200px;"
        );
        
        Label errorIcon = new Label("⚠️");
        errorIcon.setStyle("-fx-font-size: 48px;");
        
        Label errorTitle = new Label("Erreur de connexion");
        errorTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #e53e3e;");
        
        Label errorMessage = new Label(
            "Impossible de se connecter à la base de données.\n" +
            "Vérifiez votre connexion et réessayez.\n\n" +
            "Service: " + currentService
        );
        errorMessage.setStyle("-fx-font-size: 12px; -fx-text-fill: #742a2a; -fx-text-alignment: center;");
        errorMessage.setWrapText(true);
        errorMessage.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        
        Button retryButton = new Button("🔄 Réessayer");
        retryButton.setStyle(
            "-fx-background-color: #e53e3e;" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 10px 20px;" +
            "-fx-background-radius: 6px;"
        );
        retryButton.setOnAction(e -> {
            chartsContainer.getChildren().clear();
            initialize(); // Relancer l'initialisation
        });
        
        errorContainer.getChildren().addAll(errorIcon, errorTitle, errorMessage, retryButton);
        
        if (chartsContainer != null) {
            chartsContainer.getChildren().add(errorContainer);
        }
    }

    /**
     * Finalise l'initialisation avec toutes les vérifications
     */
    private void finalizeInitialization() {
        Platform.runLater(() -> {
            try {
                // Mettre à jour l'état vide du dashboard
                updateEmptyState();
                
                // Mettre à jour l'heure de dernière actualisation
                updateLastRefreshTime();
                
                // Programmer l'actualisation périodique (seulement si pas d'erreur)
                if (availableTables != null && !availableTables.isEmpty()) {
                    schedulePeriodicRefresh();
                }
                
                // Appliquer le thème spécifique au service
                applyServiceTheme();
                
                        
            } catch (Exception e) {
                System.err.println("Erreur lors de la finalisation: " + e.getMessage());
            }
        });
    }
    
    /**
     * Applique le thème visuel selon le service
     */
    private void applyServiceTheme() {
        if (chartsContainer == null || chartsContainer.getScene() == null) return;
        
        try {
            javafx.scene.Parent root = chartsContainer.getScene().getRoot();
            
            // Supprimer les anciens thèmes
            root.getStyleClass().removeAll("logistique-theme", "operations-theme", "rh-theme");
            
            // Appliquer le nouveau thème
            switch (currentService) {
                case "Logistique":
                    root.getStyleClass().add("logistique-theme");
                    break;
                case "Opérations":
                    root.getStyleClass().add("operations-theme");
                    break;
                case "Ressources Humaines":
                    root.getStyleClass().add("rh-theme");
                    break;
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de l'application du thème: " + e.getMessage());
        }
    }

    /**
     * Version robuste de updateEmptyState avec vérifications
     */
    private void updateEmptyState() {
        Platform.runLater(() -> {
            try {
                boolean hasCharts = chartsContainer != null && chartsContainer.getChildren().size() > 0;
                
                if (emptyStateMessage != null) {
                    emptyStateMessage.setVisible(!hasCharts);
                    emptyStateMessage.setManaged(!hasCharts);
                }
                
                if (chartsCount != null) {
                    int chartCount = (chartsContainer != null) ? chartsContainer.getChildren().size() : 0;
                    chartsCount.setText(chartCount + " graphique(s) affiché(s)");
                }
                
            } catch (Exception e) {
                System.err.println("Erreur lors de la mise à jour de l'état vide: " + e.getMessage());
            }
        });
    }

    
    /**
     * Ajoute des tooltips informatifs aux graphiques
     */
    private void addTooltipToChart(Chart chart, String tableName, String column) {
        if (chart != null) {
            Tooltip tooltip = new Tooltip(
                "Table: " + tableName + "\n" +
                "Colonne: " + column + "\n" +
                "Service: " + currentService + "\n" +
                "Clic droit pour plus d'options"
            );
            tooltip.setStyle(
                "-fx-background-color: rgba(0,0,0,0.8); " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 6px; " +
                "-fx-font-size: 11px; " +
                "-fx-padding: 8px 12px;"
            );
            Tooltip.install(chart, tooltip);
            
            // Ajouter un menu contextuel
            ContextMenu contextMenu = createChartContextMenu(chart, tableName, column);
            chart.setOnContextMenuRequested(e -> {
                contextMenu.show(chart, e.getScreenX(), e.getScreenY());
            });
        }
    }
    
    /**
     * Crée un menu contextuel pour les graphiques
     */
    private ContextMenu createChartContextMenu(Chart chart, String tableName, String column) {
        ContextMenu contextMenu = new ContextMenu();
        
        // Option pour exporter le graphique
        MenuItem exportItem = new MenuItem("📤 Exporter ce graphique");
        exportItem.setOnAction(e -> exportChart(chart));
        
        // Option pour actualiser le graphique
        MenuItem refreshItem = new MenuItem("🔄 Actualiser ce graphique");
        refreshItem.setOnAction(e -> refreshSingleChart(chart, tableName, column));
        
        // Option pour supprimer le graphique
        MenuItem removeItem = new MenuItem("🗑️ Supprimer ce graphique");
        removeItem.setOnAction(e -> removeSingleChart(chart));
        
        // Option pour dupliquer le graphique avec un autre type
        MenuItem duplicateItem = new MenuItem("📋 Dupliquer en autre type");
        duplicateItem.setOnAction(e -> duplicateChartAsOtherType(chart, tableName, column));
        
        contextMenu.getItems().addAll(exportItem, refreshItem, 
                                     new SeparatorMenuItem(), 
                                     duplicateItem, 
                                     new SeparatorMenuItem(), 
                                     removeItem);
        
        return contextMenu;
    }
    
    /**
     * Exporte un graphique individuel
     */
    private void exportChart(Chart chart) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Exporter le graphique - " + chart.getTitle());
            fileChooser.setInitialFileName(chart.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + ".png");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images PNG", "*.png"),
                new FileChooser.ExtensionFilter("Images JPEG", "*.jpg")
            );
            
            java.io.File file = fileChooser.showSaveDialog(chart.getScene().getWindow());
            if (file != null) {
                WritableImage image = chart.snapshot(new javafx.scene.SnapshotParameters(), null);
                
                String format = "png";
                if (file.getName().toLowerCase().endsWith(".jpg") || file.getName().toLowerCase().endsWith(".jpeg")) {
                    format = "jpg";
                }
                
                javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), format, file);
                
                HistoryManager.logCreation("Dashboard", 
                        "Export de graphique: " + chart.getTitle() + " - Service: " + currentService);
                
                showInformation("Export réussi", 
                        "Le graphique a été exporté vers:\n" + file.getAbsolutePath());
            }
        } catch (Exception e) {
            showErrorAlert("Erreur d'export", 
                    "Impossible d'exporter le graphique: " + e.getMessage() + 
                    "\nVérifiez que vous avez les permissions d'écriture dans le dossier sélectionné.");
        }
    }

    /**
     * Actualise un graphique individuel
     */
    private void refreshSingleChart(Chart chart, String tableName, String column) {
        try {
            // Déterminer le type de graphique
        	String type;
        	if (chart instanceof PieChart) type = "Camembert";
        	else if (chart instanceof LineChart) type = "Ligne";
        	else type = "Barre";
        	final String chartType = type;
            
            // Récupérer les données actualisées
            Map<String, Integer> data = getDistributionData(tableName, column);
            
            if (!data.isEmpty()) {
                // Trouver l'index du graphique dans le conteneur
                int chartIndex = -1;
                for (int i = 0; i < chartsContainer.getChildren().size(); i++) {
                    Node node = chartsContainer.getChildren().get(i);
                    if (node instanceof VBox) {
                        VBox container = (VBox) node;
                        if (container.getChildren().contains(chart) || 
                            (container.getChildren().size() > 1 && container.getChildren().get(1) == chart)) {
                            chartIndex = i;
                            break;
                        }
                    }
                }
                
                if (chartIndex >= 0) {
                    // Supprimer l'ancien graphique
                    chartsContainer.getChildren().remove(chartIndex);
                    
                    // Créer et ajouter le nouveau graphique
                    Platform.runLater(() -> {
                        try {
                            Chart newChart = null;
                            switch (chartType) {
                                case "Camembert":
                                    newChart = createEnhancedPieChart(data, chart.getTitle());
                                    break;
                                case "Ligne":
                                    newChart = createEnhancedLineChart(data, chart.getTitle(), column);
                                    break;
                                default:
                                    newChart = createEnhancedBarChart(data, chart.getTitle(), column);
                            }
                            
                            if (newChart != null) {
                                addChartToContainer(newChart);
                                showInformation("Actualisation réussie", 
                                        "Le graphique a été actualisé avec succès.");
                            }
                        } catch (Exception e) {
                            showErrorAlert("Erreur d'actualisation", e.getMessage());
                        }
                    });
                }
            }
            
        } catch (SQLException e) {
            showErrorAlert("Erreur d'actualisation", 
                    "Impossible d'actualiser le graphique: " + e.getMessage());
        }
    }



    /**
     * Met à jour l'heure de dernière actualisation
     */
    private void updateLastRefreshTime() {
        Platform.runLater(() -> {
            try {
                if (lastUpdateTime != null) {
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                    lastUpdateTime.setText(now.format(formatter));
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de la mise à jour de l'heure: " + e.getMessage());
            }
        });
    }
    
    /**
     * Programme une actualisation périodique des statistiques (toutes les 5 minutes)
     */
    private void schedulePeriodicRefresh() {
        Service<Void> refreshService = new Service<Void>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        Thread.sleep(300000); // 5 minutes
                        return null;
                    }
                    
                    @Override
                    protected void succeeded() {
                        Platform.runLater(() -> {
                            try {
                                loadDataFromDatabase();
                                updateLastRefreshTime();
                            } catch (SQLException e) {
                                System.err.println("Erreur lors de l'actualisation automatique: " + e.getMessage());
                            }
                        });
                        // Relancer le service
                        if (!isCancelled()) {
                            restart();
                        }
                    }
                    
                    @Override
                    protected void failed() {
                        System.err.println("Échec de l'actualisation automatique: " + getException().getMessage());
                        if (!isCancelled()) {
                            restart();
                        }
                    }
                };
            }
        };
        
        // Démarrer le service d'actualisation
        refreshService.start();
    }
    
    public void cleanup() {
        // Arrêter le service d'actualisation périodique si nécessaire
        if (chartsContainer != null) {
            chartsContainer.getChildren().clear();
        }
    }
    
    /**
     * Configure le conteneur de graphiques avec bouton Options bien placé
     */
    private void setupChartsContainerWithOptions() {
        if (chartsContainer != null) {
            // Configuration du FlowPane
            chartsContainer.setHgap(20);
            chartsContainer.setVgap(20);
            chartsContainer.setPadding(new Insets(15));
            chartsContainer.setAlignment(Pos.TOP_LEFT);
            chartsContainer.setPrefWrapLength(Region.USE_COMPUTED_SIZE);
            
            // Ajouter le bouton Options en haut à droite
            addOptionsButtonToParent();
        }
    }
    
    /**
     * Supprime un graphique individuel
     */
    private void removeSingleChart(Chart chart) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation de suppression");
        confirmAlert.setHeaderText("Supprimer ce graphique");
        confirmAlert.setContentText("Êtes-vous sûr de vouloir supprimer le graphique '" + chart.getTitle() + "' ?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Trouver et supprimer le conteneur du graphique
            chartsContainer.getChildren().removeIf(node -> {
                if (node instanceof VBox) {
                    VBox container = (VBox) node;
                    return container.getChildren().contains(chart) || 
                           (container.getChildren().size() > 1 && container.getChildren().get(1) == chart);
                }
                return false;
            });
            
            updateEmptyState();
            
            HistoryManager.logDeletion("Dashboard", 
                    "Suppression du graphique: " + chart.getTitle() + " - Service: " + currentService);
            
            showInformation("Suppression réussie", 
                    "Le graphique a été supprimé avec succès.");
        }
    }

    /**
     * Duplique un graphique avec un autre type
     */
    private void duplicateChartAsOtherType(Chart chart, String tableName, String column) {
        List<String> availableTypes = new ArrayList<>();
        if (!(chart instanceof PieChart)) availableTypes.add("Camembert");
        if (!(chart instanceof BarChart)) availableTypes.add("Barre");
        if (!(chart instanceof LineChart)) availableTypes.add("Ligne");
        
        if (availableTypes.isEmpty()) {
            showErrorAlert("Duplication impossible", 
                    "Tous les types de graphiques sont déjà utilisés pour ces données.");
            return;
        }
        
        ChoiceDialog<String> dialog = new ChoiceDialog<>(availableTypes.get(0), availableTypes);
        dialog.setTitle("Duplication de graphique");
        dialog.setHeaderText("Choisir le type de graphique");
        dialog.setContentText("Type de graphique pour la duplication:");
        
        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                addDistributionChart(tableName, column, 
                        chart.getTitle() + " (" + result.get() + ")", result.get());
                
                showInformation("Duplication réussie", 
                        "Le graphique a été dupliqué avec succès en type " + result.get() + ".");
                        
            } catch (SQLException e) {
                showErrorAlert("Erreur de duplication", e.getMessage());
            }
        }
    }

    
    /**
     * Ajoute le bouton Options dans le parent du conteneur de graphiques
     */
    private void addOptionsButtonToParent() {
        Platform.runLater(() -> {
            try {
                // Obtenir le parent du conteneur de graphiques
                Parent parent = chartsContainer.getParent();
                if (parent instanceof VBox) {
                    VBox vboxParent = (VBox) parent;
                    
                    // Créer un HBox pour le header avec bouton Options
                    HBox headerBox = new HBox();
                    headerBox.setAlignment(Pos.CENTER_RIGHT);
                    headerBox.setPadding(new Insets(10, 20, 0, 20));
                    
                    Button optionsButton = new Button("⚙️ Options Dashboard");
                    optionsButton.getStyleClass().addAll("button", "options-button");
                    optionsButton.setOnAction(e -> showCustomizeDialog());
                    
                    headerBox.getChildren().add(optionsButton);
                    
                    // Insérer le header en première position si pas déjà présent
                    if (vboxParent.getChildren().size() > 0 && 
                        !(vboxParent.getChildren().get(0) instanceof HBox)) {
                        vboxParent.getChildren().add(0, headerBox);
                    }
                }
            } catch (Exception e) {
                // Si on ne peut pas ajouter dans le parent, ignorer silencieusement
                System.out.println("Impossible d'ajouter le bouton Options dans le header");
            }
        });
    }
    
    private void initializeServiceInfo() {
        if (serviceInfoLabel != null) {
            serviceInfoLabel.setText("Service: " + currentService + " | Tables disponibles: " + availableTables.size());
        }
    }
    
    private void loadDataFromDatabase() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Statistiques de base depuis identite_personnelle (commune à tous)
            if (availableTables.contains("identite_personnelle")) {
                stats.put("total_personnel", countRecords(conn, "SELECT COUNT(*) FROM identite_personnelle"));
                stats.put("feminin_personnel", countRecords(conn, "SELECT COUNT(*) FROM identite_personnelle WHERE sexe = 'Femme'"));
            }
            
            // Statistiques spécifiques par service
            switch (currentService) {
                case "Logistique":
                    loadLogistiqueStats(conn, stats);
                    break;
                case "Opérations":
                    loadOperationsStats(conn, stats);
                    break;
                case "Ressources Humaines":
                    loadRhStats(conn, stats);
                    break;
                default:
                    loadDefaultStats(conn, stats);
            }
        }
        
        updateStatBoxes(stats);
    }
    
    private void loadLogistiqueStats(Connection conn, Map<String, Integer> stats) throws SQLException {
        if (availableTables.contains("parametres_corporels")) {
            stats.put("parametres_definis", countRecords(conn, "SELECT COUNT(*) FROM parametres_corporels"));
        }
        if (availableTables.contains("dotation_particuliere")) {
            stats.put("dotations_actives", countRecords(conn, "SELECT COUNT(DISTINCT matricule) FROM dotation_particuliere"));
        }
        if (availableTables.contains("maintenance")) {
            stats.put("maintenances_planifiees", countRecords(conn, "SELECT COUNT(*) FROM maintenance"));
        }
    }
    
    private void loadOperationsStats(Connection conn, Map<String, Integer> stats) throws SQLException {
        if (availableTables.contains("operation")) {
            stats.put("operations_total", countRecords(conn, "SELECT COUNT(*) FROM operation"));
            stats.put("operations_recentes", countRecords(conn, "SELECT COUNT(*) FROM operation WHERE annee >= YEAR(CURDATE()) - 1"));
        }
        if (availableTables.contains("personnel_naviguant")) {
            stats.put("personnel_naviguant", countRecords(conn, "SELECT COUNT(*) FROM personnel_naviguant"));
        }
        if (availableTables.contains("punition")) {
            stats.put("punitions_actives", countRecords(conn, "SELECT COUNT(*) FROM punition WHERE date >= DATE_SUB(CURDATE(), INTERVAL 1 YEAR)"));
        }
    }
    
    private void loadRhStats(Connection conn, Map<String, Integer> stats) throws SQLException {
        if (availableTables.contains("grade_actuel")) {
            stats.put("grades_officiers", countRecords(conn, "SELECT COUNT(*) FROM grade_actuel WHERE rang LIKE '%Lieutenant%' OR rang LIKE '%Capitaine%' OR rang LIKE '%Colonel%'"));
        }
        if (availableTables.contains("ecole_militaire")) {
            stats.put("formations_militaires", countRecords(conn, "SELECT COUNT(DISTINCT matricule) FROM ecole_militaire"));
        }
        if (availableTables.contains("decoration")) {
            stats.put("decorations_total", countRecords(conn, "SELECT COUNT(*) FROM decoration"));
        }
        if (availableTables.contains("historique_grades")) {
            stats.put("promotions_recentes", countRecords(conn, "SELECT COUNT(*) FROM historique_grades WHERE date >= DATE_SUB(CURDATE(), INTERVAL 1 YEAR)"));
        }
    }
    
    private void loadDefaultStats(Connection conn, Map<String, Integer> stats) throws SQLException {
        stats.put("total_tables", availableTables.size());
        stats.put("mises_a_jour", countRecords(conn, "SELECT COUNT(*) FROM historique_mises_a_jour_enhanced"));
    }
    
    private int countRecords(Connection conn, String query) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
    
    private void updateStatBoxes(Map<String, Integer> stats) {
        switch (currentService) {
            case "Logistique":
                messagesLabel.setText("Personnel total");
                messagesCount.setText(String.valueOf(stats.getOrDefault("total_personnel", 0)));
                
                salesLabel.setText("Paramètres corporels");
                salesCount.setText(String.valueOf(stats.getOrDefault("parametres_definis", 0)));
                
                notificationsLabel.setText("Dotations actives");
                notificationsCount.setText(String.valueOf(stats.getOrDefault("dotations_actives", 0)));
                
                scheduledLabel.setText("Maintenances");
                scheduledCount.setText(String.valueOf(stats.getOrDefault("maintenances_planifiees", 0)));
                break;
                
            case "Opérations":
                messagesLabel.setText("Personnel total");
                messagesCount.setText(String.valueOf(stats.getOrDefault("total_personnel", 0)));
                
                salesLabel.setText("Opérations totales");
                salesCount.setText(String.valueOf(stats.getOrDefault("operations_total", 0)));
                
                notificationsLabel.setText("Personnel naviguant");
                notificationsCount.setText(String.valueOf(stats.getOrDefault("personnel_naviguant", 0)));
                
                scheduledLabel.setText("Opérations récentes");
                scheduledCount.setText(String.valueOf(stats.getOrDefault("operations_recentes", 0)));
                break;
                
            case "Ressources Humaines":
                messagesLabel.setText("Personnel total");
                messagesCount.setText(String.valueOf(stats.getOrDefault("total_personnel", 0)));
                
                salesLabel.setText("Officiers");
                salesCount.setText(String.valueOf(stats.getOrDefault("grades_officiers", 0)));
                
                notificationsLabel.setText("Formations militaires");
                notificationsCount.setText(String.valueOf(stats.getOrDefault("formations_militaires", 0)));
                
                scheduledLabel.setText("Décorations");
                scheduledCount.setText(String.valueOf(stats.getOrDefault("decorations_total", 0)));
                break;
                
            default:
                messagesLabel.setText("Personnel total");
                messagesCount.setText(String.valueOf(stats.getOrDefault("total_personnel", 0)));
                
                salesLabel.setText("Tables disponibles");
                salesCount.setText(String.valueOf(stats.getOrDefault("total_tables", 0)));
                
                notificationsLabel.setText("Mises à jour");
                notificationsCount.setText(String.valueOf(stats.getOrDefault("mises_a_jour", 0)));
                
                scheduledLabel.setText("Personnel féminin");
                scheduledCount.setText(String.valueOf(stats.getOrDefault("feminin_personnel", 0)));
        }
    }

    private void updateStats() {
        // Cette méthode est appelée après updateStatBoxes
    }

    private void createServiceSpecificCharts() throws SQLException {
        Platform.runLater(() -> chartsContainer.getChildren().clear());
        
        switch (currentService) {
            case "Logistique":
                createLogistiqueCharts();
                break;
            case "Opérations":
                createOperationsCharts();
                break;
            case "Ressources Humaines":
                createRhCharts();
                break;
            default:
                createDefaultCharts();
        }
    }
    
    private void createLogistiqueCharts() throws SQLException {
        if (availableTables.contains("parametres_corporels")) {
            addDistributionChart("parametres_corporels", "taille", "Distribution des Tailles", "Camembert");
        }
        
        if (availableTables.contains("dotation_particuliere")) {
            addTemporalChart("dotation_particuliere", "annee", "Dotations par Année", "Barre");
        }
        
        if (availableTables.contains("maintenance")) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                DatabaseMetaData metaData = conn.getMetaData();
                ResultSet columns = metaData.getColumns("master", null, "maintenance", "type");
                if (columns.next()) {
                    addDistributionChart("maintenance", "type", "Maintenances par Type", "Barre");
                } else {
                    columns = metaData.getColumns("master", null, "maintenance", "designation");
                    if (columns.next()) {
                        addDistributionChart("maintenance", "designation", "Maintenances par Désignation", "Barre");
                    }
                }
            }
        }
        
        if (availableTables.contains("identite_personnelle")) {
            addDistributionChart("identite_personnelle", "sexe", "Répartition par Sexe", "Camembert");
        }
    }
    
    private void createOperationsCharts() throws SQLException {
        if (availableTables.contains("operation")) {
            addDistributionChart("operation", "type", "Opérations par Type", "Barre");
            addTemporalChart("operation", "annee", "Opérations par Année", "Ligne");
        }
        
        if (availableTables.contains("personnel_naviguant")) {
            addDistributionChart("personnel_naviguant", "qualification_type", "Qualifications Personnel Naviguant", "Camembert");
        }
        
        if (availableTables.contains("langue")) {
            addDistributionChart("langue", "langue", "Langues Parlées", "Barre");
        }
        
        if (availableTables.contains("grade_actuel")) {
            addDistributionChart("grade_actuel", "rang", "Répartition par Grade", "Barre");
        }
    }
    
    private void createRhCharts() throws SQLException {
        if (availableTables.contains("grade_actuel")) {
            addDistributionChart("grade_actuel", "rang", "Répartition par Grade", "Barre");
        }
        
        if (availableTables.contains("ecole_formation_initiale")) {
            addDistributionChart("ecole_formation_initiale", "nom_ecole", "Écoles de Formation", "Camembert");
        }
        
        if (availableTables.contains("decoration")) {
            addDistributionChart("decoration", "type", "Décorations par Type", "Barre");
        }
        
        if (availableTables.contains("historique_grades")) {
            addTemporalChart("historique_grades", "YEAR(date)", "Évolution des Promotions", "Ligne");
        }
    }
    
    private void createDefaultCharts() throws SQLException {
        if (availableTables.contains("identite_personnelle")) {
            addDistributionChart("identite_personnelle", "sexe", "Répartition par Sexe", "Camembert");
        }
        
        if (availableTables.contains("identite_culturelle")) {
            addDistributionChart("identite_culturelle", "region_origine", "Répartition par Région", "Barre");
        }
        
        if (availableTables.contains("grade_actuel")) {
            addDistributionChart("grade_actuel", "rang", "Répartition par Grade", "Barre");
        }
    }
    
    private void addDistributionChart(String tableName, String column, String title, String chartType) throws SQLException {
        Map<String, Integer> data = getDistributionData(tableName, column);
        
        if (data.isEmpty()) {
            return;
        }
        
        Platform.runLater(() -> {
            try {
                Chart chart = null;
                switch (chartType) {
                    case "Camembert":
                        chart = createEnhancedPieChart(data, title);
                        break;
                    case "Barre":
                        chart = createEnhancedBarChart(data, title, column);
                        break;
                    default:
                        chart = createEnhancedBarChart(data, title, column);
                }
                
                if (chart != null) {
                    addChartToContainer(chart, tableName, column); // Version avec paramètres
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private void addTemporalChart(String tableName, String timeColumn, String title, String chartType) throws SQLException {
        Map<String, Integer> data = getTemporalData(tableName, timeColumn);
        
        if (data.isEmpty()) {
            return;
        }
        
        Platform.runLater(() -> {
            try {
                Chart chart = null;
                switch (chartType) {
                    case "Ligne":
                        chart = createEnhancedLineChart(data, title, timeColumn);
                        break;
                    case "Barre":
                        chart = createEnhancedBarChart(data, title, timeColumn);
                        break;
                    default:
                        chart = createEnhancedLineChart(data, title, timeColumn);
                }
                
                if (chart != null) {
                    addChartToContainer(chart, tableName, timeColumn); // Version avec paramètres
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private Map<String, Integer> getDistributionData(String tableName, String column) throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT " + column + ", COUNT(*) as count FROM " + tableName + 
                      " WHERE " + column + " IS NOT NULL AND " + column + " != '' " +
                      " GROUP BY " + column + " ORDER BY count DESC LIMIT 10";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String key = rs.getString(column);
                int count = rs.getInt("count");
                if (key != null && !key.trim().isEmpty()) {
                    data.put(key, count);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des données pour " + tableName + "." + column + ": " + e.getMessage());
        }
        
        return data;
    }
    
    private Map<String, Integer> getTemporalData(String tableName, String timeColumn) throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT " + timeColumn + " as time_period, COUNT(*) as count FROM " + tableName + 
                      " WHERE " + timeColumn + " IS NOT NULL " +
                      " GROUP BY " + timeColumn + " ORDER BY time_period";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String period = rs.getString("time_period");
                int count = rs.getInt("count");
                if (period != null) {
                    data.put(period, count);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des données temporelles pour " + tableName + "." + timeColumn + ": " + e.getMessage());
        }
        
        return data;
    }
    
    /**
     * Crée un graphique en camembert amélioré avec légende visible
     */
    private PieChart createEnhancedPieChart(Map<String, Integer> data, String title) {
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        
        int colorIndex = 0;
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            PieChart.Data slice = new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue());
            pieChartData.add(slice);
            colorIndex++;
        }
        
        PieChart chart = new PieChart(pieChartData);
        
        // Configuration améliorée
        chart.setTitle(title);
        chart.setLabelsVisible(true);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.RIGHT);
        chart.setStartAngle(90);
        chart.setClockwise(true);
        
        // Taille optimisée
        chart.setPrefSize(450, 380);
        chart.setMinSize(400, 350);
        chart.setMaxSize(500, 420);
        
        // Style amélioré
        chart.setStyle("-fx-background-color: white; -fx-border-color: #e8e9ea; -fx-border-width: 1px; -fx-border-radius: 8px;");
        
        // Personnaliser les couleurs après ajout à la scène
        Platform.runLater(() -> {
            int index = 0;
            for (PieChart.Data slice : chart.getData()) {
                if (index < CHART_COLORS.length) {
                    slice.getNode().setStyle("-fx-pie-color: " + CHART_COLORS[index % CHART_COLORS.length] + ";");
                }
                index++;
            }
        });
        
        return chart;
    }
    
    /**
     * Crée un graphique en barres amélioré avec axes visibles
     */
    private BarChart<String, Number> createEnhancedBarChart(Map<String, Integer> data, String title, String xAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        // Configuration des axes
        xAxis.setLabel(xAxisLabel);
        xAxis.setTickLabelRotation(45);
        xAxis.setTickLabelGap(10);
        xAxis.setTickMarkVisible(true);
        
        yAxis.setLabel("Nombre");
        yAxis.setAutoRanging(true);
        yAxis.setTickMarkVisible(true);
        yAxis.setMinorTickVisible(true);
        yAxis.setForceZeroInRange(true);
        
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle(title);
        barChart.setLegendVisible(false); // Masquer la légende pour les graphiques en barres simples
        barChart.setCategoryGap(20);
        barChart.setBarGap(5);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Données");
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(entry.getKey(), entry.getValue());
            series.getData().add(dataPoint);
        }
        
        barChart.getData().add(series);
        
        // Taille optimisée
        barChart.setPrefSize(500, 380);
        barChart.setMinSize(450, 350);
        barChart.setMaxSize(550, 420);
        
        // Style amélioré
        barChart.setStyle("-fx-background-color: white; -fx-border-color: #e8e9ea; -fx-border-width: 1px; -fx-border-radius: 8px;");
        
        // Personnaliser les couleurs des barres
        Platform.runLater(() -> {
            int index = 0;
            for (XYChart.Data<String, Number> item : series.getData()) {
                String color = CHART_COLORS[index % CHART_COLORS.length];
                item.getNode().setStyle("-fx-bar-fill: " + color + ";");
                index++;
            }
        });
        
        return barChart;
    }
    
    /**
     * Crée un graphique en ligne amélioré avec axes visibles
     */
    private LineChart<String, Number> createEnhancedLineChart(Map<String, Integer> data, String title, String xAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        // Configuration des axes
        xAxis.setLabel(xAxisLabel);
        xAxis.setTickLabelRotation(45);
        xAxis.setTickLabelGap(10);
        xAxis.setTickMarkVisible(true);
        
        yAxis.setLabel("Nombre");
        yAxis.setAutoRanging(true);
        yAxis.setTickMarkVisible(true);
        yAxis.setMinorTickVisible(true);
        yAxis.setForceZeroInRange(true);
        
        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle(title);
        lineChart.setLegendVisible(true);
        lineChart.setLegendSide(Side.TOP);
        lineChart.setCreateSymbols(true);
        lineChart.setHorizontalGridLinesVisible(true);
        lineChart.setVerticalGridLinesVisible(true);
        lineChart.setHorizontalZeroLineVisible(true);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Évolution");
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(entry.getKey(), entry.getValue());
            series.getData().add(dataPoint);
        }
        
        lineChart.getData().add(series);
        
        // Taille optimisée
        lineChart.setPrefSize(500, 380);
        lineChart.setMinSize(450, 350);
        lineChart.setMaxSize(550, 420);
        
        // Style amélioré
        lineChart.setStyle("-fx-background-color: white; -fx-border-color: #e8e9ea; -fx-border-width: 1px; -fx-border-radius: 8px;");
        
        // Personnaliser la couleur de la ligne
        Platform.runLater(() -> {
            series.getNode().setStyle("-fx-stroke: " + CHART_COLORS[0] + "; -fx-stroke-width: 3px;");
        });
        
        return lineChart;
    }

    @FXML
    private void showCustomizeDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Personnaliser le Dashboard - " + currentService);
        dialog.setHeaderText("Gérer les graphiques du dashboard");

        VBox content = new VBox(15);
        content.setPadding(new Insets(25));
        content.getStyleClass().add("customize-dialog");
        content.setStyle("-fx-background-color: white;");
        
        // Informations sur le service avec style amélioré
        VBox serviceInfo = new VBox(8);
        Label serviceLabel = new Label("Service actuel: " + currentService);
        serviceLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50;");
        
        Label tablesLabel = new Label("Tables disponibles: " + String.join(", ", availableTables));
        tablesLabel.setWrapText(true);
        tablesLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");
        
        serviceInfo.getChildren().addAll(serviceLabel, tablesLabel);
        content.getChildren().addAll(serviceInfo, new Separator());
        
        // Section pour les graphiques existants avec amélioration
        TitledPane existingChartsPane = new TitledPane();
        existingChartsPane.setText("📊 Graphiques existants (" + chartsContainer.getChildren().size() + ")");
        existingChartsPane.setCollapsible(false);
        
        ListView<String> existingCharts = new ListView<>();
        existingCharts.setPrefHeight(120);
        
        for (int i = 0; i < chartsContainer.getChildren().size(); i++) {
            Node chartNode = chartsContainer.getChildren().get(i);
            if (chartNode instanceof VBox) {
                VBox chartBox = (VBox) chartNode;
                if (!chartBox.getChildren().isEmpty() && chartBox.getChildren().get(0) instanceof Chart) {
                    Chart chart = (Chart) chartBox.getChildren().get(0);
                    existingCharts.getItems().add((i + 1) + ". " + chart.getTitle());
                }
            } else if (chartNode instanceof Chart) {
                Chart chart = (Chart) chartNode;
                existingCharts.getItems().add((i + 1) + ". " + chart.getTitle());
            }
        }
        
        HBox chartManagementButtons = new HBox(10);
        chartManagementButtons.setAlignment(Pos.CENTER);
        
        Button removeChartBtn = new Button("🗑️ Supprimer sélectionné");
        removeChartBtn.getStyleClass().addAll("button", "delete-button");
        
        Button refreshChartsBtn = new Button("🔄 Actualiser tous");
        refreshChartsBtn.getStyleClass().addAll("button", "primary-button");
        
        Button clearAllBtn = new Button("🆑 Vider dashboard");
        clearAllBtn.getStyleClass().addAll("button", "delete-button");
        
        chartManagementButtons.getChildren().addAll(removeChartBtn, refreshChartsBtn, clearAllBtn);
        
        VBox existingChartsBox = new VBox(10, existingCharts, chartManagementButtons);
        existingChartsPane.setContent(existingChartsBox);
        
        // Section pour ajouter un nouveau graphique avec amélioration
        TitledPane addChartPane = new TitledPane();
        addChartPane.setText("➕ Ajouter un nouveau graphique");
        addChartPane.setCollapsible(false);
        
        GridPane addChartGrid = new GridPane();
        addChartGrid.setHgap(15);
        addChartGrid.setVgap(12);
        addChartGrid.setPadding(new Insets(15));
        addChartGrid.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8px;");
        
        // Sélection de table améliorée
        Label tableLabel = new Label("Table:");
        tableLabel.setStyle("-fx-font-weight: bold;");
        ComboBox<String> tableSelect = new ComboBox<>();
        tableSelect.getItems().addAll(availableTables);
        tableSelect.setPromptText("Sélectionner une table...");
        tableSelect.setPrefWidth(200);
        
        // Sélection de colonne améliorée
        Label columnLabel = new Label("Colonne:");
        columnLabel.setStyle("-fx-font-weight: bold;");
        ComboBox<String> columnSelect = new ComboBox<>();
        columnSelect.setPromptText("Sélectionner une colonne...");
        columnSelect.setPrefWidth(200);
        
        // Sélection de type améliorée
        Label chartTypeLabel = new Label("Type de graphique:");
        chartTypeLabel.setStyle("-fx-font-weight: bold;");
        ComboBox<String> chartTypeSelect = new ComboBox<>();
        chartTypeSelect.getItems().addAll("📊 Camembert", "📈 Barre", "📉 Ligne");
        chartTypeSelect.setPromptText("Type de graphique...");
        chartTypeSelect.setPrefWidth(200);
        
        // Titre personnalisé amélioré
        Label titleLabel = new Label("Titre personnalisé:");
        titleLabel.setStyle("-fx-font-weight: bold;");
        TextField titleField = new TextField();
        titleField.setPromptText("Titre du graphique (optionnel)");
        titleField.setPrefWidth(200);
        
        Button addChartBtn = new Button("✅ Créer le graphique");
        addChartBtn.getStyleClass().addAll("button", "primary-button");
        addChartBtn.setPrefWidth(200);
        
        // Arrangement de la grille
        addChartGrid.add(tableLabel, 0, 0);
        addChartGrid.add(tableSelect, 1, 0);
        addChartGrid.add(columnLabel, 0, 1);
        addChartGrid.add(columnSelect, 1, 1);
        addChartGrid.add(chartTypeLabel, 0, 2);
        addChartGrid.add(chartTypeSelect, 1, 2);
        addChartGrid.add(titleLabel, 0, 3);
        addChartGrid.add(titleField, 1, 3);
        addChartGrid.add(addChartBtn, 1, 4);
        
        addChartPane.setContent(addChartGrid);
        
        content.getChildren().addAll(existingChartsPane, addChartPane);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(600, 500);
        
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        // Gestionnaires d'événements avec amélioration
        
        // Écouteur pour charger les colonnes quand une table est sélectionnée
        tableSelect.setOnAction(e -> {
            String selectedTable = tableSelect.getValue();
            if (selectedTable != null) {
                loadColumnsForTable(selectedTable, columnSelect);
            }
        });
        
        // Gestionnaire d'événement pour le bouton d'ajout
        addChartBtn.setOnAction(e -> {
            String table = tableSelect.getValue();
            String column = columnSelect.getValue();
            String chartTypeRaw = chartTypeSelect.getValue();
            String title = titleField.getText();
            
            if (table != null && column != null && chartTypeRaw != null) {
                // Extraire le type réel du graphique
                String chartType = "Barre";
                if (chartTypeRaw.contains("Camembert")) chartType = "Camembert";
                else if (chartTypeRaw.contains("Ligne")) chartType = "Ligne";
                
                if (title == null || title.trim().isEmpty()) {
                    title = "Graphique " + column + " (" + table + ")";
                }
                
                try {
                    addDistributionChart(table, column, title, chartType);
                    HistoryManager.logCreation("Dashboard", 
                            "Ajout d'un graphique personnalisé - Service: " + currentService);
                    
                    // Réinitialiser les champs
                    tableSelect.setValue(null);
                    columnSelect.getItems().clear();
                    chartTypeSelect.setValue(null);
                    titleField.clear();
                    
                    showInformation("Succès", "Graphique ajouté avec succès!");
                    
                } catch (SQLException ex) {
                    showErrorAlert("Erreur d'ajout de graphique", ex.getMessage());
                }
            } else {
                showErrorAlert("Paramètres incomplets", "Veuillez sélectionner tous les paramètres requis");
            }
        });

        // Gestionnaire pour le bouton de suppression
        removeChartBtn.setOnAction(e -> {
            int selectedIndex = existingCharts.getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < chartsContainer.getChildren().size()) {
                String chartTitle = existingCharts.getItems().get(selectedIndex);
                chartsContainer.getChildren().remove(selectedIndex);
                existingCharts.getItems().remove(selectedIndex);
                HistoryManager.logDeletion("Dashboard", 
                        "Suppression du graphique: " + chartTitle + " - Service: " + currentService);
                showInformation("Succès", "Graphique supprimé avec succès!");
            } else {
                showErrorAlert("Sélection requise", "Veuillez sélectionner un graphique à supprimer");
            }
        });
        
        // Gestionnaire pour actualiser tous les graphiques
        refreshChartsBtn.setOnAction(e -> {
            try {
                createServiceSpecificCharts();
                dialog.close();
                HistoryManager.logUpdate("Dashboard", 
                        "Actualisation complète des graphiques - Service: " + currentService);
                showInformation("Succès", "Dashboard actualisé avec succès!");
            } catch (SQLException ex) {
                showErrorAlert("Erreur d'actualisation", ex.getMessage());
            }
        });
        
        // Gestionnaire pour vider le dashboard
        clearAllBtn.setOnAction(e -> {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Confirmation");
            confirmAlert.setHeaderText("Vider le dashboard");
            confirmAlert.setContentText("Êtes-vous sûr de vouloir supprimer tous les graphiques?");
            
            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                chartsContainer.getChildren().clear();
                existingCharts.getItems().clear();
                HistoryManager.logDeletion("Dashboard", 
                        "Suppression complète des graphiques - Service: " + currentService);
                showInformation("Succès", "Dashboard vidé avec succès!");
            }
        });

        dialog.showAndWait();
    }
    
    private void loadColumnsForTable(String tableName, ComboBox<String> columnSelect) {
        columnSelect.getItems().clear();
        List<String> columns = TableColumnManager.getColumnsForTable(tableName);
        
        // Filtrer les colonnes appropriées pour les graphiques
        List<String> graphableColumns = columns.stream()
                .filter(col -> !col.toLowerCase().equals("id") && !col.toLowerCase().startsWith("id_"))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        columnSelect.getItems().addAll(graphableColumns);
    }

    /**
     * Ajoute un graphique au conteneur avec style amélioré
     */
    private void addChartToContainer(Chart chart, String tableName, String column) {
        if (chart == null) return;
        
        // Créer le conteneur amélioré
        VBox chartContainer = new VBox(8);
        chartContainer.getStyleClass().add("enhanced-chart-container");
        chartContainer.setAlignment(Pos.CENTER);
        
        // Style amélioré
        chartContainer.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #dee2e6;" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 12px;" +
            "-fx-background-radius: 12px;" +
            "-fx-padding: 15px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 3);"
        );
        
        // Configuration du graphique
        chart.setPrefSize(450, 350);
        chart.setMinSize(400, 320);
        chart.setMaxSize(500, 380);
        chart.setStyle("-fx-background-color: transparent;");
        
        // Ajouter tooltip et menu contextuel si les paramètres sont fournis
        if (tableName != null && column != null) {
            addTooltipToChart(chart, tableName, column);
        }
        
        chartContainer.getChildren().add(chart);
        
        // Limiter le nombre de graphiques
        if (chartsContainer.getChildren().size() < 8) {
            chartsContainer.getChildren().add(chartContainer);
            updateEmptyState();
        } else {
            showErrorAlert("Limite atteinte", 
                "Vous avez atteint le nombre maximum de graphiques (8) pour une meilleure lisibilité.\n" +
                "Supprimez un graphique existant pour en ajouter un nouveau.");
        }
    }
    
    /**
     * Version surchargée pour compatibilité avec le code existant
     */
    private void addChartToContainer(Chart chart) {
        addChartToContainer(chart, null, null);
    }
    
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showInformation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}