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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
        try {
            // Récupération du service et des tables accessibles
            currentService = UserSession.getCurrentService();
            availableTables = ServicePermissions.getTablesForService(currentService);
            
            System.out.println("=== INITIALISATION DASHBOARD ===");
            System.out.println("Service: " + currentService);
            System.out.println("Tables disponibles: " + availableTables);
            System.out.println("chartsContainer null? " + (chartsContainer == null));
            
            // Vérification de l'état du conteneur
            if (chartsContainer == null) {
                System.err.println("ERREUR CRITIQUE: chartsContainer est null!");
                return;
            }
            
            // Configuration initiale du conteneur
            Platform.runLater(() -> {
                try {
                    chartsContainer.getChildren().clear();
                    System.out.println("Conteneur vidé, enfants: " + chartsContainer.getChildren().size());
                    
                    // S'assurer que le conteneur est visible
                    chartsContainer.setVisible(true);
                    chartsContainer.setManaged(true);
                    
                    // Configuration du conteneur
                    chartsContainer.setHgap(20);
                    chartsContainer.setVgap(20);
                    chartsContainer.setPadding(new Insets(15));
                    chartsContainer.setAlignment(Pos.TOP_LEFT);
                    
                    System.out.println("Conteneur configuré");
                    
                } catch (Exception e) {
                    System.err.println("Erreur lors de la configuration du conteneur: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            // 1. Initialiser les informations de service
            initializeServiceInfo();
            
            // 2. Charger les données depuis la base de données
            loadDataFromDatabase();
            
            // 3. Mettre à jour les statistiques dans les boîtes
            updateStats();
            
            // 4. Créer les graphiques initiaux selon le service (avec délai pour s'assurer que l'UI est prête)
            Platform.runLater(() -> {
                try {
                    Thread.sleep(500); // Petit délai pour s'assurer que l'UI est complètement chargée
                    createServiceSpecificCharts();
                } catch (Exception e) {
                    System.err.println("Erreur lors de la création différée des graphiques: " + e.getMessage());
                }
            });
            
            // 5. Finaliser l'initialisation
            finalizeInitialization();
            
            System.out.println("Dashboard initialisé avec succès pour le service: " + currentService);
            
        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de l'initialisation du dashboard: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Erreur de connexion à la base de données", 
                    "Impossible de charger les données du dashboard.\n" + 
                    "Erreur: " + e.getMessage());
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
                
                // CORRECTION: Forcer le rafraîchissement du conteneur
                if (chartsContainer != null) {
                    chartsContainer.layout();
                    chartsContainer.autosize();
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
    
    @FXML
    private void showEnhancedCustomizeDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Personnaliser le Dashboard - " + currentService);
        dialog.setHeaderText("Gérer les graphiques du dashboard (Graphiques simples et croisés)");

        VBox content = new VBox(15);
        content.setPadding(new Insets(25));
        content.setStyle("-fx-background-color: white;");
        
        // Section pour les graphiques existants (identique)
        // ... [même code que showCustomizeDialog() pour les graphiques existants] ...
        
        // NOUVELLE SECTION: Graphiques croisés multi-tables
        TitledPane crossTablePane = new TitledPane();
        crossTablePane.setText("🔗 Créer un graphique croisé (Multi-tables)");
        crossTablePane.setCollapsible(false);
        
        GridPane crossTableGrid = new GridPane();
        crossTableGrid.setHgap(15);
        crossTableGrid.setVgap(12);
        crossTableGrid.setPadding(new Insets(15));
        crossTableGrid.setStyle("-fx-background-color: #f0f8ff; -fx-background-radius: 8px;");
        
        // Première table et colonne
        Label table1Label = new Label("Table 1:");
        table1Label.setStyle("-fx-font-weight: bold;");
        ComboBox<String> table1Select = new ComboBox<>();
        table1Select.getItems().addAll(availableTables);
        table1Select.setPromptText("Première table...");
        table1Select.setPrefWidth(180);
        
        Label column1Label = new Label("Colonne 1:");
        column1Label.setStyle("-fx-font-weight: bold;");
        ComboBox<String> column1Select = new ComboBox<>();
        column1Select.setPromptText("Première colonne...");
        column1Select.setPrefWidth(180);
        
        // Deuxième table et colonne
        Label table2Label = new Label("Table 2:");
        table2Label.setStyle("-fx-font-weight: bold;");
        ComboBox<String> table2Select = new ComboBox<>();
        table2Select.getItems().addAll(availableTables);
        table2Select.setPromptText("Deuxième table...");
        table2Select.setPrefWidth(180);
        
        Label column2Label = new Label("Colonne 2:");
        column2Label.setStyle("-fx-font-weight: bold;");
        ComboBox<String> column2Select = new ComboBox<>();
        column2Select.setPromptText("Deuxième colonne...");
        column2Select.setPrefWidth(180);
        
        // Type de graphique croisé
        Label crossChartTypeLabel = new Label("Type de croisement:");
        crossChartTypeLabel.setStyle("-fx-font-weight: bold;");
        ComboBox<String> crossChartTypeSelect = new ComboBox<>();
        crossChartTypeSelect.getItems().addAll(
            "📊 Tableau croisé dynamique",
            "🔥 Carte de chaleur",
            "📈 Graphique à barres groupées",
            "⚡ Nuage de points"
        );
        crossChartTypeSelect.setPromptText("Type de croisement...");
        crossChartTypeSelect.setPrefWidth(220);
        
        // Titre personnalisé
        Label crossTitleLabel = new Label("Titre du graphique:");
        crossTitleLabel.setStyle("-fx-font-weight: bold;");
        TextField crossTitleField = new TextField();
        crossTitleField.setPromptText("Titre du graphique croisé (optionnel)");
        crossTitleField.setPrefWidth(220);
        
        Button createCrossChartBtn = new Button("✅ Créer le graphique croisé");
        createCrossChartBtn.getStyleClass().addAll("button", "primary-button");
        createCrossChartBtn.setPrefWidth(220);
        
        // Arrangement de la grille croisée
        crossTableGrid.add(table1Label, 0, 0);
        crossTableGrid.add(table1Select, 1, 0);
        crossTableGrid.add(column1Label, 2, 0);
        crossTableGrid.add(column1Select, 3, 0);
        
        crossTableGrid.add(table2Label, 0, 1);
        crossTableGrid.add(table2Select, 1, 1);
        crossTableGrid.add(column2Label, 2, 1);
        crossTableGrid.add(column2Select, 3, 1);
        
        crossTableGrid.add(crossChartTypeLabel, 0, 2);
        crossTableGrid.add(crossChartTypeSelect, 1, 2);
        crossTableGrid.add(crossTitleLabel, 2, 2);
        crossTableGrid.add(crossTitleField, 3, 2);
        
        crossTableGrid.add(createCrossChartBtn, 1, 3, 2, 1);
        
        crossTablePane.setContent(crossTableGrid);
        
        // Gestionnaires d'événements pour les ComboBox
        table1Select.setOnAction(e -> {
            String selectedTable = table1Select.getValue();
            if (selectedTable != null) {
                loadColumnsForTable(selectedTable, column1Select);
            }
        });
        
        table2Select.setOnAction(e -> {
            String selectedTable = table2Select.getValue();
            if (selectedTable != null) {
                loadColumnsForTable(selectedTable, column2Select);
            }
        });
        
        // Gestionnaire pour créer le graphique croisé
        createCrossChartBtn.setOnAction(e -> {
        	String table1 = table1Select.getValue();
            String column1 = column1Select.getValue();
            String table2 = table2Select.getValue();
            String column2 = column2Select.getValue();
            String crossTypeRaw = crossChartTypeSelect.getValue();
            String title = crossTitleField.getText();
            
            handleCrossChartCreation(table1, column1, table2, column2, crossTypeRaw, title);
            
        });
        
        content.getChildren().add(crossTablePane);
        
        // Reste du dialogue...
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(800, 700); // Taille agrandie pour le nouveau contenu
        
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.showAndWait();
    }
    
    private void showTableDiagnosticDialog() {
        Dialog<ButtonType> diagnosticDialog = new Dialog<>();
        diagnosticDialog.setTitle("Diagnostic des Tables");
        diagnosticDialog.setHeaderText("Diagnostic de compatibilité pour graphiques croisés");
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        HBox tableSelectionBox = new HBox(10);
        tableSelectionBox.setAlignment(Pos.CENTER_LEFT);
        
        Label table1Label = new Label("Table 1:");
        ComboBox<String> diagTable1 = new ComboBox<>();
        diagTable1.getItems().addAll(availableTables);
        
        Label table2Label = new Label("Table 2:");
        ComboBox<String> diagTable2 = new ComboBox<>();
        diagTable2.getItems().addAll(availableTables);
        
        Button diagnoseBtn = new Button("🔍 Diagnostiquer");
        
        tableSelectionBox.getChildren().addAll(table1Label, diagTable1, table2Label, diagTable2, diagnoseBtn);
        
        TextArea resultArea = new TextArea();
        resultArea.setPrefHeight(400);
        resultArea.setEditable(false);
        resultArea.setStyle("-fx-font-family: monospace;");
        
        diagnoseBtn.setOnAction(e -> {
            String t1 = diagTable1.getValue();
            String t2 = diagTable2.getValue();
            
            if (t1 != null && t2 != null) {
                resultArea.clear();
                
                // Rediriger la sortie système vers le TextArea
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;
                
                try {
                    System.setOut(new PrintStream(baos));
                    System.setErr(new PrintStream(baos));
                    
                    diagnoseCrossTableCompatibility(t1, t2);
                    testSimpleCrossQuery(t1, t2);
                    
                    resultArea.setText(baos.toString());
                    
                } catch (Exception ex) {
                    resultArea.setText("Erreur lors du diagnostic: " + ex.getMessage());
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
            } else {
                resultArea.setText("Veuillez sélectionner deux tables.");
            }
        });
        
        content.getChildren().addAll(tableSelectionBox, new Label("Résultats du diagnostic:"), resultArea);
        
        diagnosticDialog.getDialogPane().setContent(content);
        diagnosticDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        diagnosticDialog.getDialogPane().setPrefWidth(800);
        diagnosticDialog.getDialogPane().setPrefHeight(600);
        
        diagnosticDialog.showAndWait();
    }

    // NOUVELLE FONCTION 2: Extraction du type de graphique croisé
    private String extractCrossChartType(String rawType) {
        if (rawType.contains("Tableau croisé")) return "pivot";
        if (rawType.contains("Carte de chaleur")) return "heatmap";
        if (rawType.contains("barres groupées")) return "grouped_bar";
        if (rawType.contains("Nuage de points")) return "scatter";
        return "pivot";
    }

    // NOUVELLE FONCTION 3: Création de graphiques croisés
    private void createCrossTableChart(String table1, String column1, String table2, String column2, 
            String title, String chartType) throws SQLException {
	System.out.println("Création graphique croisé: " + table1 + "." + column1 + " × " + table2 + "." + column2);
	
	try {
		// Obtenir les données croisées
		Map<String, Map<String, Integer>> crossData = getCrossTableData(table1, column1, table2, column2);
		
		if (crossData.isEmpty()) {
			// Au lieu de lancer une exception, afficher un message informatif
			Platform.runLater(() -> {
				showInformation("Aucune donnée croisée", 
				"Aucune donnée commune trouvée entre :\n" +
				"• " + table1 + "." + column1 + "\n" +
				"• " + table2 + "." + column2 + "\n\n" +
				"Vérifiez que :\n" +
				"1. Les deux tables ont des matricules en commun\n" +
				"2. Les colonnes sélectionnées contiennent des données valides\n" +
				"3. Il existe des enregistrements avec des valeurs non nulles dans les deux colonnes");
			});
			return; // Sortir sans créer de graphique
		}
	
		System.out.println("Données croisées trouvées: " + crossData.size() + " groupes");
		
		Platform.runLater(() -> {
		try {
			Chart chart = null;
			switch (chartType) {
				case "heatmap":
				  chart = createHeatmapChart(crossData, title, column1, column2);
				  break;
				case "grouped_bar":
				  chart = createGroupedBarChart(crossData, title, column1, column2);
				  break;
				case "scatter":
				  chart = createScatterChart(crossData, title, column1, column2);
				  break;
				default: // pivot
				  chart = createStackedBarChart(crossData, title, column1, column2);
			}
	
			if (chart != null) {
				addChartToContainer(chart, table1 + "×" + table2, column1 + "×" + column2);
				System.out.println("Graphique croisé créé avec succès");
			}
		} catch (Exception e) {
			System.err.println("Erreur lors de la création du graphique croisé: " + e.getMessage());
			e.printStackTrace();
			showErrorAlert("Erreur de création de graphique", 
			"Impossible de créer le graphique croisé: " + e.getMessage());
		}
	});
			
	} catch (SQLException e) {
		System.err.println("Erreur SQL lors du croisement: " + e.getMessage());
		throw new SQLException("Erreur lors de la récupération des données croisées: " + e.getMessage(), e);
	}
	}
    
 // NOUVELLE FONCTION 3: Diagnostic des tables pour graphiques croisés
    private void diagnoseCrossTableCompatibility(String table1, String table2) {
        System.out.println("=== DIAGNOSTIC COMPATIBILITÉ TABLES ===");
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            // Compter les matricules dans chaque table
            String count1Query = "SELECT COUNT(DISTINCT matricule) as count FROM " + table1 + " WHERE matricule IS NOT NULL";
            String count2Query = "SELECT COUNT(DISTINCT matricule) as count FROM " + table2 + " WHERE matricule IS NOT NULL";
            
            ResultSet rs1 = stmt.executeQuery(count1Query);
            int count1 = 0;
            if (rs1.next()) count1 = rs1.getInt("count");
            rs1.close();
            
            ResultSet rs2 = stmt.executeQuery(count2Query);
            int count2 = 0;
            if (rs2.next()) count2 = rs2.getInt("count");
            rs2.close();
            
            System.out.println("Matricules uniques dans " + table1 + ": " + count1);
            System.out.println("Matricules uniques dans " + table2 + ": " + count2);
            
            // Trouver les matricules communs
            String commonQuery = String.format(
                "SELECT COUNT(DISTINCT t1.matricule) as common FROM %s t1 INNER JOIN %s t2 ON t1.matricule = t2.matricule",
                table1, table2
            );
            
            ResultSet rsCommon = stmt.executeQuery(commonQuery);
            int commonCount = 0;
            if (rsCommon.next()) commonCount = rsCommon.getInt("common");
            rsCommon.close();
            
            System.out.println("Matricules communs: " + commonCount);
            
            if (commonCount == 0) {
                System.out.println("⚠️ PROBLÈME: Aucun matricule commun entre les tables!");
            } else {
                System.out.println("✅ OK: " + commonCount + " matricules communs trouvés");
            }
            
            // Afficher quelques exemples de matricules de chaque table
            String examples1Query = "SELECT DISTINCT matricule FROM " + table1 + " WHERE matricule IS NOT NULL LIMIT 5";
            String examples2Query = "SELECT DISTINCT matricule FROM " + table2 + " WHERE matricule IS NOT NULL LIMIT 5";
            
            System.out.println("Exemples de matricules dans " + table1 + ":");
            ResultSet rsEx1 = stmt.executeQuery(examples1Query);
            while (rsEx1.next()) {
                System.out.println("  - " + rsEx1.getString("matricule"));
            }
            rsEx1.close();
            
            System.out.println("Exemples de matricules dans " + table2 + ":");
            ResultSet rsEx2 = stmt.executeQuery(examples2Query);
            while (rsEx2.next()) {
                System.out.println("  - " + rsEx2.getString("matricule"));
            }
            rsEx2.close();
            
        } catch (SQLException e) {
            System.err.println("Erreur lors du diagnostic: " + e.getMessage());
        }
        
        System.out.println("=== FIN DIAGNOSTIC ===");
    }
    
    private void handleCrossChartCreation(String table1, String column1, String table2, String column2, 
            String crossTypeRaw, String title) {
		if (table1 != null && column1 != null && table2 != null && column2 != null && crossTypeRaw != null) {
			// Vérifier que les tables sont différentes
			if (table1.equals(table2)) {
				showErrorAlert("Erreur de configuration", 
				"Veuillez sélectionner deux tables différentes pour un graphique croisé.");
				return;
			}
		
			String crossType = extractCrossChartType(crossTypeRaw);
			
			if (title == null || title.trim().isEmpty()) {
				title = "Croisement " + column1 + " (" + table1 + ") × " + column2 + " (" + table2 + ")";
			}
		
			try {
				// Faire un diagnostic des tables avant de créer le graphique
				diagnoseCrossTableCompatibility(table1, table2);
				
				// Créer le graphique croisé
				createCrossTableChart(table1, column1, table2, column2, title, crossType);
				
				HistoryManager.logCreation("Dashboard", 
				"Ajout d'un graphique croisé - Service: " + currentService);
				
				showInformation("Succès", "Processus de création du graphique croisé terminé !");
			
			} catch (Exception ex) {
				showErrorAlert("Erreur de création", 
				"Impossible de créer le graphique croisé: " + ex.getMessage());
				ex.printStackTrace();
			}
		} else {
			showErrorAlert("Paramètres incomplets", 
			"Veuillez remplir tous les champs pour créer un graphique croisé");
		}
	}
    
    private void testSimpleCrossQuery(String table1, String table2) {
        System.out.println("=== TEST REQUÊTE SIMPLE CROISÉE ===");
        
        String testQuery = String.format(
            "SELECT t1.matricule, COUNT(*) as count " +
            "FROM %s t1 " +
            "INNER JOIN %s t2 ON t1.matricule = t2.matricule " +
            "GROUP BY t1.matricule " +
            "LIMIT 10",
            table1, table2
        );
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(testQuery)) {
            
            System.out.println("Requête test: " + testQuery);
            
            int resultCount = 0;
            while (rs.next()) {
                String matricule = rs.getString("matricule");
                int count = rs.getInt("count");
                System.out.println("  Matricule: " + matricule + ", Count: " + count);
                resultCount++;
            }
            
            System.out.println("Nombre de résultats: " + resultCount);
            
            if (resultCount == 0) {
                System.out.println("❌ Aucun résultat - problème de JOIN!");
            } else {
                System.out.println("✅ JOIN fonctionne - " + resultCount + " résultats");
            }
            
        } catch (SQLException e) {
            System.err.println("Erreur lors du test: " + e.getMessage());
        }
        
        System.out.println("=== FIN TEST ===");
    }


    // NOUVELLE FONCTION 4: Récupération des données croisées
    private Map<String, Map<String, Integer>> getCrossTableData(String table1, String column1, 
            String table2, String column2) throws SQLException {
    	Map<String, Map<String, Integer>> crossData = new LinkedHashMap<>();

    	System.out.println("=== DÉBUT getCrossTableData ===");
    	System.out.println("Table1: " + table1 + ", Colonne1: " + column1);
    	System.out.println("Table2: " + table2 + ", Colonne2: " + column2);

    	// ÉTAPE 1: Vérifier que les tables ont des matricules en commun
    	String checkMatriculesQuery = String.format(
    			"SELECT COUNT(DISTINCT t1.matricule) as common_matricules " +
    					"FROM %s t1 " +
    					"INNER JOIN %s t2 ON t1.matricule = t2.matricule " +
    					"WHERE t1.matricule IS NOT NULL AND t2.matricule IS NOT NULL",
    					table1, table2
    			);

    	try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    			Statement stmt = conn.createStatement()) {

    		System.out.println("Vérification des matricules communs: " + checkMatriculesQuery);
    		ResultSet rs = stmt.executeQuery(checkMatriculesQuery);

    		int commonMatricules = 0;
    		if (rs.next()) {
    			commonMatricules = rs.getInt("common_matricules");
    		}
    		rs.close();

    		System.out.println("Matricules communs trouvés: " + commonMatricules);

    		if (commonMatricules == 0) {
    			System.out.println("Aucun matricule commun entre les deux tables");
    			return crossData; // Retourner une map vide au lieu de lancer une exception
    		}

    		// ÉTAPE 2: Vérifier que les colonnes ont des données non nulles
    		String checkColumn1Query = String.format(
    				"SELECT COUNT(*) as valid_rows FROM %s WHERE %s IS NOT NULL AND TRIM(%s) != ''",
    				table1, column1, column1
    				);

    		String checkColumn2Query = String.format(
    				"SELECT COUNT(*) as valid_rows FROM %s WHERE %s IS NOT NULL AND TRIM(%s) != ''",
    				table2, column2, column2
    				);

    		System.out.println("Vérification colonne1: " + checkColumn1Query);	
    		rs = stmt.executeQuery(checkColumn1Query);
    		int validRows1 = 0;
    		if (rs.next()) {
    			validRows1 = rs.getInt("valid_rows");
    		}
    		rs.close();

    		System.out.println("Vérification colonne2: " + checkColumn2Query);
    		rs = stmt.executeQuery(checkColumn2Query);
    		int validRows2 = 0;
    		if (rs.next()) {
    			validRows2 = rs.getInt("valid_rows");
    		}
    		rs.close();	

	System.out.println("Lignes valides pour " + column1 + ": " + validRows1);
	System.out.println("Lignes valides pour " + column2 + ": " + validRows2);
	
	if (validRows1 == 0 || validRows2 == 0) {
		System.out.println("Une des colonnes n'a pas de données valides");
		return crossData; // Retourner une map vide
	}

	// ÉTAPE 3: Construire la requête principale avec LIMIT plus élevé
	String mainQuery = String.format(
		"SELECT t1.%s as col1, t2.%s as col2, COUNT(*) as count " +
		"FROM %s t1 " +
		"INNER JOIN %s t2 ON t1.matricule = t2.matricule " +
		"WHERE t1.%s IS NOT NULL AND TRIM(t1.%s) != '' " +
		"AND t2.%s IS NOT NULL AND TRIM(t2.%s) != '' " +
		"GROUP BY t1.%s, t2.%s " +
		"ORDER BY count DESC, t1.%s, t2.%s " +
		"LIMIT 50", // Augmenter la limite
	column1, column2, table1, table2,
	column1, column1, column2, column2,
	column1, column2, column1, column2
	);

	System.out.println("Requête principale: " + mainQuery);
	
	rs = stmt.executeQuery(mainQuery);
	
	int resultCount = 0;
	while (rs.next()) {
		String value1 = rs.getString("col1");
		String value2 = rs.getString("col2");
		int count = rs.getInt("count");
		
		if (value1 != null && value2 != null && 
			!value1.trim().isEmpty() && !value2.trim().isEmpty()) {
			
			crossData.computeIfAbsent(value1, k -> new LinkedHashMap<>()).put(value2, count);
			resultCount++;
			
			System.out.println("  " + value1 + " × " + value2 + " = " + count);
		}
	}
	rs.close();

	System.out.println("Nombre de combinaisons trouvées: " + resultCount);
	
	} catch (SQLException e) {
		System.err.println("Erreur SQL dans getCrossTableData: " + e.getMessage());
		System.err.println("Code d'erreur: " + e.getErrorCode());
		throw e;
	}
	
	System.out.println("=== FIN getCrossTableData ===");
	return crossData;
	}

    // NOUVELLE FONCTION 5: Création d'un graphique en barres empilées
    private StackedBarChart<String, Number> createStackedBarChart(Map<String, Map<String, Integer>> crossData, 
                                                                String title, String xAxisLabel, String seriesLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        xAxis.setLabel(xAxisLabel);
        yAxis.setLabel("Nombre");
        
        StackedBarChart<String, Number> stackedChart = new StackedBarChart<>(xAxis, yAxis);
        stackedChart.setTitle(title);
        stackedChart.setLegendVisible(true);
        
        // Obtenir toutes les valeurs uniques pour les séries
        Set<String> allSeriesValues = new LinkedHashSet<>();
        for (Map<String, Integer> innerMap : crossData.values()) {
            allSeriesValues.addAll(innerMap.keySet());
        }
        
        // Créer une série pour chaque valeur de la deuxième colonne
        Map<String, XYChart.Series<String, Number>> seriesMap = new HashMap<>();
        for (String seriesValue : allSeriesValues) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(seriesValue);
            seriesMap.put(seriesValue, series);
            stackedChart.getData().add(series);
        }
        
        // Remplir les données
        for (Map.Entry<String, Map<String, Integer>> entry : crossData.entrySet()) {
            String category = entry.getKey();
            for (String seriesValue : allSeriesValues) {
                int value = entry.getValue().getOrDefault(seriesValue, 0);
                seriesMap.get(seriesValue).getData().add(new XYChart.Data<>(category, value));
            }
        }
        
        stackedChart.setPrefSize(500, 380);
        stackedChart.setStyle("-fx-background-color: white;");
        
        return stackedChart;
    }

    // NOUVELLE FONCTION 6: Création d'un graphique en barres groupées
    private BarChart<String, Number> createGroupedBarChart(Map<String, Map<String, Integer>> crossData, 
                                                          String title, String xAxisLabel, String seriesLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        xAxis.setLabel(xAxisLabel);
        yAxis.setLabel("Nombre");
        xAxis.setTickLabelRotation(45);
        
        BarChart<String, Number> groupedChart = new BarChart<>(xAxis, yAxis);
        groupedChart.setTitle(title);
        groupedChart.setLegendVisible(true);
        groupedChart.setCategoryGap(10);
        groupedChart.setBarGap(3);
        
        // Créer une série pour chaque valeur de la deuxième colonne
        Set<String> allSeriesValues = crossData.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        
        for (String seriesValue : allSeriesValues) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(seriesValue);
            
            for (Map.Entry<String, Map<String, Integer>> entry : crossData.entrySet()) {
                int value = entry.getValue().getOrDefault(seriesValue, 0);
                if (value > 0) {
                    series.getData().add(new XYChart.Data<>(entry.getKey(), value));
                }
            }
            
            if (!series.getData().isEmpty()) {
                groupedChart.getData().add(series);
            }
        }
        
        groupedChart.setPrefSize(500, 380);
        groupedChart.setStyle("-fx-background-color: white;");
        
        return groupedChart;
    }

    // NOUVELLE FONCTION 7: Création d'une carte de chaleur (simulée avec des rectangles colorés)
    private Chart createHeatmapChart(Map<String, Map<String, Integer>> crossData, 
                                   String title, String xAxisLabel, String yAxisLabel) {
        // Créer un graphique en aire comme approximation d'une carte de chaleur
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        xAxis.setLabel(xAxisLabel);
        yAxis.setLabel("Intensité");
        
        AreaChart<String, Number> heatmapChart = new AreaChart<>(xAxis, yAxis);
        heatmapChart.setTitle(title + " (Carte de chaleur)");
        heatmapChart.setLegendVisible(true);
        heatmapChart.setCreateSymbols(false);
        
        // Calculer les valeurs moyennes pour chaque catégorie
        for (Map.Entry<String, Map<String, Integer>> entry : crossData.entrySet()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(entry.getKey());
            
            for (Map.Entry<String, Integer> innerEntry : entry.getValue().entrySet()) {
                series.getData().add(new XYChart.Data<>(innerEntry.getKey(), innerEntry.getValue()));
            }
            
            heatmapChart.getData().add(series);
        }
        
        heatmapChart.setPrefSize(500, 380);
        heatmapChart.setStyle("-fx-background-color: white;");
        
        return heatmapChart;
    }

    // NOUVELLE FONCTION 8: Création d'un nuage de points
    private ScatterChart<String, Number> createScatterChart(Map<String, Map<String, Integer>> crossData, 
                                                           String title, String xAxisLabel, String yAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        xAxis.setLabel(xAxisLabel);
        yAxis.setLabel("Valeurs");
        
        ScatterChart<String, Number> scatterChart = new ScatterChart<>(xAxis, yAxis);
        scatterChart.setTitle(title + " (Nuage de points)");
        scatterChart.setLegendVisible(true);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Corrélation " + xAxisLabel + " × " + yAxisLabel);
        
        // Convertir les données croisées en points
        for (Map.Entry<String, Map<String, Integer>> entry : crossData.entrySet()) {
            for (Map.Entry<String, Integer> innerEntry : entry.getValue().entrySet()) {
                // Créer un point pour chaque combinaison
                String pointLabel = entry.getKey() + "-" + innerEntry.getKey();
                series.getData().add(new XYChart.Data<>(pointLabel, innerEntry.getValue()));
            }
        }
        
        scatterChart.getData().add(series);
        scatterChart.setPrefSize(500, 380);
        scatterChart.setStyle("-fx-background-color: white;");
        
        return scatterChart;
    }
    
    public void forceRefreshDashboard() {
        Platform.runLater(() -> {
            try {
                System.out.println("=== ACTUALISATION FORCÉE ===");
                
                // Vider complètement le conteneur
                chartsContainer.getChildren().clear();
                System.out.println("Conteneur vidé");
                
                // Masquer le message vide temporairement
                if (emptyStateMessage != null) {
                    emptyStateMessage.setVisible(false);
                    emptyStateMessage.setManaged(false);
                }
                
                // Recréer les graphiques
                createServiceSpecificCharts();
                
                // Mettre à jour l'affichage
                updateEmptyState();
                updateLastRefreshTime();
                
                System.out.println("Actualisation forcée terminée");
                
            } catch (Exception e) {
                System.err.println("Erreur lors de l'actualisation forcée: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // NOUVELLE FONCTION: Test de création d'un graphique simple pour débugger
    private void createTestChart() {
        Platform.runLater(() -> {
            try {
                System.out.println("=== CRÉATION D'UN GRAPHIQUE DE TEST ===");
                
                // Créer un graphique de test simple
                PieChart testChart = new PieChart();
                testChart.getData().add(new PieChart.Data("Test 1", 30));
                testChart.getData().add(new PieChart.Data("Test 2", 70));
                testChart.setTitle("Graphique de Test");
                
                addChartToContainer(testChart, "test", "test");
                
                System.out.println("Graphique de test créé");
                
            } catch (Exception e) {
                System.err.println("Erreur lors de la création du graphique de test: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // NOUVELLE FONCTION: Vérification de l'état du dashboard
    public void verifyDashboardState() {
        Platform.runLater(() -> {
            System.out.println("=== VÉRIFICATION ÉTAT DASHBOARD ===");
            System.out.println("chartsContainer null? " + (chartsContainer == null));
            
            if (chartsContainer != null) {
                System.out.println("chartsContainer.getChildren().size(): " + chartsContainer.getChildren().size());
                System.out.println("chartsContainer.isVisible(): " + chartsContainer.isVisible());
                System.out.println("chartsContainer.isManaged(): " + chartsContainer.isManaged());
                System.out.println("chartsContainer.getParent(): " + chartsContainer.getParent());
            }
            
            System.out.println("emptyStateMessage null? " + (emptyStateMessage == null));
            if (emptyStateMessage != null) {
                System.out.println("emptyStateMessage.isVisible(): " + emptyStateMessage.isVisible());
                System.out.println("emptyStateMessage.isManaged(): " + emptyStateMessage.isManaged());
            }
            
            System.out.println("availableTables: " + availableTables);
            System.out.println("currentService: " + currentService);
            System.out.println("=== FIN VÉRIFICATION ===");
        });
    }

    // MÉTHODE UTILITAIRE: Ajouter un bouton de debug (à utiliser temporairement)
    private void addDebugButton() {
        Platform.runLater(() -> {
            try {
                if (chartsContainer != null && chartsContainer.getParent() instanceof VBox) {
                    VBox parent = (VBox) chartsContainer.getParent();
                    
                    HBox debugBox = new HBox(10);
                    debugBox.setAlignment(Pos.CENTER);
                    debugBox.setPadding(new Insets(10));
                    debugBox.setStyle("-fx-background-color: #fff3cd; -fx-border-color: #ffeaa7;");
                    
                    Button debugBtn = new Button("🔧 Debug Dashboard");
                    debugBtn.setOnAction(e -> verifyDashboardState());
                    
                    Button testBtn = new Button("🧪 Créer Test");
                    testBtn.setOnAction(e -> createTestChart());
                    
                    Button refreshBtn = new Button("🔄 Forcer Refresh");
                    refreshBtn.setOnAction(e -> forceRefreshDashboard());
                    
                    debugBox.getChildren().addAll(debugBtn, testBtn, refreshBtn);
                    
                    // Insérer en première position
                    if (!parent.getChildren().contains(debugBox)) {
                        parent.getChildren().add(0, debugBox);
                    }
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de l'ajout du bouton debug: " + e.getMessage());
            }
        });
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
        // Débugger d'abord
        debugChartAddition();
        
        Platform.runLater(() -> {
            try {
                chartsContainer.getChildren().clear();
                System.out.println("Conteneur vidé, création des graphiques pour service: " + currentService);
            } catch (Exception e) {
                System.err.println("Erreur lors du vidage du conteneur: " + e.getMessage());
            }
        });
        
        try {
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
        } catch (Exception e) {
            System.err.println("Erreur lors de la création des graphiques: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void createLogistiqueCharts() throws SQLException {
    	try {
            if (availableTables.contains("parametres_corporels")) {
                System.out.println("Création graphique: Distribution des Tailles");
                addDistributionChart("parametres_corporels", "taille", "Distribution des Tailles", "Camembert");
            }
            
            if (availableTables.contains("dotation_particuliere")) {
                System.out.println("Création graphique: Dotations par Année");
                addTemporalChart("dotation_particuliere", "annee", "Dotations par Année", "Barre");
            }
            
            if (availableTables.contains("identite_personnelle")) {
                System.out.println("Création graphique: Répartition par Sexe");
                addDistributionChart("identite_personnelle", "sexe", "Répartition par Sexe", "Camembert");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la création des graphiques Logistique: " + e.getMessage());
        }
    }
    
    private void createOperationsCharts() throws SQLException {
    	try {
            if (availableTables.contains("operation")) {
                addDistributionChart("operation", "type", "Opérations par Type", "Barre");
                addTemporalChart("operation", "annee", "Opérations par Année", "Ligne");
            }
            
            if (availableTables.contains("grade_actuel")) {
                addDistributionChart("grade_actuel", "rang", "Répartition par Grade", "Barre");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la création des graphiques Opérations: " + e.getMessage());
        }
    }
    
    private void createRhCharts() throws SQLException {
    	try {
            if (availableTables.contains("grade_actuel")) {
                addDistributionChart("grade_actuel", "rang", "Répartition par Grade", "Barre");
            }
            
            if (availableTables.contains("identite_personnelle")) {
                addDistributionChart("identite_personnelle", "sexe", "Répartition par Sexe", "Camembert");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la création des graphiques RH: " + e.getMessage());
        }
    }
    
    private void createDefaultCharts() throws SQLException {
    	try {
            if (availableTables.contains("identite_personnelle")) {
                addDistributionChart("identite_personnelle", "sexe", "Répartition par Sexe", "Camembert");
            }
            
            if (availableTables.contains("grade_actuel")) {
                addDistributionChart("grade_actuel", "rang", "Répartition par Grade", "Barre");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la création des graphiques par défaut: " + e.getMessage());
        }
    }
    
    private void addDistributionChart(String tableName, String column, String title, String chartType) throws SQLException {
        System.out.println("=== DÉBUT addDistributionChart ===");
        System.out.println("Table: " + tableName + ", Colonne: " + column + ", Type: " + chartType);
        
        Map<String, Integer> data = getDistributionData(tableName, column);
        
        System.out.println("Données récupérées: " + data.size() + " éléments");
        
        if (data.isEmpty()) {
            System.out.println("Aucune donnée trouvée pour " + tableName + "." + column);
            return;
        }
        
        Platform.runLater(() -> {
            try {
                System.out.println("Création du graphique sur le thread UI...");
                
                Chart chart = null;
                switch (chartType) {
                    case "Camembert":
                        chart = createEnhancedPieChart(data, title);
                        System.out.println("Graphique camembert créé");
                        break;
                    case "Barre":
                        chart = createEnhancedBarChart(data, title, column);
                        System.out.println("Graphique en barres créé");
                        break;
                    case "Ligne":
                        chart = createEnhancedLineChart(data, title, column);
                        System.out.println("Graphique en ligne créé");
                        break;
                    default:
                        chart = createEnhancedBarChart(data, title, column);
                        System.out.println("Graphique par défaut (barres) créé");
                }
                
                if (chart != null) {
                    System.out.println("Ajout du graphique au conteneur...");
                    addChartToContainer(chart, tableName, column);
                    System.out.println("Graphique ajouté avec succès!");
                } else {
                    System.err.println("ERREUR: Chart est null après création");
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de la création du graphique: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        System.out.println("=== FIN addDistributionChart ===");
    }
    
    private void addTemporalChart(String tableName, String timeColumn, String title, String chartType) throws SQLException {
        System.out.println("=== DÉBUT addTemporalChart ===");
        System.out.println("Table: " + tableName + ", Colonne: " + timeColumn + ", Type: " + chartType);
        
        Map<String, Integer> data = getTemporalData(tableName, timeColumn);
        
        System.out.println("Données temporelles récupérées: " + data.size() + " éléments");
        
        if (data.isEmpty()) {
            System.out.println("Aucune donnée temporelle trouvée pour " + tableName + "." + timeColumn);
            return;
        }
        
        Platform.runLater(() -> {
            try {
                System.out.println("Création du graphique temporel sur le thread UI...");
                
                Chart chart = null;
                switch (chartType) {
                    case "Ligne":
                        chart = createEnhancedLineChart(data, title, timeColumn);
                        System.out.println("Graphique temporel en ligne créé");
                        break;
                    case "Barre":
                        chart = createEnhancedBarChart(data, title, timeColumn);
                        System.out.println("Graphique temporel en barres créé");
                        break;
                    default:
                        chart = createEnhancedLineChart(data, title, timeColumn);
                        System.out.println("Graphique temporel par défaut (ligne) créé");
                }
                
                if (chart != null) {
                    System.out.println("Ajout du graphique temporel au conteneur...");
                    addChartToContainer(chart, tableName, timeColumn);
                    System.out.println("Graphique temporel ajouté avec succès!");
                } else {
                    System.err.println("ERREUR: Chart temporel est null après création");
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de la création du graphique temporel: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        System.out.println("=== FIN addTemporalChart ===");
    }
    
    private Map<String, Integer> getDistributionData(String tableName, String column) throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT " + column + ", COUNT(*) as count FROM " + tableName + 
                      " WHERE " + column + " IS NOT NULL AND " + column + " != '' " +
                      " GROUP BY " + column + " ORDER BY count DESC LIMIT 10";
        
        System.out.println("Exécution de la requête: " + query);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            int rowCount = 0;
            while (rs.next()) {
                String key = rs.getString(column);
                int count = rs.getInt("count");
                if (key != null && !key.trim().isEmpty()) {
                    data.put(key, count);
                    rowCount++;
                    System.out.println("  " + key + " -> " + count);
                }
            }
            
            System.out.println("Nombre de lignes récupérées: " + rowCount);
            
        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de la récupération des données pour " + tableName + "." + column);
            System.err.println("Message: " + e.getMessage());
            System.err.println("Requête: " + query);
            throw e;
        }
        
        return data;
    }
    
    private Map<String, Integer> getTemporalData(String tableName, String timeColumn) throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT " + timeColumn + " as time_period, COUNT(*) as count FROM " + tableName + 
                      " WHERE " + timeColumn + " IS NOT NULL " +
                      " GROUP BY " + timeColumn + " ORDER BY time_period LIMIT 20";
        
        System.out.println("Exécution de la requête temporelle: " + query);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            int rowCount = 0;
            while (rs.next()) {
                String period = rs.getString("time_period");
                int count = rs.getInt("count");
                if (period != null) {
                    data.put(period, count);
                    rowCount++;
                    System.out.println("  " + period + " -> " + count);
                }
            }
            
            System.out.println("Nombre de périodes récupérées: " + rowCount);
            
        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de la récupération des données temporelles pour " + tableName + "." + timeColumn);
            System.err.println("Message: " + e.getMessage());
            System.err.println("Requête: " + query);
            throw e;
        }
        
        return data;
    }
    
    private void verifyTableStructure(String tableName) {
        System.out.println("=== VÉRIFICATION STRUCTURE TABLE: " + tableName + " ===");
        
        String query = "DESCRIBE " + tableName;
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            System.out.println("Colonnes de la table " + tableName + ":");
            while (rs.next()) {
                String field = rs.getString("Field");
                String type = rs.getString("Type");
                String nullValue = rs.getString("Null");
                System.out.println("  - " + field + " (" + type + ") NULL=" + nullValue);
            }
            
        } catch (SQLException e) {
            System.err.println("Erreur lors de la vérification de la structure de " + tableName + ": " + e.getMessage());
        }
        
        // Vérifier aussi le nombre d'enregistrements
        String countQuery = "SELECT COUNT(*) as total FROM " + tableName;
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countQuery)) {
            
            if (rs.next()) {
                int total = rs.getInt("total");
                System.out.println("Nombre total d'enregistrements dans " + tableName + ": " + total);
            }
            
        } catch (SQLException e) {
            System.err.println("Erreur lors du comptage de " + tableName + ": " + e.getMessage());
        }
        
        System.out.println("=== FIN VÉRIFICATION STRUCTURE ===");
    }
    
    private void testDatabaseConnection() {
        System.out.println("=== TEST DE CONNEXION BASE DE DONNÉES ===");
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("Connexion réussie!");
            System.out.println("URL: " + DB_URL);
            System.out.println("Utilisateur: " + DB_USER);
            
            // Tester une requête simple
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1 as test")) {
                if (rs.next()) {
                    System.out.println("Requête test réussie: " + rs.getInt("test"));
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Erreur de connexion: " + e.getMessage());
            System.err.println("Code d'erreur: " + e.getErrorCode());
            System.err.println("État SQL: " + e.getSQLState());
        }
        
        System.out.println("=== FIN TEST CONNEXION ===");
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
    	// Rediriger vers la version améliorée avec support multi-tables
        showEnhancedCustomizeDialog();
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
        if (chart == null) {
            System.err.println("Erreur: Chart est null, impossible d'ajouter au conteneur");
            return;
        }
        
        Platform.runLater(() -> {
            try {
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
                
                // CORRECTION: Supprimer la limite et ajouter directement
                chartsContainer.getChildren().add(chartContainer);
                
                // CORRECTION: Masquer le message vide et mettre à jour l'affichage
                if (emptyStateMessage != null) {
                    emptyStateMessage.setVisible(false);
                    emptyStateMessage.setManaged(false);
                }
                
                updateEmptyState();
                
                System.out.println("Graphique ajouté avec succès. Total: " + chartsContainer.getChildren().size());
                
            } catch (Exception e) {
                System.err.println("Erreur lors de l'ajout du graphique: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    private void debugChartAddition() {
        Platform.runLater(() -> {
            System.out.println("=== DEBUG DASHBOARD ===");
            System.out.println("chartsContainer null? " + (chartsContainer == null));
            if (chartsContainer != null) {
                System.out.println("Nombre d'enfants: " + chartsContainer.getChildren().size());
                System.out.println("Visible: " + chartsContainer.isVisible());
                System.out.println("Managed: " + chartsContainer.isManaged());
            }
            System.out.println("emptyStateMessage null? " + (emptyStateMessage == null));
            if (emptyStateMessage != null) {
                System.out.println("emptyStateMessage visible: " + emptyStateMessage.isVisible());
                System.out.println("emptyStateMessage managed: " + emptyStateMessage.isManaged());
            }
        });
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