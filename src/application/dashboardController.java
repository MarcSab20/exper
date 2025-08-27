package application;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.FXCollections;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Service;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.FileChooser;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller moderne et optimisé pour le dashboard - VERSION CORRIGÉE
 */
public class dashboardController {
    private static final Logger LOGGER = Logger.getLogger(dashboardController.class.getName());
    
    // Composants FXML
    @FXML private FlowPane chartsContainer;
    @FXML private Label personnelTotalCount, personnelOfficiersCount, personnelFemininCount, formationsCount;
    @FXML private Label personnelTotalLabel, personnelOfficiersLabel, personnelFemininLabel, formationsLabel;
    @FXML private Label serviceInfoLabel;
    @FXML private VBox emptyStateMessage;
    @FXML private Label lastUpdateTime;
    @FXML private Label chartsCount;
    
    // Services
    private final DashboardDataService dataService = new DashboardDataService();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    
    // État du dashboard
    private String currentService;
    private List<String> availableTables;
    private List<ChartPersistenceService.ChartConfig> currentChartConfigs = new ArrayList<>();
    private Map<String, Chart> activeCharts = new LinkedHashMap<>();
    
    // CORRECTION: Variables pour la mise à jour automatique
    private boolean isLoading = false;
    private boolean isInitialized = false;
    private long lastDataCheckTime = 0;
    
    @FXML
    public void initialize() {
        LOGGER.info("=== INITIALISATION DASHBOARD MODERNE CORRIGÉ ===");
        
        try {
            // 1. Initialiser les informations de base
            initializeBasicInfo();
            
            // 2. Configurer l'interface utilisateur
            setupUI();
            
            // 3. CORRECTION: Charger les données de manière asynchrone avec callback
            loadDashboardDataAsync(() -> {
                // 4. Programmer les mises à jour automatiques après le chargement initial
                scheduleAutoRefresh();
                isInitialized = true;
                LOGGER.info("Dashboard moderne initialisé avec succès");
            });
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation du dashboard", e);
            showErrorState("Erreur d'initialisation: " + e.getMessage());
        }
    }
    
    /**
     * Initialise les informations de base
     */
    private void initializeBasicInfo() {
        currentService = UserSession.getCurrentService();
        availableTables = ServicePermissions.getTablesForService(currentService);
        
        LOGGER.info("Service: " + currentService);
        LOGGER.info("Tables disponibles: " + availableTables.size());
        
        if (serviceInfoLabel != null) {
            serviceInfoLabel.setText(String.format("Service: %s | %d tables disponibles", 
                                                 currentService, availableTables.size()));
        }
    }
    
    /**
     * Configure l'interface utilisateur
     */
    private void setupUI() {
        if (chartsContainer != null) {
            chartsContainer.getChildren().clear(); // CORRECTION: Vider le conteneur au départ
            chartsContainer.setHgap(25);
            chartsContainer.setVgap(25);
            chartsContainer.setPadding(new Insets(20));
            chartsContainer.setAlignment(Pos.TOP_CENTER);
            chartsContainer.setPrefWrapLength(Region.USE_COMPUTED_SIZE);
        }
        
        // Configurer les labels des statistiques
        setupStatsLabels();
        
        // Masquer le message vide initialement
        if (emptyStateMessage != null) {
            emptyStateMessage.setVisible(false);
            emptyStateMessage.setManaged(false);
        }
        
        updateLastRefreshTime();
    }
    
    /**
     * Configure les labels des statistiques
     */
    private void setupStatsLabels() {
        if (personnelTotalLabel != null) personnelTotalLabel.setText("Personnel Total");
        if (personnelOfficiersLabel != null) personnelOfficiersLabel.setText("Personnel Officiers");
        if (personnelFemininLabel != null) personnelFemininLabel.setText("Personnel Féminin");
        if (formationsLabel != null) formationsLabel.setText("Formations");
        
        // Valeurs par défaut pendant le chargement
        if (personnelTotalCount != null) personnelTotalCount.setText("...");
        if (personnelOfficiersCount != null) personnelOfficiersCount.setText("...");
        if (personnelFemininCount != null) personnelFemininCount.setText("...");
        if (formationsCount != null) formationsCount.setText("...");
    }
    
    /**
     * CORRECTION: Charge les données du dashboard avec callback
     */
    private void loadDashboardDataAsync(Runnable onComplete) {
        if (isLoading) return;
        isLoading = true;
        
        showLoadingIndicator(true);
        
        CompletableFuture<Void> statsTask = CompletableFuture.runAsync(() -> {
            try {
                DashboardDataService.DashboardStats stats = dataService.getMainStats();
                Platform.runLater(() -> updateStatsDisplay(stats));
            } catch (SQLException e) {
                Platform.runLater(() -> showStatsError());
                LOGGER.log(Level.SEVERE, "Erreur lors du chargement des statistiques", e);
            }
        });
        
        CompletableFuture<Void> chartsTask = CompletableFuture.runAsync(() -> {
            try {
                // CORRECTION: D'abord charger les configurations
                List<ChartPersistenceService.ChartConfig> configs = 
                    ChartPersistenceService.loadChartConfigs(currentService);
                
                // Si aucune configuration n'existe, créer les graphiques par défaut
                if (configs.isEmpty()) {
                    LOGGER.info("Aucune configuration trouvée, création des graphiques par défaut");
                    Platform.runLater(() -> {
                        createAndSaveDefaultCharts();
                        // Recharger après avoir créé les défauts
                        CompletableFuture.runAsync(() -> {
                            try {
                                List<ChartPersistenceService.ChartConfig> newConfigs = 
                                    ChartPersistenceService.loadChartConfigs(currentService);
                                Platform.runLater(() -> loadChartsFromConfigs(newConfigs));
                            } catch (Exception e) {
                                LOGGER.log(Level.SEVERE, "Erreur rechargement après défauts", e);
                            }
                        });
                    });
                } else {
                    Platform.runLater(() -> loadChartsFromConfigs(configs));
                }
                
            } catch (Exception e) {
                Platform.runLater(() -> showChartsError());
                LOGGER.log(Level.SEVERE, "Erreur lors du chargement des graphiques", e);
            }
        });
        
        CompletableFuture.allOf(statsTask, chartsTask).whenComplete((result, throwable) -> {
            Platform.runLater(() -> {
                isLoading = false;
                showLoadingIndicator(false);
                updateEmptyState();
                updateLastRefreshTime();
                lastDataCheckTime = dataService.getCurrentTimestamp();
                
                if (throwable != null) {
                    LOGGER.log(Level.SEVERE, "Erreur lors du chargement complet", throwable);
                }
                
                // Appeler le callback
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }
    
    /**
     * CORRECTION: Version surchargée sans callback pour les rafraîchissements
     */
    private void loadDashboardDataAsync() {
        loadDashboardDataAsync(null);
    }
    
    /**
     * CORRECTION: Met à jour l'affichage des statistiques
     */
    private void updateStatsDisplay(DashboardDataService.DashboardStats stats) {
        if (personnelTotalCount != null) {
            personnelTotalCount.setText(String.valueOf(stats.getPersonnelTotal()));
        }
        if (personnelOfficiersCount != null) {
            personnelOfficiersCount.setText(String.valueOf(stats.getPersonnelOfficiers()));
        }
        if (personnelFemininCount != null) {
            personnelFemininCount.setText(String.valueOf(stats.getPersonnelFeminin()));
        }
        if (formationsCount != null) {
            formationsCount.setText(String.valueOf(stats.getNombreFormations()));
        }
        
        LOGGER.info("Statistiques mises à jour: " + stats);
    }
    
    /**
     * CORRECTION: Crée et sauvegarde les graphiques par défaut
     */
    private void createAndSaveDefaultCharts() {
        LOGGER.info("Création des graphiques par défaut pour le service: " + currentService);
        
        // Créer les configurations par défaut selon vos spécifications
        List<ChartPersistenceService.ChartConfig> defaultConfigs = Arrays.asList(
            new ChartPersistenceService.ChartConfig("default_sexe", "camembert", "Répartition par Sexe", 
                          "identite_personnelle", "sexe", null, null, false, true, 1),
            new ChartPersistenceService.ChartConfig("default_grade", "histogramme", "Répartition par Grade", 
                          "grade_actuel", "rang", null, null, false, true, 2),
            new ChartPersistenceService.ChartConfig("default_region", "camembert", "Répartition par Région", 
                          "identite_culturelle", "region_origine", null, null, false, true, 3),
            new ChartPersistenceService.ChartConfig("default_religion", "histogramme", "Répartition par Religion", 
                          "identite_culturelle", "religion", null, null, false, true, 4)
        );
        
        // Sauvegarder chaque configuration
        for (ChartPersistenceService.ChartConfig config : defaultConfigs) {
            ChartPersistenceService.saveChartConfig(currentService, config);
        }
        
        LOGGER.info("Graphiques par défaut créés et sauvegardés");
    }
    
    /**
     * CORRECTION: Charge les graphiques à partir des configurations
     */
    private void loadChartsFromConfigs(List<ChartPersistenceService.ChartConfig> configs) {
        if (chartsContainer == null) return;
        
        // CORRECTION: Vider proprement le conteneur
        chartsContainer.getChildren().clear();
        activeCharts.clear();
        currentChartConfigs = new ArrayList<>(configs);
        
        if (configs.isEmpty()) {
            LOGGER.warning("Aucune configuration de graphique à charger");
            updateEmptyState();
            updateChartsCount();
            return;
        }
        
        LOGGER.info("Chargement de " + configs.size() + " graphiques configurés");
        
        // CORRECTION: Traiter les graphiques de manière séquentielle pour éviter les concurrences
        for (ChartPersistenceService.ChartConfig config : configs) {
            createAndAddChartFromConfig(config);
        }
        
        updateChartsCount();
    }
    
    /**
     * CORRECTION: Crée et ajoute un graphique de manière synchrone
     */
    private void createAndAddChartFromConfig(ChartPersistenceService.ChartConfig config) {
        CompletableFuture.runAsync(() -> {
            try {
                Chart chart = createChartFromConfig(config);
                if (chart != null) {
                    Platform.runLater(() -> {
                        addChartToContainer(chart, config);
                        LOGGER.fine("Graphique ajouté avec succès: " + config.getChartTitle());
                    });
                } else {
                    LOGGER.warning("Impossible de créer le graphique: " + config.getChartId());
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erreur lors de la création du graphique: " + config.getChartId(), e);
            }
        });
    }
    
    /**
     * CORRECTION: Crée un graphique à partir d'une configuration
     */
    private Chart createChartFromConfig(ChartPersistenceService.ChartConfig config) throws SQLException {
        if (config.isCrossTable()) {
            // Graphique croisé
            Map<String, Map<String, Integer>> crossData = dataService.getCrossTableData(
                config.getTableName(), config.getColumnName(),
                config.getTableName2(), config.getColumnName2()
            );
            
            if (crossData.isEmpty()) {
                LOGGER.warning("Aucune donnée croisée pour: " + config.getChartId());
                return null;
            }
            
            return ChartFactory.createCrossChart(
                config.getChartType(), crossData, config.getChartTitle(),
                config.getColumnName(), config.getColumnName2()
            );
            
        } else {
            // Graphique simple
            Map<String, Integer> data;
            
            if (config.getChartId().startsWith("default_")) {
                // CORRECTION: Graphiques par défaut avec logique spéciale
                data = getDefaultChartData(config.getChartId());
            } else {
                // Graphique personnalisé
                data = dataService.getColumnData(config.getTableName(), config.getColumnName());
            }
            
            if (data.isEmpty()) {
                LOGGER.warning("Aucune donnée pour: " + config.getChartId());
                return null;
            }
            
            return ChartFactory.createChart(
                config.getChartType(), data, config.getChartTitle(), config.getColumnName()
            );
        }
    }
    
    /**
     * CORRECTION: Obtient les données pour les graphiques par défaut
     */
    private Map<String, Integer> getDefaultChartData(String chartId) throws SQLException {
        switch (chartId) {
            case "default_sexe":
                return dataService.getRepartitionParSexe();
            case "default_grade":
                return dataService.getRepartitionParGrade();
            case "default_region":
                return dataService.getRepartitionParRegion();
            case "default_religion":
                return dataService.getRepartitionParReligion();
            default:
                return new HashMap<>();
        }
    }
    
    /**
     * CORRECTION: Ajoute un graphique au conteneur avec style moderne
     */
    private void addChartToContainer(Chart chart, ChartPersistenceService.ChartConfig config) {
        if (chart == null || chartsContainer == null) return;
        
        // Créer le conteneur du graphique
        VBox chartContainer = createChartContainer(chart, config);
        
        // CORRECTION: Ajouter au conteneur principal sur le thread JavaFX
        Platform.runLater(() -> {
            chartsContainer.getChildren().add(chartContainer);
            activeCharts.put(config.getChartId(), chart);
            
            // Masquer le message vide
            if (emptyStateMessage != null) {
                emptyStateMessage.setVisible(false);
                emptyStateMessage.setManaged(false);
            }
            
            updateChartsCount();
            LOGGER.fine("Graphique ajouté à l'interface: " + config.getChartTitle());
        });
    }
    
    /**
     * Crée le conteneur stylé pour un graphique
     */
    private VBox createChartContainer(Chart chart, ChartPersistenceService.ChartConfig config) {
        VBox container = new VBox(10);
        container.getStyleClass().add("modern-chart-container");
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(20));
        
        // Style moderne
        container.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #e1e8ed;" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 15px;" +
            "-fx-background-radius: 15px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 12, 0, 0, 4);"
        );
        
        // Configurer le graphique
        chart.setPrefSize(480, 380);
        chart.setMinSize(450, 350);
        chart.setMaxSize(520, 420);
        
        // Ajouter tooltips
        ChartFactory.addTooltips(chart, config.getTableName(), config.getColumnName());
        
        // Créer le menu contextuel
        ContextMenu contextMenu = createChartContextMenu(chart, config);
        chart.setOnContextMenuRequested(e -> {
            contextMenu.show(chart, e.getScreenX(), e.getScreenY());
        });
        
        container.getChildren().add(chart);
        return container;
    }
    
    /**
     * Crée le menu contextuel pour un graphique
     */
    private ContextMenu createChartContextMenu(Chart chart, ChartPersistenceService.ChartConfig config) {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem exportItem = new MenuItem("📤 Exporter");
        exportItem.setOnAction(e -> exportChart(chart));
        
        MenuItem refreshItem = new MenuItem("🔄 Actualiser");
        refreshItem.setOnAction(e -> refreshSingleChart(config));
        
        MenuItem removeItem = new MenuItem("🗑️ Supprimer");
        removeItem.setOnAction(e -> removeSingleChart(config));
        // Désactiver la suppression pour les graphiques par défaut
        removeItem.setDisable(config.isDefault());
        
        contextMenu.getItems().addAll(exportItem, refreshItem, new SeparatorMenuItem(), removeItem);
        
        return contextMenu;
    }
    
    /**
     * Exporte un graphique
     */
    private void exportChart(Chart chart) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Exporter le graphique");
            fileChooser.setInitialFileName(chart.getTitle().replaceAll("[^a-zA-Z0-9]", "_") + ".png");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images PNG", "*.png")
            );
            
            java.io.File file = fileChooser.showSaveDialog(chart.getScene().getWindow());
            if (file != null) {
                WritableImage image = chart.snapshot(null, null);
                javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                
                showInformation("Export réussi", "Graphique exporté vers: " + file.getName());
                
                // Enregistrer dans l'historique
                HistoryManager.logCreation("Dashboard", 
                    "Export de graphique: " + chart.getTitle() + " - Service: " + currentService);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'export", e);
            showErrorAlert("Erreur d'export", "Impossible d'exporter le graphique: " + e.getMessage());
        }
    }
    
    /**
     * Actualise un graphique individuel
     */
    private void refreshSingleChart(ChartPersistenceService.ChartConfig config) {
        CompletableFuture.runAsync(() -> {
            try {
                Chart newChart = createChartFromConfig(config);
                if (newChart != null) {
                    Platform.runLater(() -> {
                        // Remplacer l'ancien graphique
                        removeChartFromContainer(config.getChartId());
                        addChartToContainer(newChart, config);
                        showInformation("Actualisation réussie", 
                                      "Le graphique a été actualisé avec succès.");
                    });
                }
            } catch (SQLException e) {
                Platform.runLater(() -> showErrorAlert("Erreur d'actualisation", 
                                                      "Impossible d'actualiser: " + e.getMessage()));
            }
        });
    }
    
    /**
     * CORRECTION: Supprime un graphique individuel
     */
    private void removeSingleChart(ChartPersistenceService.ChartConfig config) {
        if (config.isDefault()) {
            showErrorAlert("Suppression interdite", "Impossible de supprimer un graphique par défaut.");
            return;
        }
        
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation de suppression");
        confirmAlert.setHeaderText("Supprimer ce graphique");
        confirmAlert.setContentText("Êtes-vous sûr de vouloir supprimer '" + config.getChartTitle() + "' ?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Supprimer de l'interface
            removeChartFromContainer(config.getChartId());
            
            // Supprimer de la persistance
            ChartPersistenceService.deleteChartConfig(currentService, config.getChartId());
            
            // Mettre à jour la liste locale
            currentChartConfigs.removeIf(c -> c.getChartId().equals(config.getChartId()));
            
            // Enregistrer dans l'historique
            HistoryManager.logDeletion("Dashboard", 
                "Suppression de graphique: " + config.getChartTitle() + " - Service: " + currentService);
            
            showInformation("Suppression réussie", "Le graphique a été supprimé avec succès.");
        }
    }
    
    /**
     * CORRECTION: Supprime un graphique du conteneur
     */
    private void removeChartFromContainer(String chartId) {
        Chart chartToRemove = activeCharts.remove(chartId);
        
        if (chartToRemove != null) {
            // Trouver et supprimer le conteneur correspondant
            chartsContainer.getChildren().removeIf(node -> {
                if (node instanceof VBox) {
                    VBox container = (VBox) node;
                    if (!container.getChildren().isEmpty() && container.getChildren().get(0) instanceof Chart) {
                        Chart chart = (Chart) container.getChildren().get(0);
                        return chart == chartToRemove;
                    }
                }
                return false;
            });
        }
        
        updateEmptyState();
        updateChartsCount();
    }
    
    /**
     * CORRECTION: Met à jour le compteur de graphiques
     */
    private void updateChartsCount() {
        if (chartsCount != null) {
            Platform.runLater(() -> {
                int count = activeCharts.size();
                chartsCount.setText(count + " graphique(s) affiché(s)");
            });
        }
    }
    
    /**
     * Met à jour l'état vide du dashboard
     */
    private void updateEmptyState() {
        if (emptyStateMessage != null) {
            Platform.runLater(() -> {
                boolean isEmpty = activeCharts.isEmpty();
                emptyStateMessage.setVisible(isEmpty);
                emptyStateMessage.setManaged(isEmpty);
            });
        }
    }
    
    /**
     * Met à jour l'heure de dernière actualisation
     */
    private void updateLastRefreshTime() {
        if (lastUpdateTime != null) {
            Platform.runLater(() -> {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                lastUpdateTime.setText(now.format(formatter));
            });
        }
    }
    
    /**
     * CORRECTION: Programme l'actualisation automatique avec vérification des changements
     */
    private void scheduleAutoRefresh() {
        // Actualisation automatique toutes les 5 minutes avec vérification des changements
        scheduler.scheduleWithFixedDelay(() -> {
            if (isInitialized && !isLoading) {
                try {
                    // Vérifier si les données ont changé
                    if (dataService.hasDataChanged(lastDataCheckTime)) {
                        LOGGER.info("Changements détectés dans la base de données, actualisation du dashboard");
                        Platform.runLater(this::refreshDashboard);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Erreur lors de la vérification des changements", e);
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
        
        LOGGER.info("Actualisation automatique programmée (5 minutes)");
    }
    
    /**
     * CORRECTION: Actualise complètement le dashboard
     */
    @FXML
    public void refreshDashboard() {
        if (isLoading) return;
        
        LOGGER.info("Actualisation complète du dashboard");
        loadDashboardDataAsync();
        
        // Enregistrer l'action
        HistoryManager.logUpdate("Dashboard", 
            "Actualisation manuelle du dashboard - Service: " + currentService);
    }
    
    /**
     * Affiche le dialogue de personnalisation
     */
    @FXML
    public void showCustomizeDialog() {
        try {
            CustomizationDialogController dialog = new CustomizationDialogController(
                currentService, availableTables, this::onChartConfigurationChanged
            );
            dialog.showDialog();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'affichage du dialogue de personnalisation", e);
            showErrorAlert("Erreur", "Impossible d'ouvrir la personnalisation: " + e.getMessage());
        }
    }
    
    /**
     * CORRECTION: Callback appelé quand la configuration des graphiques change
     */
    private void onChartConfigurationChanged() {
        Platform.runLater(() -> {
            LOGGER.info("Configuration des graphiques modifiée, rechargement...");
            // Recharger complètement
            loadDashboardDataAsync();
        });
    }
    
    /**
     * Affiche l'indicateur de chargement
     */
    private void showLoadingIndicator(boolean show) {
        // Implémentation de l'indicateur de chargement
        // Peut être étendue avec une ProgressIndicator
    }
    
    /**
     * Affiche un état d'erreur pour les statistiques
     */
    private void showStatsError() {
        if (personnelTotalCount != null) personnelTotalCount.setText("Erreur");
        if (personnelOfficiersCount != null) personnelOfficiersCount.setText("Erreur");
        if (personnelFemininCount != null) personnelFemininCount.setText("Erreur");
        if (formationsCount != null) formationsCount.setText("Erreur");
    }
    
    /**
     * Affiche un état d'erreur pour les graphiques
     */
    private void showChartsError() {
        if (emptyStateMessage != null) {
            emptyStateMessage.setVisible(true);
            emptyStateMessage.setManaged(true);
        }
    }
    
    /**
     * Affiche un état d'erreur général
     */
    private void showErrorState(String message) {
        Platform.runLater(() -> {
            if (chartsContainer != null) {
                chartsContainer.getChildren().clear();
                
                VBox errorBox = new VBox(15);
                errorBox.setAlignment(Pos.CENTER);
                errorBox.setPadding(new Insets(50));
                
                Label errorIcon = new Label("⚠️");
                errorIcon.setStyle("-fx-font-size: 48px;");
                
                Label errorMessage = new Label("Erreur de chargement du dashboard");
                errorMessage.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
                
                Label errorDetails = new Label(message);
                errorDetails.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
                errorDetails.setWrapText(true);
                
                Button retryButton = new Button("🔄 Réessayer");
                retryButton.setOnAction(e -> refreshDashboard());
                
                errorBox.getChildren().addAll(errorIcon, errorMessage, errorDetails, retryButton);
                chartsContainer.getChildren().add(errorBox);
            }
        });
    }
    
    /**
     * Affiche une alerte d'erreur
     */
    private void showErrorAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Affiche une information
     */
    private void showInformation(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    /**
     * Nettoyage lors de la fermeture
     */
    public void cleanup() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
        activeCharts.clear();
        currentChartConfigs.clear();
    }
}