package application;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service pour la persistance des configurations de graphiques personnalisés - VERSION AMÉLIORÉE
 */
public class ChartPersistenceService {
    private static final Logger LOGGER = Logger.getLogger(ChartPersistenceService.class.getName());
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    // Cache pour améliorer les performances
    private static final Map<String, List<ChartConfig>> CONFIG_CACHE = new HashMap<>();
    private static long lastCacheUpdate = 0;
    private static final long CACHE_VALIDITY_MS = 60000; // 1 minute
    
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
                INDEX idx_position_order (position_order),
                UNIQUE KEY unique_user_chart (user_service, chart_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""";
        
        String createHistoryTableQuery = """
            CREATE TABLE IF NOT EXISTS dashboard_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_service VARCHAR(100) NOT NULL,
                action_type VARCHAR(50) NOT NULL,
                chart_id VARCHAR(100),
                chart_title VARCHAR(200),
                action_details TEXT,
                action_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                user_name VARCHAR(100),
                INDEX idx_user_service_date (user_service, action_date),
                INDEX idx_action_type (action_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate(createTableQuery);
            stmt.executeUpdate(createHistoryTableQuery);
            LOGGER.info("Tables dashboard_chart_configs et dashboard_history initialisées avec succès");
            
            // Insérer les graphiques par défaut si pas encore présents
            insertDefaultCharts();
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation des tables", e);
        }
    }
    
    /**
     * Insère les graphiques par défaut pour tous les services
     */
    private static void insertDefaultCharts() {
        List<String> services = Arrays.asList("admin", "Admin", "Logistique", "Opérations", "Ressources Humaines");
        
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
     * AMÉLIORATION: Insère les graphiques par défaut pour un service donné
     */
    private static void insertDefaultChartsForService(String service) throws SQLException {
        List<ChartConfig> defaultCharts = Arrays.asList(
            new ChartConfig("default_sexe", "camembert", "Répartition par Sexe", 
                          "identite_personnelle", "sexe", null, null, false, true, 1),
            new ChartConfig("default_grade", "histogramme", "Répartition par Grade", 
                          "grade_actuel", "rang", null, null, false, true, 2),
            new ChartConfig("default_region", "camembert", "Répartition par Région", 
                          "identite_culturelle", "region_origine", null, null, false, true, 3),
            new ChartConfig("default_religion", "histogramme", "Répartition par Religion", 
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
            
            // Enregistrer dans l'historique
            logToHistory(service, "CREATE", "default_batch", "Graphiques par défaut", 
                        "Création de " + defaultCharts.size() + " graphiques par défaut");
            
            LOGGER.info("Graphiques par défaut insérés pour le service: " + service);
        }
    }
    
    /**
     * AMÉLIORATION: Sauvegarde une configuration de graphique avec validation
     */
    public static boolean saveChartConfig(String userService, ChartConfig config) {
        if (userService == null || config == null) {
            LOGGER.warning("Service ou configuration null lors de la sauvegarde");
            return false;
        }
        
        // Valider la configuration
        if (!isValidChartConfig(config)) {
            LOGGER.warning("Configuration de graphique invalide: " + config);
            return false;
        }
        
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
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Enregistrer dans l'historique
                String actionType = config.isDefault() ? "CREATE_DEFAULT" : "CREATE";
                logToHistory(userService, actionType, config.getChartId(), config.getChartTitle(),
                           "Configuration sauvegardée: " + config.getChartTitle());
                
                // Invalider le cache
                invalidateCache(userService);
                
                LOGGER.info("Configuration sauvegardée: " + config.getChartId() + " pour " + userService);
                return true;
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la sauvegarde de la configuration", e);
        }
        
        return false;
    }
    
    /**
     * AMÉLIORATION: Charge toutes les configurations avec cache
     */
    public static List<ChartConfig> loadChartConfigs(String userService) {
        if (userService == null) {
            return new ArrayList<>();
        }
        
        // Vérifier le cache
        if (isCacheValid() && CONFIG_CACHE.containsKey(userService)) {
            LOGGER.fine("Configuration chargée depuis le cache pour: " + userService);
            return new ArrayList<>(CONFIG_CACHE.get(userService));
        }
        
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
            
            // Mettre en cache
            CONFIG_CACHE.put(userService, new ArrayList<>(configs));
            lastCacheUpdate = System.currentTimeMillis();
            
            LOGGER.info("Chargement de " + configs.size() + " configurations pour " + userService);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement des configurations", e);
        }
        
        return configs;
    }
    
    /**
     * AMÉLIORATION: Supprime une configuration avec vérifications
     */
    public static boolean deleteChartConfig(String userService, String chartId) {
        if (userService == null || chartId == null) {
            LOGGER.warning("Service ou chartId null lors de la suppression");
            return false;
        }
        
        // Vérifier d'abord si la configuration existe et si elle peut être supprimée
        ChartConfig existingConfig = getChartConfig(userService, chartId);
        if (existingConfig == null) {
            LOGGER.warning("Configuration non trouvée: " + chartId);
            return false;
        }
        
        if (existingConfig.isDefault()) {
            LOGGER.warning("Tentative de suppression d'un graphique par défaut: " + chartId);
            return false;
        }
        
        String deleteQuery = "DELETE FROM dashboard_chart_configs WHERE user_service = ? AND chart_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(deleteQuery)) {
            
            stmt.setString(1, userService);
            stmt.setString(2, chartId);
            
            int deletedRows = stmt.executeUpdate();
            
            if (deletedRows > 0) {
                // Enregistrer dans l'historique
                logToHistory(userService, "DELETE", chartId, existingConfig.getChartTitle(),
                           "Configuration supprimée: " + existingConfig.getChartTitle());
                
                // Invalider le cache
                invalidateCache(userService);
                
                LOGGER.info("Configuration supprimée: " + chartId + " pour " + userService);
                return true;
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression de la configuration", e);
        }
        
        return false;
    }
    
    /**
     * NOUVEAU: Obtient une configuration spécifique
     */
    public static ChartConfig getChartConfig(String userService, String chartId) {
        String query = """
            SELECT chart_id, chart_type, chart_title, table_name, column_name, 
                   table_name_2, column_name_2, is_cross_table, is_default, position_order
            FROM dashboard_chart_configs 
            WHERE user_service = ? AND chart_id = ?""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, userService);
            stmt.setString(2, chartId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ChartConfig(
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
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de la configuration", e);
        }
        
        return null;
    }
    
    /**
     * Met à jour l'ordre de position des graphiques
     */
    public static boolean updateChartPositions(String userService, Map<String, Integer> chartPositions) {
        if (userService == null || chartPositions == null || chartPositions.isEmpty()) {
            return false;
        }
        
        String updateQuery = "UPDATE dashboard_chart_configs SET position_order = ? WHERE user_service = ? AND chart_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            
            conn.setAutoCommit(false);
            
            try {
                for (Map.Entry<String, Integer> entry : chartPositions.entrySet()) {
                    stmt.setInt(1, entry.getValue());
                    stmt.setString(2, userService);
                    stmt.setString(3, entry.getKey());
                    stmt.addBatch();
                }
                
                stmt.executeBatch();
                conn.commit();
                
                // Invalider le cache
                invalidateCache(userService);
                
                LOGGER.info("Positions mises à jour pour " + chartPositions.size() + " graphiques");
                return true;
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la mise à jour des positions", e);
            return false;
        }
    }
    
    /**
     * NOUVEAU: Obtient les statistiques des graphiques par service
     */
    public static Map<String, ChartServiceStats> getServiceStats() {
        Map<String, ChartServiceStats> stats = new HashMap<>();
        
        String query = """
            SELECT 
                user_service,
                COUNT(*) as total_charts,
                SUM(CASE WHEN is_default = TRUE THEN 1 ELSE 0 END) as default_charts,
                SUM(CASE WHEN is_default = FALSE THEN 1 ELSE 0 END) as custom_charts,
                SUM(CASE WHEN is_cross_table = TRUE THEN 1 ELSE 0 END) as cross_charts,
                MAX(updated_date) as last_updated
            FROM dashboard_chart_configs
            GROUP BY user_service""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String service = rs.getString("user_service");
                ChartServiceStats serviceStat = new ChartServiceStats(
                    service,
                    rs.getInt("total_charts"),
                    rs.getInt("default_charts"),
                    rs.getInt("custom_charts"),
                    rs.getInt("cross_charts"),
                    rs.getTimestamp("last_updated")
                );
                stats.put(service, serviceStat);
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des statistiques", e);
        }
        
        return stats;
    }
    
    /**
     * Nettoie les anciennes configurations (optionnel, pour la maintenance)
     */
    public static int cleanupOldConfigs(int daysOld) {
        String cleanupQuery = """
            DELETE FROM dashboard_chart_configs 
            WHERE is_default = FALSE 
            AND updated_date < DATE_SUB(NOW(), INTERVAL ? DAY)""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(cleanupQuery)) {
            
            stmt.setInt(1, daysOld);
            int deletedRows = stmt.executeUpdate();
            
            if (deletedRows > 0) {
                // Invalider tout le cache
                CONFIG_CACHE.clear();
                LOGGER.info("Nettoyage effectué: " + deletedRows + " anciennes configurations supprimées");
            }
            
            return deletedRows;
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors du nettoyage des anciennes configurations", e);
            return 0;
        }
    }
    
    /**
     * NOUVEAU: Valide une configuration de graphique
     */
    private static boolean isValidChartConfig(ChartConfig config) {
        if (config.getChartId() == null || config.getChartId().trim().isEmpty()) {
            return false;
        }
        
        if (config.getChartType() == null || config.getChartType().trim().isEmpty()) {
            return false;
        }
        
        if (config.getTableName() == null || config.getTableName().trim().isEmpty()) {
            return false;
        }
        
        if (config.isCrossTable()) {
            return config.getTableName2() != null && !config.getTableName2().trim().isEmpty() &&
                   config.getColumnName2() != null && !config.getColumnName2().trim().isEmpty();
        }
        
        return config.getColumnName() != null && !config.getColumnName().trim().isEmpty();
    }
    
    /**
     * Enregistre une action dans l'historique
     */
    private static void logToHistory(String userService, String actionType, String chartId, 
                                   String chartTitle, String details) {
        String insertHistoryQuery = """
            INSERT INTO dashboard_history 
            (user_service, action_type, chart_id, chart_title, action_details, user_name)
            VALUES (?, ?, ?, ?, ?, ?)""";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(insertHistoryQuery)) {
            
            stmt.setString(1, userService);
            stmt.setString(2, actionType);
            stmt.setString(3, chartId);
            stmt.setString(4, chartTitle);
            stmt.setString(5, details);
            stmt.setString(6, UserSession.getCurrentUser());
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors de l'enregistrement dans l'historique", e);
        }
    }
    
    /**
     * Vérifie si le cache est valide
     */
    private static boolean isCacheValid() {
        return System.currentTimeMillis() - lastCacheUpdate < CACHE_VALIDITY_MS;
    }
    
    /**
     * Invalide le cache pour un service
     */
    private static void invalidateCache(String userService) {
        CONFIG_CACHE.remove(userService);
    }
    
    /**
     * Invalide complètement le cache
     */
    public static void invalidateAllCache() {
        CONFIG_CACHE.clear();
        lastCacheUpdate = 0;
        LOGGER.info("Cache complètement invalidé");
    }
    
    /**
     * Classe pour les statistiques par service
     */
    public static class ChartServiceStats {
        private final String service;
        private final int totalCharts;
        private final int defaultCharts;
        private final int customCharts;
        private final int crossCharts;
        private final Timestamp lastUpdated;
        
        public ChartServiceStats(String service, int totalCharts, int defaultCharts, 
                               int customCharts, int crossCharts, Timestamp lastUpdated) {
            this.service = service;
            this.totalCharts = totalCharts;
            this.defaultCharts = defaultCharts;
            this.customCharts = customCharts;
            this.crossCharts = crossCharts;
            this.lastUpdated = lastUpdated;
        }
        
        public String getService() { return service; }
        public int getTotalCharts() { return totalCharts; }
        public int getDefaultCharts() { return defaultCharts; }
        public int getCustomCharts() { return customCharts; }
        public int getCrossCharts() { return crossCharts; }
        public Timestamp getLastUpdated() { return lastUpdated; }
        
        @Override
        public String toString() {
            return String.format("ChartServiceStats{service='%s', total=%d, default=%d, custom=%d, cross=%d}",
                               service, totalCharts, defaultCharts, customCharts, crossCharts);
        }
    }
    
    /**
     * Classe pour encapsuler la configuration d'un graphique - VERSION AMÉLIORÉE
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
            return String.format("ChartConfig{id='%s', type='%s', title='%s', default=%s, cross=%s}", 
                    chartId, chartType, chartTitle, isDefault, isCrossTable);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ChartConfig that = (ChartConfig) obj;
            return Objects.equals(chartId, that.chartId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(chartId);
        }
    }
}