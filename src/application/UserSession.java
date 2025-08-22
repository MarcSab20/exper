package application;

public class UserSession {
    private static String currentUser = "Unknown";
    private static String currentService = "Admin"; // Service par défaut
    
    public static String getCurrentUser() {
        return currentUser;
    }
    
    public static void setCurrentUser(String username) {
        currentUser = username;
        
        // Enregistrer la connexion
        DatabaseManager.logConnexion(username, "Connexion", "Succès");
    }
    
    public static String getCurrentService() {
        return currentService;
    }
    
    public static void setCurrentService(String service) {
        currentService = service;
    }
    
    /**
     * Définit l'utilisateur et le service en une seule fois
     */
    public static void setCurrentUserAndService(String username, String service) {
        currentUser = username;
        currentService = service;
        
        // Enregistrer la connexion avec le service
        DatabaseManager.logConnexion(username, "Connexion - Service: " + service, "Succès");
    }
    
    public static void logout() {
        // Enregistrer la déconnexion
        DatabaseManager.logConnexion(currentUser, "Déconnexion - Service: " + currentService, "Succès");
        currentUser = "Unknown";
        currentService = "Admin";
    }
    
    /**
     * Vérifie si l'utilisateur actuel a accès à une table donnée
     */
    public static boolean hasAccessToTable(String tableName) {
        return ServicePermissions.hasAccessToTable(currentService, tableName);
    }
    
    /**
     * Obtient les tables accessibles pour le service actuel
     */
    public static java.util.List<String> getAccessibleTables() {
        return ServicePermissions.getTablesForService(currentService);
    }
}