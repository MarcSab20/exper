package application;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.chart.*;
import javafx.scene.control.Tooltip;
import javafx.application.Platform;
import java.util.*;

/**
 * Factory pour créer différents types de graphiques de manière optimisée - VERSION AMÉLIORÉE
 */
public class ChartFactory {
    
    // Couleurs modernes pour les graphiques
    private static final String[] MODERN_COLORS = {
        "#667eea", "#764ba2", "#f093fb", "#f5576c", "#4facfe", "#00f2fe",
        "#43e97b", "#38f9d7", "#ffecd2", "#fcb69f", "#a8edea", "#fed6e3",
        "#d299c2", "#fef9d7", "#89f7fe", "#66a6ff", "#f6d365", "#fda085",
        "#96fbc4", "#f9f586", "#fa709a", "#fee140", "#a18cd1", "#fbc2eb"
    };
    
    /**
     * CORRECTION: Crée un graphique selon le type demandé avec mapping amélioré
     */
    public static Chart createChart(String chartType, Map<String, Integer> data, String title, String xAxisLabel) {
        if (data == null || data.isEmpty()) {
            return createEmptyChart(title);
        }
        
        switch (chartType.toLowerCase()) {
            case "camembert":
            case "pie":
                return createModernPieChart(data, title);
            case "histogramme":
            case "bar":
                return createModernBarChart(data, title, xAxisLabel);
            case "ligne":
            case "line":
                return createModernLineChart(data, title, xAxisLabel);
            case "aire":
            case "area":
                return createModernAreaChart(data, title, xAxisLabel);
            case "nuage":
            case "scatter":
                return createModernScatterChart(data, title, xAxisLabel);
            default:
                return createModernBarChart(data, title, xAxisLabel);
        }
    }
    
    /**
     * CORRECTION: Crée un graphique croisé selon le type demandé avec mapping amélioré
     */
    public static Chart createCrossChart(String chartType, Map<String, Map<String, Integer>> crossData, 
                                       String title, String xAxisLabel, String seriesLabel) {
        if (crossData == null || crossData.isEmpty()) {
            return createEmptyChart(title);
        }
        
        switch (chartType.toLowerCase()) {
            case "empile":
            case "stacked":
                return createModernStackedBarChart(crossData, title, xAxisLabel, seriesLabel);
            case "groupe":
            case "grouped":
                return createModernGroupedBarChart(crossData, title, xAxisLabel, seriesLabel);
            case "chaleur":
            case "heatmap":
                return createModernHeatmapChart(crossData, title, xAxisLabel, seriesLabel);
            case "nuage":
            case "scatter":
                return createModernCrossScatterChart(crossData, title, xAxisLabel, seriesLabel);
            default:
                return createModernStackedBarChart(crossData, title, xAxisLabel, seriesLabel);
        }
    }
    
    /**
     * Crée un graphique camembert moderne
     */
    private static PieChart createModernPieChart(Map<String, Integer> data, String title) {
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        
        int colorIndex = 0;
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            String label = entry.getKey() + " (" + entry.getValue() + ")";
            PieChart.Data slice = new PieChart.Data(label, entry.getValue());
            pieData.add(slice);
        }
        
        PieChart chart = new PieChart(pieData);
        chart.setTitle(title);
        chart.setLabelsVisible(true);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.RIGHT);
        chart.setStartAngle(90);
        chart.setClockwise(true);
        
        // Taille optimisée
        chart.setPrefSize(500, 400);
        chart.setMinSize(450, 350);
        
        // Appliquer les couleurs modernes après l'ajout à la scène
        Platform.runLater(() -> applyModernColorsToChart(chart, pieData.size()));
        
        return chart;
    }
    
    /**
     * Crée un graphique en barres moderne
     */
    private static BarChart<String, Number> createModernBarChart(Map<String, Integer> data, String title, String xAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        configureAxes(xAxis, yAxis, xAxisLabel != null ? xAxisLabel : "Catégorie", "Nombre");
        
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setCategoryGap(20);
        chart.setBarGap(5);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Données");
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        chart.getData().add(series);
        chart.setPrefSize(550, 400);
        chart.setMinSize(500, 350);
        
        // Appliquer les couleurs modernes
        Platform.runLater(() -> applyModernColorsToChart(chart, data.size()));
        
        return chart;
    }
    
    /**
     * Crée un graphique en ligne moderne
     */
    private static LineChart<String, Number> createModernLineChart(Map<String, Integer> data, String title, String xAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        configureAxes(xAxis, yAxis, xAxisLabel != null ? xAxisLabel : "Catégorie", "Nombre");
        
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.TOP);
        chart.setCreateSymbols(true);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Évolution");
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        chart.getData().add(series);
        chart.setPrefSize(550, 400);
        chart.setMinSize(500, 350);
        
        // Style moderne pour la ligne
        Platform.runLater(() -> {
            if (!series.getData().isEmpty()) {
                series.getNode().setStyle("-fx-stroke: " + MODERN_COLORS[0] + "; -fx-stroke-width: 3px;");
            }
        });
        
        return chart;
    }
    
    /**
     * Crée un graphique en aire moderne
     */
    private static AreaChart<String, Number> createModernAreaChart(Map<String, Integer> data, String title, String xAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        configureAxes(xAxis, yAxis, xAxisLabel != null ? xAxisLabel : "Catégorie", "Nombre");
        
        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.TOP);
        chart.setCreateSymbols(false);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Données");
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        chart.getData().add(series);
        chart.setPrefSize(550, 400);
        chart.setMinSize(500, 350);
        
        return chart;
    }
    
    /**
     * Crée un nuage de points moderne
     */
    private static ScatterChart<String, Number> createModernScatterChart(Map<String, Integer> data, String title, String xAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        configureAxes(xAxis, yAxis, xAxisLabel != null ? xAxisLabel : "Catégorie", "Valeurs");
        
        ScatterChart<String, Number> chart = new ScatterChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(true);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Points de données");
        
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        
        chart.getData().add(series);
        chart.setPrefSize(550, 400);
        chart.setMinSize(500, 350);
        
        return chart;
    }
    
    /**
     * Crée un graphique en barres empilées moderne
     */
    private static StackedBarChart<String, Number> createModernStackedBarChart(Map<String, Map<String, Integer>> crossData, 
                                                                             String title, String xAxisLabel, String seriesLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        configureAxes(xAxis, yAxis, xAxisLabel != null ? xAxisLabel : "Catégorie", "Nombre");
        
        StackedBarChart<String, Number> chart = new StackedBarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.RIGHT);
        
        // Obtenir toutes les valeurs de série uniques
        Set<String> allSeriesValues = new LinkedHashSet<>();
        crossData.values().forEach(map -> allSeriesValues.addAll(map.keySet()));
        
        // Créer une série pour chaque valeur
        int colorIndex = 0;
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
                chart.getData().add(series);
                final int currentColorIndex = colorIndex++;
                
                // Appliquer la couleur
                Platform.runLater(() -> {
                    if (currentColorIndex < MODERN_COLORS.length) {
                        series.getData().forEach(data -> {
                            if (data.getNode() != null) {
                                data.getNode().setStyle("-fx-bar-fill: " + MODERN_COLORS[currentColorIndex % MODERN_COLORS.length] + ";");
                            }
                        });
                    }
                });
            }
        }
        
        chart.setPrefSize(600, 450);
        chart.setMinSize(550, 400);
        
        return chart;
    }
    
    /**
     * Crée un graphique en barres groupées moderne
     */
    private static BarChart<String, Number> createModernGroupedBarChart(Map<String, Map<String, Integer>> crossData, 
                                                                       String title, String xAxisLabel, String seriesLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        configureAxes(xAxis, yAxis, xAxisLabel != null ? xAxisLabel : "Catégorie", "Nombre");
        
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.RIGHT);
        chart.setCategoryGap(10);
        chart.setBarGap(3);
        
        // Obtenir toutes les valeurs de série uniques
        Set<String> allSeriesValues = new LinkedHashSet<>();
        crossData.values().forEach(map -> allSeriesValues.addAll(map.keySet()));
        
        // Créer une série pour chaque valeur
        int colorIndex = 0;
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
                chart.getData().add(series);
                final int currentColorIndex = colorIndex++;
                
                // Appliquer la couleur
                Platform.runLater(() -> {
                    if (currentColorIndex < MODERN_COLORS.length) {
                        series.getData().forEach(data -> {
                            if (data.getNode() != null) {
                                data.getNode().setStyle("-fx-bar-fill: " + MODERN_COLORS[currentColorIndex % MODERN_COLORS.length] + ";");
                            }
                        });
                    }
                });
            }
        }
        
        chart.setPrefSize(650, 450);
        chart.setMinSize(600, 400);
        
        return chart;
    }
    
    /**
     * Crée une "carte de chaleur" simulée avec un graphique en aire
     */
    private static Chart createModernHeatmapChart(Map<String, Map<String, Integer>> crossData, 
                                                String title, String xAxisLabel, String seriesLabel) {
        // Simuler une heatmap avec un graphique en aire empilé
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        configureAxes(xAxis, yAxis, xAxisLabel != null ? xAxisLabel : "Catégorie", "Intensité");
        
        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setTitle(title + " (Intensité)");
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.RIGHT);
        chart.setCreateSymbols(false);
        
        // Créer des séries pour simuler l'effet de heatmap
        Set<String> allSeriesValues = new LinkedHashSet<>();
        crossData.values().forEach(map -> allSeriesValues.addAll(map.keySet()));
        
        int colorIndex = 0;
        for (String seriesValue : allSeriesValues) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(seriesValue);
            
            for (Map.Entry<String, Map<String, Integer>> entry : crossData.entrySet()) {
                int value = entry.getValue().getOrDefault(seriesValue, 0);
                series.getData().add(new XYChart.Data<>(entry.getKey(), value));
            }
            
            chart.getData().add(series);
            colorIndex++;
        }
        
        chart.setPrefSize(600, 450);
        chart.setMinSize(550, 400);
        
        return chart;
    }
    
    /**
     * Crée un nuage de points croisé
     */
    private static ScatterChart<String, Number> createModernCrossScatterChart(Map<String, Map<String, Integer>> crossData, 
                                                                            String title, String xAxisLabel, String seriesLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        
        configureAxes(xAxis, yAxis, xAxisLabel != null ? xAxisLabel : "Catégorie", "Valeurs");
        
        ScatterChart<String, Number> chart = new ScatterChart<>(xAxis, yAxis);
        chart.setTitle(title + " (Corrélation)");
        chart.setLegendVisible(true);
        
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Corrélation " + xAxisLabel + " × " + seriesLabel);
        
        // Convertir les données croisées en points
        for (Map.Entry<String, Map<String, Integer>> entry : crossData.entrySet()) {
            for (Map.Entry<String, Integer> innerEntry : entry.getValue().entrySet()) {
                String pointLabel = entry.getKey() + "-" + innerEntry.getKey();
                series.getData().add(new XYChart.Data<>(pointLabel, innerEntry.getValue()));
            }
        }
        
        chart.getData().add(series);
        chart.setPrefSize(600, 450);
        chart.setMinSize(550, 400);
        
        return chart;
    }
    
    /**
     * Crée un graphique vide avec un message
     */
    private static Chart createEmptyChart(String title) {
        PieChart chart = new PieChart();
        chart.setTitle(title + " (Aucune donnée)");
        chart.getData().add(new PieChart.Data("Aucune donnée disponible", 1));
        chart.setLegendVisible(false);
        chart.setPrefSize(400, 300);
        
        Platform.runLater(() -> {
            if (!chart.getData().isEmpty()) {
                chart.getData().get(0).getNode().setStyle("-fx-pie-color: #ecf0f1;");
            }
        });
        
        return chart;
    }
    
    /**
     * Configure les axes avec un style moderne
     */
    private static void configureAxes(CategoryAxis xAxis, NumberAxis yAxis, String xLabel, String yLabel) {
        xAxis.setLabel(xLabel);
        xAxis.setTickLabelRotation(45);
        xAxis.setTickLabelGap(8);
        xAxis.setTickMarkVisible(true);
        
        yAxis.setLabel(yLabel);
        yAxis.setAutoRanging(true);
        yAxis.setTickMarkVisible(true);
        yAxis.setMinorTickVisible(true);
        yAxis.setForceZeroInRange(true);
    }
    
    /**
     * Applique des couleurs modernes à un graphique
     */
    private static void applyModernColorsToChart(Chart chart, int dataCount) {
        Platform.runLater(() -> {
            if (chart instanceof PieChart) {
                PieChart pieChart = (PieChart) chart;
                int colorIndex = 0;
                for (PieChart.Data slice : pieChart.getData()) {
                    if (slice.getNode() != null && colorIndex < MODERN_COLORS.length) {
                        slice.getNode().setStyle("-fx-pie-color: " + MODERN_COLORS[colorIndex % MODERN_COLORS.length] + ";");
                        colorIndex++;
                    }
                }
            } else if (chart instanceof XYChart) {
                @SuppressWarnings("unchecked")
                XYChart<String, Number> xyChart = (XYChart<String, Number>) chart;
                
                int seriesIndex = 0;
                for (XYChart.Series<String, Number> series : xyChart.getData()) {
                    if (seriesIndex < MODERN_COLORS.length) {
                        int dataIndex = 0;
                        for (XYChart.Data<String, Number> data : series.getData()) {
                            if (data.getNode() != null) {
                                String color = MODERN_COLORS[(seriesIndex + dataIndex) % MODERN_COLORS.length];
                                data.getNode().setStyle("-fx-bar-fill: " + color + "; -fx-background-color: " + color + ";");
                                dataIndex++;
                            }
                        }
                        seriesIndex++;
                    }
                }
            }
        });
    }
    
    /**
     * Ajoute des tooltips informatifs à un graphique
     */
    public static void addTooltips(Chart chart, String tableName, String columnName) {
        String tooltipText = String.format("Source: %s\nColonne: %s\nClic droit pour plus d'options", 
                                         tableName != null ? tableName : "Multiple", 
                                         columnName != null ? columnName : "Multiple");
        
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-text-fill: white; " +
                        "-fx-background-radius: 6px; -fx-font-size: 11px; -fx-padding: 8px 12px;");
        
        Tooltip.install(chart, tooltip);
    }
    
    /**
     * AMÉLIORATION: Types de graphiques disponibles avec tous les types demandés
     */
    public enum ChartType {
        PIE("Camembert", "camembert"),
        BAR("Histogramme", "histogramme"),
        LINE("Ligne", "ligne"),
        AREA("Aire", "aire"),
        SCATTER("Nuage de points", "nuage"),
        STACKED_BAR("Barres empilées", "empile"),
        GROUPED_BAR("Barres groupées", "groupe"),
        HEATMAP("Carte de chaleur", "chaleur");
        
        private final String displayName;
        private final String code;
        
        ChartType(String displayName, String code) {
            this.displayName = displayName;
            this.code = code;
        }
        
        public String getDisplayName() { return displayName; }
        public String getCode() { return code; }
        
        public static List<String> getDisplayNames() {
            List<String> names = new ArrayList<>();
            for (ChartType type : values()) {
                names.add(type.getDisplayName());
            }
            return names;
        }
        
        public static String getCodeFromDisplayName(String displayName) {
            for (ChartType type : values()) {
                if (type.getDisplayName().equals(displayName)) {
                    return type.getCode();
                }
            }
            return "histogramme"; // défaut
        }
        
        /**
         * NOUVEAU: Obtient les types de graphiques pour graphiques simples
         */
        public static List<String> getSingleVariableChartTypes() {
            return Arrays.asList(
                "Camembert", "Histogramme", "Ligne", "Aire", "Nuage de points"
            );
        }
        
        /**
         * NOUVEAU: Obtient les types de graphiques pour graphiques croisés
         */
        public static List<String> getCrossTableChartTypes() {
            return Arrays.asList(
                "Barres empilées", "Barres groupées", "Carte de chaleur", "Nuage de points"
            );
        }
    }
}