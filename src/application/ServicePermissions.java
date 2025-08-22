package application;

import java.util.*;

/**
 * Gestionnaire des permissions d'accès aux tables par service
 */
public class ServicePermissions {
    
    // Tables communes à tous les services
    private static final List<String> TABLES_COMMUNES = Arrays.asList(
        "identite_personnelle", 
        "identite_sociale", 
        "identite_culturelle", 
        "grade_actuel", 
        "formation_actuelle", 
        "specialite"
    );
    
    // Tables spécifiques par service
    private static final Map<String, List<String>> TABLES_PAR_SERVICE = new HashMap<>();
    
    static {
        // Service Logistique
        TABLES_PAR_SERVICE.put("Logistique", Arrays.asList(
            "parametres_corporels",
            "dotation_particuliere_config", 
            "dotation_particuliere",
            "dotation_20_mai",
            "maintenance"
        ));
        
        // Service Opérations
        TABLES_PAR_SERVICE.put("Opérations", Arrays.asList(
            "operation",
            "punition", 
            "langue",
            "infos_specifiques_general",
            "personnel_naviguant"
        ));
        
        // Service Ressources Humaines
        TABLES_PAR_SERVICE.put("Ressources Humaines", Arrays.asList(
            "ecole_civile",
            "ecole_formation_initiale",
            "ecole_militaire", 
            "personnel_naviguant",
            "medaille",
            "punition",
            "decoration",
            "infos_specifiques_general",
            "historique_postes",
            "historique_grades"
        ));
    }
    
    /**
     * Obtient la liste des tables accessibles pour un service donné
     */
    public static List<String> getTablesForService(String service) {
        List<String> tables = new ArrayList<>(TABLES_COMMUNES);
        
        if (TABLES_PAR_SERVICE.containsKey(service)) {
            tables.addAll(TABLES_PAR_SERVICE.get(service));
        }
        
        Collections.sort(tables);
        return tables;
    }
    
    /**
     * Vérifie si un service a accès à une table spécifique
     */
    public static boolean hasAccessToTable(String service, String tableName) {
        return getTablesForService(service).contains(tableName);
    }
    
    /**
     * Obtient la colonne clé primaire pour une table donnée
     */
    public static String getPrimaryKeyColumn(String tableName) {
        // La plupart des tables utilisent matricule comme clé primaire
        switch (tableName) {
            case "identite_personnelle":
            case "identite_sociale": 
            case "identite_culturelle":
            case "grade_actuel":
            case "formation_actuelle":
            case "specialite":
            case "parametres_corporels":
            case "dotation_particuliere_config":
            case "infos_specifiques_general":
            case "personnel_naviguant":
                return "matricule";
            
            // Tables avec ID auto-incrémenté
            case "dotation_particuliere":
            case "dotation_20_mai":
            case "maintenance":
            case "operation":
            case "punition":
            case "langue":
            case "ecole_civile":
            case "ecole_formation_initiale": 
            case "ecole_militaire":
            case "medaille":
            case "decoration":
            case "historique_postes":
            case "historique_grades":
                return "id";
                
            default:
                return "id"; // Par défaut
        }
    }
    
    /**
     * Indique si une table utilise matricule comme clé de regroupement pour les stats
     */
    public static boolean usesMatriculeForGrouping(String tableName) {
        return !getPrimaryKeyColumn(tableName).equals("matricule") || 
               Arrays.asList("dotation_particuliere", "dotation_20_mai", "maintenance", 
                           "operation", "punition", "langue", "ecole_civile", 
                           "ecole_militaire", "medaille", "decoration", 
                           "historique_postes", "historique_grades").contains(tableName);
    }
    
    /**
     * Obtient une description conviviale de la table
     */
    public static String getTableDescription(String tableName) {
        switch (tableName) {
            case "identite_personnelle": return "Identité Personnelle";
            case "identite_sociale": return "Identité Sociale";
            case "identite_culturelle": return "Identité Culturelle";
            case "grade_actuel": return "Grade Actuel";
            case "formation_actuelle": return "Formation Actuelle";
            case "specialite": return "Spécialités";
            case "parametres_corporels": return "Paramètres Corporels";
            case "dotation_particuliere_config": return "Configuration Dotations";
            case "dotation_particuliere": return "Dotations Particulières";
            case "dotation_20_mai": return "Dotations 20 Mai";
            case "maintenance": return "Maintenances";
            case "operation": return "Opérations";
            case "punition": return "Punitions";
            case "langue": return "Langues";
            case "infos_specifiques_general": return "Infos Spécifiques Général";
            case "personnel_naviguant": return "Personnel Naviguant";
            case "ecole_civile": return "Écoles Civiles";
            case "ecole_formation_initiale": return "École Formation Initiale";
            case "ecole_militaire": return "Écoles Militaires";
            case "medaille": return "Médailles";
            case "decoration": return "Décorations";
            case "historique_postes": return "Historique des Postes";
            case "historique_grades": return "Historique des Grades";
            default: return tableName;
        }
    }
}