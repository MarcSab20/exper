/**
 * CORRECTION COMPLÈTE - Gestion sécurisée des connexions de base de données
 * Le problème vient du partage de connexions entre threads
 */

package application;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnhancedDatabaseManager extends DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(EnhancedDatabaseManager.class.getName());
    
    // CORRECTION: Paramètres de connexion pour éviter les connexions fermées
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master?autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    static {
        // Initialiser les tables d'historique si elles n'existent pas
        initializeHistoryTables();
    }
    
    /**
     * CORRECTION: Obtenir une nouvelle connexion sécurisée à chaque appel
     */
    private static Connection getEnhancedConnection() throws SQLException {
        try {
            // S'assurer que le driver est chargé
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // Vérifier que la connexion est valide
            if (conn.isValid(5)) { // Timeout de 5 secondes
                return conn;
            } else {
                throw new SQLException("Connexion invalide obtenue");
            }
            
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver MySQL introuvable", e);
        }
    }
    
    /**
     * Initialise les tables d'historique améliorées
     */
    private static void initializeHistoryTables() {
        try (Connection conn = getEnhancedConnection()) {
            String createEnhancedHistoryTable = 
                "CREATE TABLE IF NOT EXISTS historique_mises_a_jour_enhanced (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  tableName VARCHAR(100) NOT NULL," +
                "  updateDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  status VARCHAR(50) NOT NULL," +
                "  recordsInserted INT DEFAULT 0," +
                "  recordsUpdated INT DEFAULT 0," +
                "  recordsUnchanged INT DEFAULT 0," +
                "  service VARCHAR(50) NOT NULL," +
                "  INDEX idx_table_service (tableName, service)," +
                "  INDEX idx_update_date (updateDate)" +
                ")";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(createEnhancedHistoryTable);
                LOGGER.info("Table d'historique améliorée initialisée avec succès");
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation des tables d'historique", e);
        }
    }
    
    /**
     * CORRECTION: Enregistre une mise à jour avec gestion sécurisée des connexions
     */
    public static int logEnhancedUpdate(String tableName, String status,
                                       int recordsInserted, int recordsUpdated, int recordsUnchanged,
                                       String service) {
        
        // CORRECTION: Nouvelle connexion dédiée pour cette opération
        try (Connection conn = getEnhancedConnection()) {
            
            String sql = "INSERT INTO historique_mises_a_jour_enhanced " +
                         "(tableName, status, recordsInserted, recordsUpdated, recordsUnchanged, service) " +
                         "VALUES (?, ?, ?, ?, ?, ?)";
            
            // CORRECTION: Vérifier d'abord si la connexion est valide
            if (!conn.isValid(5)) {
                LOGGER.severe("Connexion invalide avant l'insertion");
                return -1;
            }
            
            conn.setAutoCommit(false);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                
                stmt.setString(1, tableName);
                stmt.setString(2, status);
                stmt.setInt(3, recordsInserted);
                stmt.setInt(4, recordsUpdated);
                stmt.setInt(5, recordsUnchanged);
                stmt.setString(6, service != null ? service : "Inconnu");
                
                int affectedRows = stmt.executeUpdate();
                
                if (affectedRows == 0) {
                    conn.rollback();
                    throw new SQLException("L'enregistrement de la mise à jour a échoué - aucune ligne affectée");
                }
                
                int updateId = -1;
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        updateId = generatedKeys.getInt(1);
                        
                        // CORRECTION: Enregistrer dans l'historique avec une connexion séparée
                        logModificationSeparately(tableName, recordsInserted, recordsUpdated, recordsUnchanged, updateId);
                        
                        conn.commit();
                        
                        LOGGER.info("Mise à jour enregistrée avec succès. ID: " + updateId + 
                                   " pour table: " + tableName + " service: " + service);
                        
                        return updateId;
                    } else {
                        conn.rollback();
                        throw new SQLException("Aucun ID généré pour la mise à jour");
                    }
                }
                
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    LOGGER.log(Level.WARNING, "Erreur lors du rollback", rollbackEx);
                }
                throw e;
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'enregistrement de la mise à jour améliorée", e);
            return -1;
        }
    }
    
    /**
     * CORRECTION: Enregistrer dans l'historique des modifications avec une connexion séparée
     */
    private static void logModificationSeparately(String tableName, int recordsInserted, 
                                                 int recordsUpdated, int recordsUnchanged, int updateId) {
        try (Connection separateConn = getEnhancedConnection()) {
            String detailsModification = String.format(
                "Mise à jour CSV #%d: %d insertions, %d modifications, %d inchangés",
                updateId, recordsInserted, recordsUpdated, recordsUnchanged
            );
            
            String sql = "INSERT INTO historique_modifications " +
                        "(date, table_modifiee, type_modification, utilisateur, details) " +
                        "VALUES (NOW(), ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = separateConn.prepareStatement(sql)) {
                stmt.setString(1, tableName);
                stmt.setString(2, "Mise à jour CSV");
                stmt.setString(3, getCurrentUser());
                stmt.setString(4, detailsModification);
                
                stmt.executeUpdate();
                LOGGER.info("Historique des modifications mis à jour pour la mise à jour #" + updateId);
                
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors de l'enregistrement dans l'historique des modifications", e);
            // Ne pas faire échouer la mise à jour principale pour cette erreur secondaire
        }
    }
    
    /**
     * CORRECTION: Récupère l'historique avec gestion sécurisée des connexions
     */
    public static List<EnhancedUpdateRecord> getEnhancedUpdateHistory() {
        List<EnhancedUpdateRecord> updates = new ArrayList<>();
        
        String sql = "SELECT id, tableName, updateDate, status, description, " +
                     "recordsInserted, recordsUpdated, recordsUnchanged, service " +
                     "FROM historique_mises_a_jour_enhanced " +
                     "ORDER BY updateDate DESC, id DESC " +
                     "LIMIT 100"; // CORRECTION: Limiter pour éviter les timeouts
        
        try (Connection conn = getEnhancedConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
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
                    getCurrentUser()
                ));
            }
            
            LOGGER.info("Historique récupéré: " + updates.size() + " enregistrements");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de l'historique amélioré", e);
        }
        
        return updates;
    }
    
    /**
     * CORRECTION: Obtient la dernière mise à jour avec gestion sécurisée
     */
    public static EnhancedUpdateRecord getLastEnhancedUpdateForTable(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            return null;
        }
        
        String sql = "SELECT id, tableName, updateDate, status, description, " +
                     "recordsInserted, recordsUpdated, recordsUnchanged, service " +
                     "FROM historique_mises_a_jour_enhanced " +
                     "WHERE tableName = ? " +
                     "ORDER BY updateDate DESC, id DESC LIMIT 1";
        
        try (Connection conn = getEnhancedConnection();
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
                        getCurrentUser()
                    );
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de la dernière mise à jour pour " + tableName, e);
        }
        
        return null;
    }
    
    /**
     * CORRECTION: Test de connexion pour débogage
     */
    public static boolean testConnection() {
        try (Connection conn = getEnhancedConnection()) {
            boolean isValid = conn.isValid(10);
            LOGGER.info("Test de connexion: " + (isValid ? "SUCCÈS" : "ÉCHEC"));
            return isValid;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Test de connexion échoué", e);
            return false;
        }
    }
    
    /**
     * CORRECTION: Obtient l'utilisateur actuel de manière sécurisée
     */
    private static String getCurrentUser() {
        try {
            String currentUser = UserSession.getCurrentUser();
            return (currentUser != null && !currentUser.trim().isEmpty()) ? currentUser : "Système";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la récupération de l'utilisateur actuel", e);
            return "Système";
        }
    }
    
    /**
     * CORRECTION: Nettoyage des anciennes entrées pour éviter la surcharge
     */
    public static void cleanupOldHistory(int daysToKeep) {
        String sql = "DELETE FROM historique_mises_a_jour_enhanced " +
                     "WHERE updateDate < DATE_SUB(NOW(), INTERVAL ? DAY)";
        
        try (Connection conn = getEnhancedConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, daysToKeep);
            int deletedRows = stmt.executeUpdate();
            
            if (deletedRows > 0) {
                LOGGER.info("Nettoyage automatique: " + deletedRows + " anciens enregistrements supprimés");
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors du nettoyage de l'historique", e);
        }
    }
}

/**
 * CORRECTION: Classe EnhancedUpdateRecord avec gestion d'erreurs
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
        this.tableName = tableName != null ? tableName : "Inconnu";
        this.updateDate = updateDate != null ? updateDate : new Timestamp(System.currentTimeMillis());
        this.status = status != null ? status : "Inconnu";
        this.description = description != null ? description : "";
        this.recordsInserted = Math.max(0, recordsInserted);
        this.recordsUpdated = Math.max(0, recordsUpdated);
        this.recordsUnchanged = Math.max(0, recordsUnchanged);
        this.service = service != null ? service : "Inconnu";
        this.user = user != null ? user : "Système";
    }
    
    // Getters avec vérifications de sécurité
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
    
    @Override
    public String toString() {
        return String.format("EnhancedUpdateRecord{id=%d, table='%s', status='%s', total=%d}", 
                           id, tableName, status, getTotalRecords());
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