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
 * Controller moderne et optimisé pour le dashboard
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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // État du dashboard
    private String currentService;
    private List<String> availableTables;
    private List<ChartPersistenceService.ChartConfig> currentChartConfigs = new ArrayList<>();
    private Map<String, Chart> activeCharts = new LinkedHashMap<>();
    
    // Indicateurs de chargement
    private boolean isLoading = false;
    private boolean isInitialized = false;
    
    @FXML
    public void initialize() {
        LOGGER.info("=== INITIALISATION DASHBOARD MODERNE ===");
        
        try {
            // 1. Initialiser les informations de base
            initializeBasicInfo();
            
            // 2. Configurer l'interface utilisateur
            setupUI();
            
            // 3. Charger les données de manière asynchrone
            loadDashboardDataAsync();
            
            // 4. Programmer les mises à jour automatiques
            scheduleAutoRefresh();
            
            isInitialized = true;
            LOGGER.info("Dashboard moderne initialisé avec succès");
            
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
     * Charge les données du dashboard de manière asynchrone
     */
    private void loadDashboardDataAsync() {
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
                List<ChartPersistenceService.ChartConfig> configs = 
                    ChartPersistenceService.loadChartConfigs(currentService);
                Platform.runLater(() -> loadChartsFromConfigs(configs));
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
                
                if (throwable != null) {
                    LOGGER.log(Level.SEVERE, "Erreur lors du chargement complet", throwable);
                }
            });
        });
    }
    
    /**
     * Met à jour l'affichage des statistiques
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
     * Charge les graphiques à partir des configurations
     */
    private void loadChartsFromConfigs(List<ChartPersistenceService.ChartConfig> configs) {
        if (chartsContainer == null) return;
        
        chartsContainer.getChildren().clear();
        activeCharts.clear();
        currentChartConfigs = configs;
        
        if (configs.isEmpty()) {
            LOGGER.info("Aucune configuration de graphique trouvée, création des graphiques par défaut");
            createDefaultCharts();
            return;
        }
        
        LOGGER.info("Chargement de " + configs.size() + " graphiques configurés");
        
        for (ChartPersistenceService.ChartConfig config : configs) {
            CompletableFuture.runAsync(() -> {
                try {
                    Chart chart = createChartFromConfig(config);
                    if (chart != null) {
                        Platform.runLater(() -> addChartToContainer(chart, config));
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Erreur lors de la création du graphique: " + config.getChartId(), e);
                }
            });
        }
    }
    
    /**
     * Crée un graphique à partir d'une configuration
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
                // Graphiques par défaut avec logique spéciale
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
     * Obtient les données pour les graphiques par défaut
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
     * Crée les graphiques par défaut
     */
    private void createDefaultCharts() {
        CompletableFuture.runAsync(() -> {
            try {
                createDefaultChart("default_sexe", "pie", "Répartition par Sexe", 
                                 dataService::getRepartitionParSexe);
                createDefaultChart("default_grade", "bar", "Répartition par Grade", 
                                 dataService::getRepartitionParGrade);
                createDefaultChart("default_region", "pie", "Répartition par Région", 
                                 dataService::getRepartitionParRegion);
                createDefaultChart("default_religion", "bar", "Répartition par Religion", 
                                 dataService::getRepartitionParReligion);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erreur lors de la création des graphiques par défaut", e);
            }
        });
    }
    
    /**
     * Interface fonctionnelle pour obtenir des données
     */
    @FunctionalInterface
    private interface DataSupplier {
        Map<String, Integer> get() throws SQLException;
    }
    
    /**
     * Crée un graphique par défaut
     */
    private void createDefaultChart(String chartId, String chartType, String title, DataSupplier dataSupplier) {
        try {
            Map<String, Integer> data = dataSupplier.get();
            if (!data.isEmpty()) {
                Chart chart = ChartFactory.createChart(chartType, data, title, "");
                
                Platform.runLater(() -> {
                    ChartPersistenceService.ChartConfig config = new ChartPersistenceService.ChartConfig(
                        chartId, chartType, title, "", "", null, null, false, true, 0
                    );
                    addChartToContainer(chart, config);
                });
                
                LOGGER.info("Graphique par défaut créé: " + title);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la création du graphique par défaut: " + title, e);
        }
    }
    
    /**
     * Ajoute un graphique au conteneur avec style moderne
     */
    private void addChartToContainer(Chart chart, ChartPersistenceService.ChartConfig config) {
        if (chart == null || chartsContainer == null) return;
        
        // Créer le conteneur du graphique
        VBox chartContainer = createChartContainer(chart, config);
        
        // Ajouter au conteneur principal
        chartsContainer.getChildren().add(chartContainer);
        activeCharts.put(config.getChartId(), chart);
        
        // Masquer le message vide
        if (emptyStateMessage != null) {
            emptyStateMessage.setVisible(false);
            emptyStateMessage.setManaged(false);
        }
        
        updateChartsCount();
        
        LOGGER.fine("Graphique ajouté: " + config.getChartTitle());
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
     * Supprime un graphique individuel
     */
    private void removeSingleChart(ChartPersistenceService.ChartConfig config) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirmation de suppression");
        confirmAlert.setHeaderText("Supprimer ce graphique");
        confirmAlert.setContentText("Êtes-vous sûr de vouloir supprimer '" + config.getChartTitle() + "' ?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Supprimer de l'interface
            removeChartFromContainer(config.getChartId());
            
            // Supprimer de la persistance (sauf les graphiques par défaut)
            if (!config.isDefault()) {
                ChartPersistenceService.deleteChartConfig(currentService, config.getChartId());
            }
            
            // Enregistrer dans l'historique
            HistoryManager.logDeletion("Dashboard", 
                "Suppression de graphique: " + config.getChartTitle() + " - Service: " + currentService);
            
            showInformation("Suppression réussie", "Le graphique a été supprimé avec succès.");
        }
    }
    
    /**
     * Supprime un graphique du conteneur
     */
    private void removeChartFromContainer(String chartId) {
        activeCharts.remove(chartId);
        
        // Trouver et supprimer le conteneur correspondant
        chartsContainer.getChildren().removeIf(node -> {
            if (node instanceof VBox) {
                VBox container = (VBox) node;
                if (!container.getChildren().isEmpty() && container.getChildren().get(0) instanceof Chart) {
                    Chart chart = (Chart) container.getChildren().get(0);
                    // Identifier le graphique par son titre ou autre propriété
                    return activeCharts.values().stream().noneMatch(c -> c == chart);
                }
            }
            return false;
        });
        
        updateEmptyState();
        updateChartsCount();
    }
    
    /**
     * Met à jour le compteur de graphiques
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
            boolean isEmpty = activeCharts.isEmpty();
            emptyStateMessage.setVisible(isEmpty);
            emptyStateMessage.setManaged(isEmpty);
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
     * Programme l'actualisation automatique
     */
    private void scheduleAutoRefresh() {
        // Actualisation toutes les 10 minutes
        scheduler.scheduleWithFixedDelay(() -> {
            if (isInitialized && !isLoading) {
                Platform.runLater(this::refreshDashboard);
            }
        }, 10, 10, TimeUnit.MINUTES);
    }
    
    /**
     * Actualise complètement le dashboard
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
     * Callback appelé quand la configuration des graphiques change
     */
    private void onChartConfigurationChanged() {
        Platform.runLater(() -> {
            LOGGER.info("Configuration des graphiques modifiée, rechargement...");
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
        }
        activeCharts.clear();
        currentChartConfigs.clear();
    }
}