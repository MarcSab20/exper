package application;

import javafx.application.Platform;
import java.sql.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.*;

/**
 * Gestionnaire de mise à jour automatique du dashboard
 * Surveille les changements dans la base de données et déclenche les actualisations
 */
public class DashboardAutoRefreshManager {
    private static final Logger LOGGER = Logger.getLogger(DashboardAutoRefreshManager.class.getName());
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    // Tables à surveiller pour les changements
    private static final List<String> MONITORED_TABLES = Arrays.asList(
        "identite_personnelle",
        "grade_actuel", 
        "identite_culturelle",
        "formation_actuelle",
        "affectation_actuelle"
    );
    
    private final ScheduledExecutorService scheduler;
    private final Map<String, Long> lastModificationTimes;
    private final List<Consumer<String>> changeListeners;
    private boolean isRunning = false;
    
    // Configuration
    private int checkIntervalMinutes = 2; // Vérification toutes les 2 minutes
    private boolean enableSmartRefresh = true; // Actualisation intelligente
    
    public DashboardAutoRefreshManager() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.lastModificationTimes = new ConcurrentHashMap<>();
        this.changeListeners = new ArrayList<>();
        
        // Initialiser les temps de modification
        initializeModificationTimes();
    }
    
    /**
     * Démarre le gestionnaire de mise à jour automatique
     */
    public void start() {
        if (isRunning) {
            LOGGER.warning("Le gestionnaire de mise à jour automatique est déjà en cours d'exécution");
            return;
        }
        
        isRunning = true;
        
        // Programmer la vérification périodique des changements
        scheduler.scheduleWithFixedDelay(this::checkForChanges, 
                                       1, // Démarrage après 1 minute
                                       checkIntervalMinutes, 
                                       TimeUnit.MINUTES);
        
        // Programmer la maintenance périodique
        scheduler.scheduleWithFixedDelay(this::performMaintenance,
                                       30, // Première maintenance après 30 minutes
                                       60, // Maintenance toutes les heures
                                       TimeUnit.MINUTES);
        
        LOGGER.info("Gestionnaire de mise à jour automatique démarré (vérification toutes les " + 
                   checkIntervalMinutes + " minutes)");
    }
    
    /**
     * Arrête le gestionnaire de mise à jour automatique
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("Gestionnaire de mise à jour automatique arrêté");
    }
    
    /**
     * Ajoute un listener qui sera notifié lors des changements
     */
    public void addChangeListener(Consumer<String> listener) {
        changeListeners.add(listener);
    }
    
    /**
     * Supprime un listener
     */
    public void removeChangeListener(Consumer<String> listener) {
        changeListeners.remove(listener);
    }
    
    /**
     * Configure l'intervalle de vérification
     */
    public void setCheckInterval(int minutes) {
        if (minutes < 1 || minutes > 60) {
            throw new IllegalArgumentException("L'intervalle doit être entre 1 et 60 minutes");
        }
        
        this.checkIntervalMinutes = minutes;
        
        if (isRunning) {
            // Redémarrer avec le nouvel intervalle
            stop();
            start();
        }
        
        LOGGER.info("Intervalle de vérification mis à jour: " + minutes + " minutes");
    }
    
    /**
     * Active ou désactive l'actualisation intelligente
     */
    public void setSmartRefreshEnabled(boolean enabled) {
        this.enableSmartRefresh = enabled;
        LOGGER.info("Actualisation intelligente " + (enabled ? "activée" : "désactivée"));
    }
    
    /**
     * Force une vérification immédiate des changements
     */
    public void forceCheck() {
        if (isRunning) {
            scheduler.execute(this::checkForChanges);
        }
    }
    
    /**
     * Initialise les temps de modification de référence
     */
    private void initializeModificationTimes() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (String tableName : MONITORED_TABLES) {
                long modTime = getTableModificationTime(conn, tableName);
                lastModificationTimes.put(tableName, modTime);
                LOGGER.fine("Temps de modification initialisé pour " + tableName + ": " + modTime);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'initialisation des temps de modification", e);
        }
    }
    
    /**
     * Vérifie les changements dans les tables surveillées
     */
    private void checkForChanges() {
        if (!isRunning) return;
        
        try {
            Set<String> changedTables = detectChangedTables();
            
            if (!changedTables.isEmpty()) {
                LOGGER.info("Changements détectés dans les tables: " + changedTables);
                
                // Notifier les listeners
                String changeMessage = "Tables modifiées: " + String.join(", ", changedTables);
                notifyChangeListeners(changeMessage);
                
                // Mettre à jour les temps de modification
                updateModificationTimes(changedTables);
                
                // Enregistrer l'événement
                logRefreshEvent(changedTables);
            } else {
                LOGGER.fine("Aucun changement détecté lors de la vérification");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la vérification des changements", e);
        }
    }
    
    /**
     * Détecte les tables qui ont été modifiées
     */
    private Set<String> detectChangedTables() throws SQLException {
        Set<String> changedTables = new HashSet<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (String tableName : MONITORED_TABLES) {
                if (!tableExists(conn, tableName)) {
                    continue; // Ignorer les tables qui n'existent pas
                }
                
                long currentModTime = getTableModificationTime(conn, tableName);
                long lastKnownModTime = lastModificationTimes.getOrDefault(tableName, 0L);
                
                if (currentModTime > lastKnownModTime) {
                    changedTables.add(tableName);
                    LOGGER.fine(String.format("Table %s modifiée: %d > %d", 
                              tableName, currentModTime, lastKnownModTime));
                }
            }
        }
        
        return changedTables;
    }
    
    /**
     * Obtient le temps de modification d'une table
     */
    private long getTableModificationTime(Connection conn, String tableName) throws SQLException {
        // Approche 1: Utiliser les métadonnées de la table si disponibles
        String metaQuery = "SELECT UPDATE_TIME FROM INFORMATION_SCHEMA.TABLES " +
                          "WHERE TABLE_SCHEMA = 'master' AND TABLE_NAME = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(metaQuery)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp updateTime = rs.getTimestamp("UPDATE_TIME");
                    if (updateTime != null) {
                        return updateTime.getTime();
                    }
                }
            }
        } catch (SQLException e) {
            // Ignorer les erreurs de métadonnées
        }
        
        // Approche 2: Compter les lignes comme indicateur de changement
        String countQuery = "SELECT COUNT(*) as row_count FROM " + tableName;
        try (PreparedStatement stmt = conn.prepareStatement(countQuery);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int rowCount = rs.getInt("row_count");
                // Utiliser le nombre de lignes et le timestamp actuel
                return System.currentTimeMillis() / 1000 + rowCount;
            }
        }
        
        return System.currentTimeMillis() / 1000;
    }
    
    /**
     * Vérifie si une table existe
     */
    private boolean tableExists(Connection conn, String tableName) {
        try {
            String query = "SELECT 1 FROM " + tableName + " LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Met à jour les temps de modification après détection de changements
     */
    private void updateModificationTimes(Set<String> changedTables) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (String tableName : changedTables) {
                long newModTime = getTableModificationTime(conn, tableName);
                lastModificationTimes.put(tableName, newModTime);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la mise à jour des temps de modification", e);
        }
    }
    
    /**
     * Notifie tous les listeners des changements
     */
    private void notifyChangeListeners(String changeMessage) {
        Platform.runLater(() -> {
            for (Consumer<String> listener : changeListeners) {
                try {
                    listener.accept(changeMessage);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Erreur lors de la notification d'un listener", e);
                }
            }
        });
    }
    
    /**
     * Enregistre l'événement de rafraîchissement
     */
    private void logRefreshEvent(Set<String> changedTables) {
        try {
            String details = String.format("Actualisation automatique déclenchée par modification des tables: %s", 
                                          String.join(", ", changedTables));
            
            HistoryManager.logUpdate("Dashboard Auto-Refresh", details);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors de l'enregistrement de l'événement", e);
        }
    }
    
    /**
     * Effectue la maintenance périodique
     */
    private void performMaintenance() {
        if (!isRunning) return;
        
        try {
            // Nettoyer les connexions expirées
            cleanupExpiredConnections();
            
            // Vérifier la connectivité
            if (!testDatabaseConnection()) {
                LOGGER.warning("Problème de connectivité détecté lors de la maintenance");
            }
            
            // Optimiser les temps de modification si nécessaire
            optimizeModificationTimes();
            
            LOGGER.fine("Maintenance périodique effectuée");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la maintenance", e);
        }
    }
    
    /**
     * Nettoie les connexions expirées (implémentation basique)
     */
    private void cleanupExpiredConnections() {
        // Implémentation basique - dans une version complète, 
        // cela pourrait inclure un pool de connexions
        System.gc(); // Suggestion de garbage collection
    }
    
    /**
     * Test de connectivité à la base de données
     */
    private boolean testDatabaseConnection() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Test de connectivité échoué", e);
            return false;
        }
    }
    
    /**
     * Optimise les temps de modification stockés
     */
    private void optimizeModificationTimes() {
        // Supprimer les entrées pour les tables qui n'existent plus
        Set<String> tablesToRemove = new HashSet<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (String tableName : lastModificationTimes.keySet()) {
                if (!tableExists(conn, tableName)) {
                    tablesToRemove.add(tableName);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors de l'optimisation", e);
            return;
        }
        
        for (String tableName : tablesToRemove) {
            lastModificationTimes.remove(tableName);
            LOGGER.info("Table supprimée de la surveillance: " + tableName);
        }
    }
    
    /**
     * Obtient les statistiques du gestionnaire
     */
    public RefreshManagerStats getStats() {
        return new RefreshManagerStats(
            isRunning,
            checkIntervalMinutes,
            enableSmartRefresh,
            lastModificationTimes.size(),
            changeListeners.size()
        );
    }
    
    /**
     * Classe pour encapsuler les statistiques
     */
    public static class RefreshManagerStats {
        private final boolean isRunning;
        private final int checkIntervalMinutes;
        private final boolean smartRefreshEnabled;
        private final int monitoredTables;
        private final int listeners;
        
        public RefreshManagerStats(boolean isRunning, int checkIntervalMinutes, 
                                 boolean smartRefreshEnabled, int monitoredTables, int listeners) {
            this.isRunning = isRunning;
            this.checkIntervalMinutes = checkIntervalMinutes;
            this.smartRefreshEnabled = smartRefreshEnabled;
            this.monitoredTables = monitoredTables;
            this.listeners = listeners;
        }
        
        public boolean isRunning() { return isRunning; }
        public int getCheckIntervalMinutes() { return checkIntervalMinutes; }
        public boolean isSmartRefreshEnabled() { return smartRefreshEnabled; }
        public int getMonitoredTables() { return monitoredTables; }
        public int getListeners() { return listeners; }
        
        @Override
        public String toString() {
            return String.format("RefreshManagerStats{running=%s, interval=%dmin, smart=%s, tables=%d, listeners=%d}",
                               isRunning, checkIntervalMinutes, smartRefreshEnabled, monitoredTables, listeners);
        }
    }
}