package application;


public class UserSession {
 private static String currentUser = "Unknown";
 
 public static String getCurrentUser() {
     return currentUser;
 }
 
 public static void setCurrentUser(String username) {
     currentUser = username;
     
     // Enregistrer la connexion
     DatabaseManager.logConnexion(username, "Connexion", "Succès");
 }
 
 public static void logout() {
     // Enregistrer la déconnexion
     DatabaseManager.logConnexion(currentUser, "Déconnexion", "Succès");
     currentUser = "Unknown";
 }
}
