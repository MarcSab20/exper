package application;

public class HistoryManager {
    /**
     * Enregistre une tentative de connexion dans l'historique
     * @param username Nom d'utilisateur
     * @param success Indique si la connexion a réussi
     */
    public static void logConnexionAttempt(String username, boolean success) {
        String statut = success ? "Succès" : "Échec";
        DatabaseManager.logConnexion(username, "Connexion", statut);
    }
    
    /**
     * Enregistre une déconnexion dans l'historique
     * @param username Nom d'utilisateur
     */
    public static void logDeconnexion(String username) {
        DatabaseManager.logConnexion(username, "Déconnexion", "Succès");
    }
    
    /**
     * Enregistre une création dans l'historique des modifications
     * @param table Nom de la table modifiée
     * @param details Détails de la modification
     */
    public static void logCreation(String table, String details) {
        DatabaseManager.logModification(table, "Création", UserSession.getCurrentUser(), details);
    }
    
    /**
     * Enregistre une mise à jour dans l'historique des modifications
     * @param table Nom de la table modifiée
     * @param details Détails de la modification
     */
    public static void logUpdate(String table, String details) {
        DatabaseManager.logModification(table, "Mise à jour", UserSession.getCurrentUser(), details);
    }
    
    /**
     * Enregistre une suppression dans l'historique des modifications
     * @param table Nom de la table modifiée
     * @param details Détails de la modification
     */
    public static void logDeletion(String table, String details) {
        DatabaseManager.logModification(table, "Suppression", UserSession.getCurrentUser(), details);
    }
}