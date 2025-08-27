package application;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service dédié à la récupération et au traitement des données pour le dashboard
 */
public class DashboardDataService {
    private static final Logger LOGGER = Logger.getLogger(DashboardDataService.class.getName());
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    // Définition des grades par catégorie
    private static final Set<String> GRADES_OFFICIERS = Set.of(
        "Sous-lieutenant", "Lieutenant", "Capitaine", "Lieutenant-colonel", "Colonel",
        "General de brigade aérienne", "Général de division aérienne", "General de corps d'armée"
    );
    
    private static final Set<String> GRADES_OFFICIERS_SUBALTERNES = Set.of(
        "Sous-lieutenant", "Lieutenant", "Capitaine", "Lieutenant-colonel", "Colonel"
    );
    
    private static final Set<String> GRADES_OFFICIERS_GENERAUX = Set.of(
        "General de brigade aérienne", "Général de division aérienne", "General de corps d'armée"
    );
    
    private static final Set<String> GRADES_SOUS_OFFICIERS = Set.of(
        "Adjudant", "Adjudant chef", "Adjudant chef major", "Sergent", "Sergent-chef"
    );
    
    private static final Set<String> GRADES_MILITAIRES_RANG = Set.of(
        "Caporal chef", "Caporal", "Soldat de 1ère classe", "Soldat de 2e classe"
    );

    /**
     * Récupère toutes les statistiques principales du dashboard
     */
    public DashboardStats getMainStats() throws SQLException {
        DashboardStats stats = new DashboardStats();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Personnel total
            stats.setPersonnelTotal(getPersonnelTotal(conn));
            
            // Personnel officiers
            stats.setPersonnelOfficiers(getPersonnelOfficiers(conn));
            
            // Personnel féminin
            stats.setPersonnelFeminin(getPersonnelFeminin(conn));
            
            // Nombre de formations
            stats.setNombreFormations(getNombreFormations(conn));
            
            LOGGER.info("Statistiques principales récupérées: " + stats);
        }
        
        return stats;
    }
    
    private int getPersonnelTotal(Connection conn) throws SQLException {
        String query = "SELECT COUNT(*) FROM identite_personnelle";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
    
    private int getPersonnelOfficiers(Connection conn) throws SQLException {
        StringBuilder query = new StringBuilder();
        query.append("SELECT COUNT(*) FROM grade_actuel WHERE rang IN (");
        
        String[] grades = GRADES_OFFICIERS.toArray(new String[0]);
        for (int i = 0; i < grades.length; i++) {
            if (i > 0) query.append(", ");
            query.append("?");
        }
        query.append(")");
        
        try (PreparedStatement stmt = conn.prepareStatement(query.toString())) {
            int paramIndex = 1;
            for (String grade : GRADES_OFFICIERS) {
                stmt.setString(paramIndex++, grade);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
    
    private int getPersonnelFeminin(Connection conn) throws SQLException {
        String query = "SELECT COUNT(*) FROM identite_personnelle WHERE sexe = 'Femme'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
    
    private int getNombreFormations(Connection conn) throws SQLException {
        String query = "SELECT COUNT(DISTINCT formation) FROM formation_actuelle WHERE formation IS NOT NULL";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
    
    /**
     * Récupère les données de répartition par sexe
     */
    public Map<String, Integer> getRepartitionParSexe() throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT sexe, COUNT(*) as count FROM identite_personnelle " +
                      "WHERE sexe IS NOT NULL GROUP BY sexe ORDER BY count DESC";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String sexe = rs.getString("sexe");
                int count = rs.getInt("count");
                if (sexe != null && !sexe.trim().isEmpty()) {
                    data.put(sexe, count);
                }
            }
        }
        
        return data;
    }
    
    /**
     * Récupère les données de répartition par grade (groupées)
     */
    public Map<String, Integer> getRepartitionParGrade() throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT rang, COUNT(*) as count FROM grade_actuel " +
                      "WHERE rang IS NOT NULL GROUP BY rang";
        
        Map<String, Integer> gradeStats = new HashMap<>();
        gradeStats.put("Officiers subalternes", 0);
        gradeStats.put("Officiers généraux", 0);
        gradeStats.put("Sous-officiers", 0);
        gradeStats.put("Militaires du rang", 0);
        gradeStats.put("Autres", 0);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String rang = rs.getString("rang");
                int count = rs.getInt("count");
                
                if (GRADES_OFFICIERS_SUBALTERNES.contains(rang)) {
                    gradeStats.put("Officiers subalternes", gradeStats.get("Officiers subalternes") + count);
                } else if (GRADES_OFFICIERS_GENERAUX.contains(rang)) {
                    gradeStats.put("Officiers généraux", gradeStats.get("Officiers généraux") + count);
                } else if (GRADES_SOUS_OFFICIERS.contains(rang)) {
                    gradeStats.put("Sous-officiers", gradeStats.get("Sous-officiers") + count);
                } else if (GRADES_MILITAIRES_RANG.contains(rang)) {
                    gradeStats.put("Militaires du rang", gradeStats.get("Militaires du rang") + count);
                } else {
                    gradeStats.put("Autres", gradeStats.get("Autres") + count);
                }
            }
        }
        
        // Ne conserver que les catégories avec des données
        for (Map.Entry<String, Integer> entry : gradeStats.entrySet()) {
            if (entry.getValue() > 0) {
                data.put(entry.getKey(), entry.getValue());
            }
        }
        
        return data;
    }
    
    /**
     * Récupère les données de répartition par région d'origine
     */
    public Map<String, Integer> getRepartitionParRegion() throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT region_origine, COUNT(*) as count FROM identite_culturelle " +
                      "WHERE region_origine IS NOT NULL AND region_origine != '' " +
                      "GROUP BY region_origine ORDER BY count DESC LIMIT 10";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String region = rs.getString("region_origine");
                int count = rs.getInt("count");
                if (region != null && !region.trim().isEmpty()) {
                    data.put(region, count);
                }
            }
        }
        
        return data;
    }
    
    /**
     * Récupère les données de répartition par religion
     */
    public Map<String, Integer> getRepartitionParReligion() throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = "SELECT religion, COUNT(*) as count FROM identite_culturelle " +
                      "WHERE religion IS NOT NULL AND religion != '' " +
                      "GROUP BY religion ORDER BY count DESC LIMIT 8";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String religion = rs.getString("religion");
                int count = rs.getInt("count");
                if (religion != null && !religion.trim().isEmpty()) {
                    data.put(religion, count);
                }
            }
        }
        
        return data;
    }
    
    /**
     * Récupère les données pour une colonne spécifique d'une table
     */
    public Map<String, Integer> getColumnData(String tableName, String columnName) throws SQLException {
        Map<String, Integer> data = new LinkedHashMap<>();
        String query = String.format(
            "SELECT %s, COUNT(*) as count FROM %s " +
            "WHERE %s IS NOT NULL AND %s != '' " +
            "GROUP BY %s ORDER BY count DESC LIMIT 15",
            columnName, tableName, columnName, columnName, columnName
        );
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String value = rs.getString(columnName);
                int count = rs.getInt("count");
                if (value != null && !value.trim().isEmpty()) {
                    data.put(value, count);
                }
            }
        }
        
        return data;
    }
    
    /**
     * Récupère les données croisées entre deux tables
     */
    public Map<String, Map<String, Integer>> getCrossTableData(String table1, String column1, 
                                                              String table2, String column2) throws SQLException {
        Map<String, Map<String, Integer>> crossData = new LinkedHashMap<>();
        
        String query = String.format(
            "SELECT t1.%s as col1, t2.%s as col2, COUNT(*) as count " +
            "FROM %s t1 " +
            "INNER JOIN %s t2 ON t1.matricule = t2.matricule " +
            "WHERE t1.%s IS NOT NULL AND TRIM(t1.%s) != '' " +
            "AND t2.%s IS NOT NULL AND TRIM(t2.%s) != '' " +
            "GROUP BY t1.%s, t2.%s " +
            "ORDER BY count DESC, t1.%s, t2.%s " +
            "LIMIT 50",
            column1, column2, table1, table2,
            column1, column1, column2, column2,
            column1, column2, column1, column2
        );
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String value1 = rs.getString("col1");
                String value2 = rs.getString("col2");
                int count = rs.getInt("count");
                
                if (value1 != null && value2 != null && 
                    !value1.trim().isEmpty() && !value2.trim().isEmpty()) {
                    crossData.computeIfAbsent(value1, k -> new LinkedHashMap<>()).put(value2, count);
                }
            }
        }
        
        return crossData;
    }
    
    /**
     * Vérifie si une table existe et contient des données
     */
    public boolean isTableAvailable(String tableName) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables("master", null, tableName, null)) {
                if (rs.next()) {
                    // Vérifier s'il y a des données
                    String countQuery = "SELECT COUNT(*) FROM " + tableName;
                    try (Statement stmt = conn.createStatement();
                         ResultSet countRs = stmt.executeQuery(countQuery)) {
                        return countRs.next() && countRs.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la vérification de la table " + tableName, e);
        }
        return false;
    }
    
    /**
     * Classe pour encapsuler les statistiques principales
     */
    public static class DashboardStats {
        private int personnelTotal;
        private int personnelOfficiers;
        private int personnelFeminin;
        private int nombreFormations;
        
        // Getters et setters
        public int getPersonnelTotal() { return personnelTotal; }
        public void setPersonnelTotal(int personnelTotal) { this.personnelTotal = personnelTotal; }
        
        public int getPersonnelOfficiers() { return personnelOfficiers; }
        public void setPersonnelOfficiers(int personnelOfficiers) { this.personnelOfficiers = personnelOfficiers; }
        
        public int getPersonnelFeminin() { return personnelFeminin; }
        public void setPersonnelFeminin(int personnelFeminin) { this.personnelFeminin = personnelFeminin; }
        
        public int getNombreFormations() { return nombreFormations; }
        public void setNombreFormations(int nombreFormations) { this.nombreFormations = nombreFormations; }
        
        @Override
        public String toString() {
            return String.format("DashboardStats{total=%d, officiers=%d, feminin=%d, formations=%d}", 
                    personnelTotal, personnelOfficiers, personnelFeminin, nombreFormations);
        }
    }
}