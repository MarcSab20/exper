package application;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionnaire de base de données pour les mouvements de stock
 */
public class StockMovementManager {
    private static final Logger LOGGER = Logger.getLogger(StockMovementManager.class.getName());
    
    /**
     * Crée les tables nécessaires si elles n'existent pas
     */
    public static void initializeTables(Connection connection) {
        try {
            // Table des mouvements de stock
            String createMovementsTable = """
                CREATE TABLE IF NOT EXISTS stock_movements (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    stock_id INT NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    quantite INT NOT NULL,
                    description TEXT,
                    date_movement DATETIME NOT NULL,
                    utilisateur VARCHAR(100) NOT NULL,
                    quantite_avant INT NOT NULL,
                    quantite_apres INT NOT NULL,
                    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
                    INDEX idx_stock_id (stock_id),
                    INDEX idx_date_movement (date_movement)
                )""";
            
            // Ajouter les colonnes manquantes à la table stocks
            String alterStocksTable1 = """
                ALTER TABLE stocks 
                ADD COLUMN IF NOT EXISTS date_creation DATETIME DEFAULT CURRENT_TIMESTAMP""";
                
            String alterStocksTable2 = """
                ALTER TABLE stocks 
                ADD COLUMN IF NOT EXISTS quantite_initiale INT DEFAULT 0""";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(createMovementsTable);
                LOGGER.info("Table stock_movements créée/vérifiée avec succès");
                
                // Essayer d'ajouter les colonnes (peut échouer si elles existent déjà)
                try {
                    stmt.executeUpdate(alterStocksTable1);
                    stmt.executeUpdate(alterStocksTable2);
                    LOGGER.info("Colonnes ajoutées à la table stocks");
                } catch (SQLException e) {
                    // Normal si les colonnes existent déjà
                    LOGGER.info("Colonnes date_creation et quantite_initiale déjà présentes");
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation des tables", e);
        }
    }
    
    /**
     * Enregistre un mouvement de stock
     */
    public static boolean enregistrerMouvement(Connection connection, StockMovement mouvement) {
        String sql = """
            INSERT INTO stock_movements 
            (stock_id, type, quantite, description, date_movement, utilisateur, quantite_avant, quantite_apres)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, mouvement.getStockId());
            stmt.setString(2, mouvement.getType());
            stmt.setInt(3, mouvement.getQuantite());
            stmt.setString(4, mouvement.getDescription());
            stmt.setTimestamp(5, Timestamp.valueOf(mouvement.getDateMovement()));
            stmt.setString(6, mouvement.getUtilisateur());
            stmt.setInt(7, mouvement.getQuantiteAvant());
            stmt.setInt(8, mouvement.getQuantiteApres());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        mouvement.setId(generatedKeys.getInt(1));
                    }
                }
                
                LOGGER.info("Mouvement enregistré avec succès: " + mouvement.getType() + " de " + mouvement.getQuantite() + " unités");
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'enregistrement du mouvement: " + e.getMessage(), e);
            // Afficher le détail de l'erreur pour debug
            System.err.println("Erreur SQL détaillée: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Récupère tous les mouvements d'un stock
     */
    public static List<StockMovement> getMouvementsParStock(Connection connection, int stockId) {
        List<StockMovement> mouvements = new ArrayList<>();
        
        String sql = """
            SELECT id, stock_id, type, quantite, description, date_movement, 
                   utilisateur, quantite_avant, quantite_apres
            FROM stock_movements 
            WHERE stock_id = ? 
            ORDER BY date_movement DESC""";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, stockId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockMovement mouvement = new StockMovement(
                        rs.getInt("id"),
                        rs.getInt("stock_id"),
                        rs.getString("type"),
                        rs.getInt("quantite"),
                        rs.getString("description"),
                        rs.getTimestamp("date_movement").toLocalDateTime(),
                        rs.getString("utilisateur"),
                        rs.getInt("quantite_avant"),
                        rs.getInt("quantite_apres")
                    );
                    mouvements.add(mouvement);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des mouvements", e);
        }
        
        return mouvements;
    }
    
    /**
     * Met à jour la quantité d'un stock et enregistre le mouvement
     */
    public static boolean effectuerMouvement(Connection connection, int stockId, String type, 
            int quantiteMouvement, String description) {
    	try {
    		connection.setAutoCommit(false);
	
    		// Récupérer les informations actuelles du stock SANS le trigger
    		String selectSql = "SELECT quantite FROM stocks WHERE id = ? FOR UPDATE";
    		int quantiteActuelle = 0;
	
    		try (PreparedStatement selectStmt = connection.prepareStatement(selectSql)) {
    			selectStmt.setInt(1, stockId);
    			try (ResultSet rs = selectStmt.executeQuery()) {
    				if (rs.next()) {
    					quantiteActuelle = rs.getInt("quantite");
    				} else {
    					throw new SQLException("Stock non trouvé avec l'ID: " + stockId);
    				}
    			}
    		}
	
    		// Calculer la nouvelle quantité
    		int nouvelleQuantite;
    		if ("APPROVISIONNEMENT".equals(type)) {
    			nouvelleQuantite = quantiteActuelle + quantiteMouvement;
    		} else {
    			nouvelleQuantite = quantiteActuelle - quantiteMouvement;
    			if (nouvelleQuantite < 0) {
    				connection.rollback();
    				throw new SQLException("Quantité insuffisante pour le retrait demandé");
    			}
    		}
    		
    		// Mettre à jour la quantité du stock D'ABORD
    		String updateSql = "UPDATE stocks SET quantite = ? WHERE id = ?";
    		try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
    			updateStmt.setInt(1, nouvelleQuantite);
    			updateStmt.setInt(2, stockId);
    			updateStmt.executeUpdate();
    		}
    		
    		// Ensuite créer et enregistrer le mouvement SANS trigger
    		StockMovement mouvement = new StockMovement(stockId, type, quantiteMouvement, 
    				description, quantiteActuelle, nouvelleQuantite);
	
    		// Enregistrement direct sans trigger
    		String insertMovementSql = """
    				INSERT INTO stock_movements 
    				(stock_id, type, quantite, description, date_movement, utilisateur, quantite_avant, quantite_apres)
    				VALUES (?, ?, ?, ?, NOW(), ?, ?, ?)""";
	
    		try (PreparedStatement insertStmt = connection.prepareStatement(insertMovementSql)) {
    			insertStmt.setInt(1, stockId);
    			insertStmt.setString(2, type);
    			insertStmt.setInt(3, quantiteMouvement);
    			insertStmt.setString(4, description);
    			insertStmt.setString(5, UserSession.getCurrentUser());
    			insertStmt.setInt(6, quantiteActuelle);
    			insertStmt.setInt(7, nouvelleQuantite);
    			
    			insertStmt.executeUpdate();
    		}
    		
    		// Enregistrer dans l'historique
    		String details = String.format("Mouvement %s: %s %d unités - %s", 
    				type, 
    				"APPROVISIONNEMENT".equals(type) ? "+" : "-",
    						quantiteMouvement,
    						description);
    		HistoryManager.logUpdate("Stocks", details);
	
    		connection.commit();
    		connection.setAutoCommit(true);
	
    		LOGGER.info("Mouvement effectué avec succès: " + details);
    		return true;
    		
    	} catch (SQLException e) {
    		try {
    			connection.rollback();
    			connection.setAutoCommit(true);
    		} catch (SQLException rollbackEx) {
    			LOGGER.log(Level.WARNING, "Erreur lors du rollback", rollbackEx);
    		}
    		LOGGER.log(Level.SEVERE, "Erreur lors de l'exécution du mouvement", e);
    		return false;
    	}
	}
    
    /**
     * Récupère la fiche complète d'un équipement
     */
    public static EquipmentCard getEquipmentCard(Connection connection, Stock stock) {
        try {
            // Récupérer les informations de création
            String stockInfoSql = """
                SELECT date_creation, quantite_initiale 
                FROM stocks 
                WHERE id = ?""";
            
            LocalDateTime dateCreation = LocalDateTime.now();
            int quantiteInitiale = 0;
            
            try (PreparedStatement stmt = connection.prepareStatement(stockInfoSql)) {
                stmt.setInt(1, stock.getId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp ts = rs.getTimestamp("date_creation");
                        if (ts != null) {
                            dateCreation = ts.toLocalDateTime();
                        }
                        quantiteInitiale = rs.getInt("quantite_initiale");
                    }
                }
            }
            
            // Récupérer tous les mouvements
            List<StockMovement> mouvements = getMouvementsParStock(connection, stock.getId());
            
            return new EquipmentCard(stock, dateCreation, quantiteInitiale, mouvements);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération de la fiche équipement", e);
            return new EquipmentCard(stock, LocalDateTime.now(), 0, new ArrayList<>());
        }
    }
    
    /**
     * Supprime un stock et tous ses mouvements associés
     */
    public static boolean supprimerStock(Connection connection, int stockId, String designation) {
        try {
            connection.setAutoCommit(false);
            
            // Supprimer d'abord les mouvements (CASCADE devrait s'en occuper, mais soyons explicites)
            String deleteMovementsSql = "DELETE FROM stock_movements WHERE stock_id = ?";
            try (PreparedStatement deleteMovementsStmt = connection.prepareStatement(deleteMovementsSql)) {
                deleteMovementsStmt.setInt(1, stockId);
                int mouvementsSupprimes = deleteMovementsStmt.executeUpdate();
                LOGGER.info("Suppression de " + mouvementsSupprimes + " mouvements pour le stock " + designation);
            }
            
            // Supprimer le stock
            String deleteStockSql = "DELETE FROM stocks WHERE id = ?";
            try (PreparedStatement deleteStockStmt = connection.prepareStatement(deleteStockSql)) {
                deleteStockStmt.setInt(1, stockId);
                int stocksSupprimes = deleteStockStmt.executeUpdate();
                
                if (stocksSupprimes > 0) {
                    // Enregistrer dans l'historique
                    String details = String.format("Suppression de l'équipement: %s (ID: %d)", designation, stockId);
                    HistoryManager.logDeletion("Stocks", details);
                    
                    connection.commit();
                    connection.setAutoCommit(true);
                    return true;
                }
            }
            
            connection.rollback();
            connection.setAutoCommit(true);
            return false;
            
        } catch (SQLException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException rollbackEx) {
                LOGGER.log(Level.WARNING, "Erreur lors du rollback", rollbackEx);
            }
            LOGGER.log(Level.SEVERE, "Erreur lors de la suppression du stock", e);
            return false;
        }
    }
}