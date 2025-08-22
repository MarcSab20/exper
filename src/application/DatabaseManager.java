package application;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";  
    private static final String DB_PASSWORD = "29Papa278.";  
    
    private static Connection connection;
    
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            } catch (ClassNotFoundException e) {
                LOGGER.log(Level.SEVERE, "Driver MySQL introuvable", e);
                throw new SQLException("Driver MySQL introuvable", e);
            }
        }
        return connection;
    }
    
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Erreur lors de la fermeture de la connexion", e);
            }
        }
    }
    
    // Méthode pour enregistrer une connexion
    public static void logConnexion(String utilisateur, String action, String statut) {
        String sql = "INSERT INTO historique_connexions (date, utilisateur, action, statut) VALUES (NOW(), ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, utilisateur);
            stmt.setString(2, action);
            stmt.setString(3, statut);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'enregistrement de la connexion", e);
        }
    }
    
    // Méthode pour enregistrer une modification
    public static void logModification(String tableModifiee, String typeModif, String utilisateur, String details) {
        String sql = "INSERT INTO historique_modifications (date, table_modifiee, type_modification, utilisateur, details) VALUES (NOW(), ?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, tableModifiee);
            stmt.setString(2, typeModif);
            stmt.setString(3, utilisateur);
            stmt.setString(4, details);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'enregistrement de la modification", e);
        }
    }
    
 // Méthode pour enregistrer une mise à jour
    public static int logUpdate(String tableName, String status, String description, int recordsUpdated, int recordsInserted) {
        String sql = "INSERT INTO historique_mises_a_jour (tableName, updateDate, status, description, recordsUpdated, recordsInserted) " +
                     "VALUES (?, NOW(), ?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, tableName);
            stmt.setString(2, status);
            stmt.setString(3, description);
            stmt.setInt(4, recordsUpdated);
            stmt.setInt(5, recordsInserted);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("La création de l'enregistrement de mise à jour a échoué");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("La création de l'enregistrement de mise à jour a échoué, aucun ID obtenu");
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'enregistrement de la mise à jour", e);
            return -1;
        }
    }
    
    // Récupérer l'historique des mises à jour
    public static List<UpdateRecord> getUpdateHistory() {
        List<UpdateRecord> updates = new ArrayList<>();
        String sql = "SELECT id, tableName, updateDate, status, description, recordsUpdated, recordsInserted " +
                     "FROM historique_mises_a_jour ORDER BY updateDate DESC";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                updates.add(new UpdateRecord(
                    rs.getInt("id"),
                    rs.getString("tableName"),
                    rs.getTimestamp("updateDate"),
                    rs.getString("status"),
                    rs.getString("description"),
                    rs.getInt("recordsUpdated"),
                    rs.getInt("recordsInserted")
                ));
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de l'historique des mises à jour", e);
        }
        
        return updates;
    }
    
    // Obtenir la dernière mise à jour pour une table spécifique
    public static UpdateRecord getLastUpdateForTable(String tableName) {
        String sql = "SELECT id, tableName, updateDate, status, description, recordsUpdated, recordsInserted " +
                     "FROM historique_mises_a_jour WHERE tableName = ? ORDER BY updateDate DESC LIMIT 1";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, tableName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new UpdateRecord(
                        rs.getInt("id"),
                        rs.getString("tableName"),
                        rs.getTimestamp("updateDate"),
                        rs.getString("status"),
                        rs.getString("description"),
                        rs.getInt("recordsUpdated"),
                        rs.getInt("recordsInserted")
                    );
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de la dernière mise à jour", e);
        }
        
        return null;
    }
    
    // Obtenir le schéma d'une table (liste des colonnes et types)
    public static List<ColumnInfo> getTableSchema(String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        String sql = "DESCRIBE " + tableName;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String name = rs.getString("Field");
                String type = rs.getString("Type");
                boolean isNullable = "YES".equalsIgnoreCase(rs.getString("Null"));
                String key = rs.getString("Key");
                boolean isPrimaryKey = "PRI".equalsIgnoreCase(key);
                
                columns.add(new ColumnInfo(name, type, isNullable, isPrimaryKey));
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération du schéma de la table", e);
        }
        
        return columns;
    }
}

// Classe pour stocker les informations d'une colonne
class ColumnInfo {
    private final String name;
    private final String type;
    private final boolean nullable;
    private final boolean primaryKey;
    
    public ColumnInfo(String name, String type, boolean nullable, boolean primaryKey) {
        this.name = name;
        this.type = type;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
    }
    
    public String getName() { return name; }
    public String getType() { return type; }
    public boolean isNullable() { return nullable; }
    public boolean isPrimaryKey() { return primaryKey; }
}