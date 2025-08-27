package application;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service pour la persistance des configurations de graphiques personnalisés
 */
public class ChartPersistenceService {
    private static final Logger LOGGER = Logger.getLogger(ChartPersistenceService.class.getName());
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    static {
        initializeChartConfigTable();
    }
    
    /**
     * Initialise la table des configurations de graphiques si elle n'existe pas
     */
    private static void initializeChartConfigTable() {
        String createTableQuery = """
            CREATE TABLE IF NOT EXISTS dashboard_chart_configs (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_service VARCHAR(100) NOT NULL,
                chart_id VARCHAR(100) NOT NULL,
                chart_type VARCHAR(50) NOT NULL,
                chart_title VARCHAR(200),
                table_name VARCHAR(100),
                column_name VARCHAR(100),
                table_name_2 VARCHAR(100),
                column_name_2 VARCHAR(100),
                is_cross_table BOOLEAN DEFAULT FALSE,
                is_default BOOLEAN DEFAULT FALSE,
                position_order INT DEFAULT 0,
                created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_user_service (user_service),
                INDEX idx_chart_id (chart_id),
                UNIQUE KEY unique_user_chart (user_service, chart_id)
            )""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate(createTableQuery);
            LOGGER.info("Table dashboard_chart_configs initialisée avec succès");
            
            // Insérer les graphiques par défaut si pas encore présents
            insertDefaultCharts();
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation de la table des configurations", e);
        }
    }
    
    /**
     * Insère les graphiques par défaut pour tous les services
     */
    private static void insertDefaultCharts() {
        List<String> services = Arrays.asList("admin", "Logistique", "Opérations", "Ressources Humaines");
        
        for (String service : services) {
            try {
                if (!hasDefaultCharts(service)) {
                    insertDefaultChartsForService(service);
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Erreur lors de l'insertion des graphiques par défaut pour " + service, e);
            }
        }
    }
    
    /**
     * Vérifie si un service a déjà des graphiques par défaut
     */
    private static boolean hasDefaultCharts(String service) throws SQLException {
        String query = "SELECT COUNT(*) FROM dashboard_chart_configs WHERE user_service = ? AND is_default = TRUE";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, service);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
    
    /**
     * Insère les graphiques par défaut pour un service donné
     */
    private static void insertDefaultChartsForService(String service) throws SQLException {
        List<ChartConfig> defaultCharts = Arrays.asList(
            new ChartConfig("default_sexe", "pie", "Répartition par Sexe", 
                          "identite_personnelle", "sexe", null, null, false, true, 1),
            new ChartConfig("default_grade", "bar", "Répartition par Grade", 
                          "grade_actuel", "rang", null, null, false, true, 2),
            new ChartConfig("default_region", "pie", "Répartition par Région", 
                          "identite_culturelle", "region_origine", null, null, false, true, 3),
            new ChartConfig("default_religion", "bar", "Répartition par Religion", 
                          "identite_culturelle", "religion", null, null, false, true, 4)
        );
        
        String insertQuery = """
            INSERT INTO dashboard_chart_configs 
            (user_service, chart_id, chart_type, chart_title, table_name, column_name, 
             table_name_2, column_name_2, is_cross_table, is_default, position_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            
            for (ChartConfig config : defaultCharts) {
                stmt.setString(1, service);
                stmt.setString(2, config.getChartId());
                stmt.setString(3, config.getChartType());
                stmt.setString(4, config.getChartTitle());
                stmt.setString(5, config.getTableName());
                stmt.setString(6, config.getColumnName());
                stmt.setString(7, config.getTableName2());
                stmt.setString(8, config.getColumnName2());
                stmt.setBoolean(9, config.isCrossTable());
                stmt.setBoolean(10, config.isDefault());
                stmt.setInt(11, config.getPositionOrder());
                
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            LOGGER.info("Graphiques par défaut insérés pour le service: " + service);
        }
    }
    
    /**
     * Sauvegarde une configuration de graphique
     */
    public static void saveChartConfig(String userService, ChartConfig config) {
        String upsertQuery = """
            INSERT INTO dashboard_chart_configs 
            (user_service, chart_id, chart_type, chart_title, table_name, column_name, 
             table_name_2, column_name_2, is_cross_table, is_default, position_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            chart_type = VALUES(chart_type),
            chart_title = VALUES(chart_title),
            table_name = VALUES(table_name),
            column_name = VALUES(column_name),
            table_name_2 = VALUES(table_name_2),
            column_name_2 = VALUES(column_name_2),
            is_cross_table = VALUES(is_cross_table),
            position_order = VALUES(position_order),
            updated_date = CURRENT_TIMESTAMP""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(upsertQuery)) {
            
            stmt.setString(1, userService);
            stmt.setString(2, config.getChartId());
            stmt.setString(3, config.getChartType());
            stmt.setString(4, config.getChartTitle());
            stmt.setString(5, config.getTableName());
            stmt.setString(6, config.getColumnName());
            stmt.setString(7, config.getTableName2());
            stmt.setString(8, config.getColumnName2());
            stmt.setBoolean(9, config.isCrossTable());
            stmt.setBoolean(10, config.isDefault());
            stmt.setInt(11, config.getPositionOrder());
            
            stmt.executeUpdate();
            
            // Enregistrer dans l'historique
            HistoryManager.logCreation("dashboard_chart_configs", 
                    "Configuration graphique sauvegardée: " + config.getChartTitle() + 
                    " (Service: " + userService + ")");
            
            LOGGER.info("Configuration sauvegardée: " + config.getChartId() + " pour " + userService);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la sauvegarde de la configuration", e);
        }
    }
    
    /**
     * Charge toutes les configurations de graphiques pour un service
     */
    public static List<ChartConfig> loadChartConfigs(String userService) {
        List<ChartConfig> configs = new ArrayList<>();
        
        String query = """
            SELECT chart_id, chart_type, chart_title, table_name, column_name, 
                   table_name_2, column_name_2, is_cross_table, is_default, position_order
            FROM dashboard_chart_configs 
            WHERE user_service = ? 
            ORDER BY position_order, created_date""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, userService);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChartConfig config = new ChartConfig(
                        rs.getString("chart_id"),
                        rs.getString("chart_type"),
                        rs.getString("chart_title"),
                        rs.getString("table_name"),
                        rs.getString("column_name"),
                        rs.getString("table_name_2"),
                        rs.getString("column_name_2"),
                        rs.getBoolean("is_cross_table"),
                        rs.getBoolean("is_default"),
                        rs.getInt("position_order")
                    );
                    configs.add(config);
                }
            }
            
            LOGGER.info("Chargement de " + configs.size() + " configurations pour " + userService);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des configurations", e);
        }
        
        return configs;
    }
    
    /**
     * Supprime une configuration de graphique
     */
    public static void deleteChartConfig(String userService, String chartId) {
        String deleteQuery = "DELETE FROM dashboard_chart_configs WHERE user_service = ? AND chart_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(deleteQuery)) {
            
            stmt.setString(1, userService);
            stmt.setString(2, chartId);
            
            int deletedRows = stmt.executeUpdate();
            
            if (deletedRows > 0) {
                // Enregistrer dans l'historique
                HistoryManager.logDeletion("dashboard_chart_configs", 
                        "Configuration graphique supprimée: " + chartId + 
                        " (Service: " + userService + ")");
                
                LOGGER.info("Configuration supprimée: " + chartId + " pour " + userService);
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression de la configuration", e);
        }
    }
    
    /**
     * Met à jour l'ordre de position des graphiques
     */
    public static void updateChartPositions(String userService, Map<String, Integer> chartPositions) {
        String updateQuery = "UPDATE dashboard_chart_configs SET position_order = ? WHERE user_service = ? AND chart_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            
            for (Map.Entry<String, Integer> entry : chartPositions.entrySet()) {
                stmt.setInt(1, entry.getValue());
                stmt.setString(2, userService);
                stmt.setString(3, entry.getKey());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            
            LOGGER.info("Positions mises à jour pour " + chartPositions.size() + " graphiques");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la mise à jour des positions", e);
        }
    }
    
    /**
     * Nettoie les anciennes configurations (optionnel, pour la maintenance)
     */
    public static void cleanupOldConfigs(int daysOld) {
        String cleanupQuery = """
            DELETE FROM dashboard_chart_configs 
            WHERE is_default = FALSE 
            AND updated_date < DATE_SUB(NOW(), INTERVAL ? DAY)""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(cleanupQuery)) {
            
            stmt.setInt(1, daysOld);
            int deletedRows = stmt.executeUpdate();
            
            if (deletedRows > 0) {
                LOGGER.info("Nettoyage effectué: " + deletedRows + " anciennes configurations supprimées");
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors du nettoyage des anciennes configurations", e);
        }
    }
    
    /**
     * Classe pour encapsuler la configuration d'un graphique
     */
    public static class ChartConfig {
        private String chartId;
        private String chartType;
        private String chartTitle;
        private String tableName;
        private String columnName;
        private String tableName2;
        private String columnName2;
        private boolean isCrossTable;
        private boolean isDefault;
        private int positionOrder;
        
        public ChartConfig(String chartId, String chartType, String chartTitle,
                          String tableName, String columnName, String tableName2,
                          String columnName2, boolean isCrossTable, boolean isDefault,
                          int positionOrder) {
            this.chartId = chartId;
            this.chartType = chartType;
            this.chartTitle = chartTitle;
            this.tableName = tableName;
            this.columnName = columnName;
            this.tableName2 = tableName2;
            this.columnName2 = columnName2;
            this.isCrossTable = isCrossTable;
            this.isDefault = isDefault;
            this.positionOrder = positionOrder;
        }
        
        // Getters et setters
        public String getChartId() { return chartId; }
        public void setChartId(String chartId) { this.chartId = chartId; }
        
        public String getChartType() { return chartType; }
        public void setChartType(String chartType) { this.chartType = chartType; }
        
        public String getChartTitle() { return chartTitle; }
        public void setChartTitle(String chartTitle) { this.chartTitle = chartTitle; }
        
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        
        public String getTableName2() { return tableName2; }
        public void setTableName2(String tableName2) { this.tableName2 = tableName2; }
        
        public String getColumnName2() { return columnName2; }
        public void setColumnName2(String columnName2) { this.columnName2 = columnName2; }
        
        public boolean isCrossTable() { return isCrossTable; }
        public void setCrossTable(boolean crossTable) { isCrossTable = crossTable; }
        
        public boolean isDefault() { return isDefault; }
        public void setDefault(boolean aDefault) { isDefault = aDefault; }
        
        public int getPositionOrder() { return positionOrder; }
        public void setPositionOrder(int positionOrder) { this.positionOrder = positionOrder; }
        
        @Override
        public String toString() {
            return String.format("ChartConfig{id='%s', type='%s', title='%s', default=%s}", 
                    chartId, chartType, chartTitle, isDefault);
        }
    }
}