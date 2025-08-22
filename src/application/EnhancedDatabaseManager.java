package application;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnhancedDatabaseManager extends DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(EnhancedDatabaseManager.class.getName());
    
    static {
        // Initialiser les tables d'historique si elles n'existent pas
        initializeHistoryTables();
    }
    
    /**
     * Initialise les tables d'historique améliorées
     */
    private static void initializeHistoryTables() {
        try (Connection conn = getConnection()) {
            // Table d'historique des mises à jour améliorée
            String createEnhancedHistoryTable = 
                "CREATE TABLE IF NOT EXISTS historique_mises_a_jour_enhanced (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  tableName VARCHAR(100) NOT NULL," +
                "  updateDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  status VARCHAR(50) NOT NULL," +
                "  description TEXT," +
                "  recordsInserted INT DEFAULT 0," +
                "  recordsUpdated INT DEFAULT 0," +
                "  recordsUnchanged INT DEFAULT 0," +
                "  service VARCHAR(50) NOT NULL," +
                "  user VARCHAR(100) NOT NULL" +
                ")";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(createEnhancedHistoryTable);
                LOGGER.info("Table d'historique améliorée initialisée");
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation des tables d'historique", e);
        }
    }
    
    /**
     * CORRECTION: Enregistre une mise à jour améliorée dans l'historique avec transaction
     */
    public static int logEnhancedUpdate(String tableName, String status, String description, 
                                       int recordsInserted, int recordsUpdated, int recordsUnchanged,
                                       String service) {
        String sql = "INSERT INTO historique_mises_a_jour_enhanced " +
                     "(tableName, status, description, recordsInserted, recordsUpdated, recordsUnchanged, service, user) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = getConnection()) {
            // CORRECTION: Utiliser une transaction pour s'assurer de la cohérence
            conn.setAutoCommit(false);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                
                stmt.setString(1, tableName);
                stmt.setString(2, status);
                stmt.setString(3, description);
                stmt.setInt(4, recordsInserted);
                stmt.setInt(5, recordsUpdated);
                stmt.setInt(6, recordsUnchanged);
                stmt.setString(7, service);
                stmt.setString(8, getCurrentUser());
                
                int affectedRows = stmt.executeUpdate();
                
                if (affectedRows == 0) {
                    throw new SQLException("L'enregistrement de la mise à jour a échoué");
                }
                
                int updateId = -1;
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        updateId = generatedKeys.getInt(1);
                        
                        // Enregistrer aussi dans l'historique des modifications pour compatibilité
                        String detailsModification = String.format(
                            "Mise à jour CSV #%d: %d insertions, %d modifications, %d inchangés",
                            updateId, recordsInserted, recordsUpdated, recordsUnchanged
                        );
                        
                        logModification(tableName, "Mise à jour CSV", getCurrentUser(), detailsModification);
                        
                        // CORRECTION: Valider la transaction
                        conn.commit();
                        
                        LOGGER.info("Mise à jour enregistrée avec succès. ID: " + updateId);
                        
                        return updateId;
                    } else {
                        throw new SQLException("L'enregistrement de la mise à jour a échoué, aucun ID obtenu");
                    }
                }
                
            } catch (SQLException e) {
                // CORRECTION: Rollback en cas d'erreur
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'enregistrement de la mise à jour améliorée", e);
            return -1;
        }
    }
    
    /**
     * CORRECTION: Récupère l'historique complet des mises à jour améliorées avec ORDER BY
     */
    public static List<EnhancedUpdateRecord> getEnhancedUpdateHistory() {
        List<EnhancedUpdateRecord> updates = new ArrayList<>();
        String sql = "SELECT id, tableName, updateDate, status, description, " +
                     "recordsInserted, recordsUpdated, recordsUnchanged, service, user " +
                     "FROM historique_mises_a_jour_enhanced ORDER BY updateDate DESC, id DESC";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                updates.add(new EnhancedUpdateRecord(
                    rs.getInt("id"),
                    rs.getString("tableName"),
                    rs.getTimestamp("updateDate"),
                    rs.getString("status"),
                    rs.getString("description"),
                    rs.getInt("recordsInserted"),
                    rs.getInt("recordsUpdated"),
                    rs.getInt("recordsUnchanged"),
                    rs.getString("service"),
                    rs.getString("user")
                ));
            }
            
            LOGGER.info("Historique récupéré: " + updates.size() + " enregistrements");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de l'historique amélioré", e);
        }
        
        return updates;
    }
    
    /**
     * Obtient la dernière mise à jour pour une table spécifique
     */
    public static EnhancedUpdateRecord getLastEnhancedUpdateForTable(String tableName) {
        String sql = "SELECT id, tableName, updateDate, status, description, " +
                     "recordsInserted, recordsUpdated, recordsUnchanged, service, user " +
                     "FROM historique_mises_a_jour_enhanced WHERE tableName = ? " +
                     "ORDER BY updateDate DESC, id DESC LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, tableName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new EnhancedUpdateRecord(
                        rs.getInt("id"),
                        rs.getString("tableName"),
                        rs.getTimestamp("updateDate"),
                        rs.getString("status"),
                        rs.getString("description"),
                        rs.getInt("recordsInserted"),
                        rs.getInt("recordsUpdated"),
                        rs.getInt("recordsUnchanged"),
                        rs.getString("service"),
                        rs.getString("user")
                    );
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de la dernière mise à jour", e);
        }
        
        return null;
    }
    
    /**
     * Obtient l'historique des mises à jour pour un service spécifique
     */
    public static List<EnhancedUpdateRecord> getEnhancedUpdateHistoryForService(String service) {
        List<EnhancedUpdateRecord> updates = new ArrayList<>();
        String sql = "SELECT id, tableName, updateDate, status, description, " +
                     "recordsInserted, recordsUpdated, recordsUnchanged, service, user " +
                     "FROM historique_mises_a_jour_enhanced WHERE service = ? " +
                     "ORDER BY updateDate DESC, id DESC";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, service);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    updates.add(new EnhancedUpdateRecord(
                        rs.getInt("id"),
                        rs.getString("tableName"),
                        rs.getTimestamp("updateDate"),
                        rs.getString("status"),
                        rs.getString("description"),
                        rs.getInt("recordsInserted"),
                        rs.getInt("recordsUpdated"),
                        rs.getInt("recordsUnchanged"),
                        rs.getString("service"),
                        rs.getString("user")
                    ));
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de l'historique par service", e);
        }
        
        return updates;
    }
    
    /**
     * Identifie les tables périmées (non mises à jour depuis plus de 30 jours)
     */
    public static List<String> getOutdatedTables(String service) {
        List<String> outdatedTables = new ArrayList<>();
        List<String> serviceTables = ServicePermissions.getTablesForService(service);
        
        String sql = "SELECT tableName, MAX(updateDate) as lastUpdate " +
                     "FROM historique_mises_a_jour_enhanced " +
                     "WHERE service = ? AND tableName = ? " +
                     "GROUP BY tableName";
        
        try (Connection conn = getConnection()) {
            for (String table : serviceTables) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, service);
                    stmt.setString(2, table);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Timestamp lastUpdate = rs.getTimestamp("lastUpdate");
                            long daysSinceUpdate = (System.currentTimeMillis() - lastUpdate.getTime()) / (1000 * 60 * 60 * 24);
                            
                            if (daysSinceUpdate > 30) {
                                outdatedTables.add(table);
                            }
                        } else {
                            // Aucune mise à jour trouvée, considérer comme périmée
                            outdatedTables.add(table);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la vérification des tables périmées", e);
        }
        
        return outdatedTables;
    }
    
    /**
     * Obtient des statistiques de mise à jour pour un service
     */
    public static UpdateStatistics getUpdateStatistics(String service) {
        UpdateStatistics stats = new UpdateStatistics();
        
        String sql = "SELECT " +
                     "COUNT(*) as totalUpdates, " +
                     "SUM(recordsInserted) as totalInserted, " +
                     "SUM(recordsUpdated) as totalUpdated, " +
                     "SUM(recordsUnchanged) as totalUnchanged, " +
                     "COUNT(DISTINCT tableName) as tablesUpdated " +
                     "FROM historique_mises_a_jour_enhanced " +
                     "WHERE service = ? AND updateDate >= DATE_SUB(NOW(), INTERVAL 30 DAY)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, service);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.setTotalUpdates(rs.getInt("totalUpdates"));
                    stats.setTotalInserted(rs.getInt("totalInserted"));
                    stats.setTotalUpdated(rs.getInt("totalUpdated"));
                    stats.setTotalUnchanged(rs.getInt("totalUnchanged"));
                    stats.setTablesUpdated(rs.getInt("tablesUpdated"));
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des statistiques", e);
        }
        
        return stats;
    }
    
    /**
     * CORRECTION: Obtient l'utilisateur actuel (à adapter selon votre système d'authentification)
     */
    private static String getCurrentUser() {
        try {
            return UserSession.getCurrentUser();
        } catch (Exception e) {
            return System.getProperty("user.name", "Inconnu");
        }
    }
}

/**
 * Classe pour représenter un enregistrement de mise à jour amélioré
 */
class EnhancedUpdateRecord {
    private final int id;
    private final String tableName;
    private final Timestamp updateDate;
    private final String status;
    private final String description;
    private final int recordsInserted;
    private final int recordsUpdated;
    private final int recordsUnchanged;
    private final String service;
    private final String user;
    
    public EnhancedUpdateRecord(int id, String tableName, Timestamp updateDate, String status,
                               String description, int recordsInserted, int recordsUpdated, 
                               int recordsUnchanged, String service, String user) {
        this.id = id;
        this.tableName = tableName;
        this.updateDate = updateDate;
        this.status = status;
        this.description = description;
        this.recordsInserted = recordsInserted;
        this.recordsUpdated = recordsUpdated;
        this.recordsUnchanged = recordsUnchanged;
        this.service = service;
        this.user = user;
    }
    
    // Getters
    public int getId() { return id; }
    public String getTableName() { return tableName; }
    public Date getUpdateDate() { return updateDate; }
    public String getStatus() { return status; }
    public String getDescription() { return description; }
    public int getRecordsInserted() { return recordsInserted; }
    public int getRecordsUpdated() { return recordsUpdated; }
    public int getRecordsUnchanged() { return recordsUnchanged; }
    public String getService() { return service; }
    public String getUser() { return user; }
    
    public int getTotalRecords() { 
        return recordsInserted + recordsUpdated + recordsUnchanged; 
    }
    
    public boolean hasErrors() { 
        return "Échec".equals(status) || "Partiel".equals(status); 
    }
}

/**
 * Classe pour les statistiques de mise à jour
 */
class UpdateStatistics {
    private int totalUpdates;
    private int totalInserted;
    private int totalUpdated;
    private int totalUnchanged;
    private int tablesUpdated;
    
    // Getters et setters
    public int getTotalUpdates() { return totalUpdates; }
    public void setTotalUpdates(int totalUpdates) { this.totalUpdates = totalUpdates; }
    
    public int getTotalInserted() { return totalInserted; }
    public void setTotalInserted(int totalInserted) { this.totalInserted = totalInserted; }
    
    public int getTotalUpdated() { return totalUpdated; }
    public void setTotalUpdated(int totalUpdated) { this.totalUpdated = totalUpdated; }
    
    public int getTotalUnchanged() { return totalUnchanged; }
    public void setTotalUnchanged(int totalUnchanged) { this.totalUnchanged = totalUnchanged; }
    
    public int getTablesUpdated() { return tablesUpdated; }
    public void setTablesUpdated(int tablesUpdated) { this.tablesUpdated = tablesUpdated; }
    
    public int getTotalRecords() {
        return totalInserted + totalUpdated + totalUnchanged;
    }
}