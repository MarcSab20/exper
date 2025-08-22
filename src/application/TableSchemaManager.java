package application;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionnaire de schéma de table mis à jour qui reflète la structure réelle
 * des tables après les modifications de tables_update.sql
 */
public class TableSchemaManager {
    private static final Logger LOGGER = Logger.getLogger(TableSchemaManager.class.getName());
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    // Cache pour éviter les requêtes répétitives
    private static final Map<String, List<ColumnInfo>> SCHEMA_CACHE = new HashMap<>();
    private static final Map<String, Set<String>> COLUMN_NAMES_CACHE = new HashMap<>();
    
    /**
     * Obtient le schéma réel d'une table directement depuis la base de données
     */
    public static List<ColumnInfo> getRealTableSchema(String tableName) {
        // Vérifier le cache d'abord
        if (SCHEMA_CACHE.containsKey(tableName)) {
            return new ArrayList<>(SCHEMA_CACHE.get(tableName));
        }
        
        List<ColumnInfo> columns = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Utiliser DESCRIBE pour obtenir la structure exacte
            String sql = "DESCRIBE " + tableName;
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    String columnName = rs.getString("Field");
                    String type = rs.getString("Type");
                    boolean isNullable = "YES".equalsIgnoreCase(rs.getString("Null"));
                    String key = rs.getString("Key");
                    boolean isPrimaryKey = "PRI".equalsIgnoreCase(key);
                    boolean isAutoIncrement = "auto_increment".equalsIgnoreCase(rs.getString("Extra"));
                    
                    ColumnInfo columnInfo = new ColumnInfo(columnName, type, isNullable, isPrimaryKey, isAutoIncrement);
                    columns.add(columnInfo);
                }
            }
            
            // Mettre en cache
            SCHEMA_CACHE.put(tableName, new ArrayList<>(columns));
            
            LOGGER.info("Schéma récupéré pour " + tableName + ": " + columns.size() + " colonnes");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération du schéma pour " + tableName, e);
        }
        
        return columns;
    }
    
    /**
     * Obtient les noms des colonnes d'une table
     */
    public static Set<String> getTableColumnNames(String tableName) {
        // Vérifier le cache d'abord
        if (COLUMN_NAMES_CACHE.containsKey(tableName)) {
            return new HashSet<>(COLUMN_NAMES_CACHE.get(tableName));
        }
        
        Set<String> columnNames = new HashSet<>();
        List<ColumnInfo> schema = getRealTableSchema(tableName);
        
        for (ColumnInfo column : schema) {
            columnNames.add(column.getName());
        }
        
        // Mettre en cache
        COLUMN_NAMES_CACHE.put(tableName, new HashSet<>(columnNames));
        
        return columnNames;
    }
    
    /**
     * Obtient les colonnes obligatoires (NOT NULL et pas auto-increment)
     */
    public static Set<String> getRequiredColumns(String tableName) {
        Set<String> requiredColumns = new HashSet<>();
        List<ColumnInfo> schema = getRealTableSchema(tableName);
        
        for (ColumnInfo column : schema) {
            if (!column.isNullable() && !column.isAutoIncrement()) {
                requiredColumns.add(column.getName());
            }
        }
        
        return requiredColumns;
    }
    
    /**
     * Valide la compatibilité entre un fichier CSV et une table
     */
    public static SchemaValidationResult validateCSVCompatibility(List<String> csvColumns, String tableName) {
        Set<String> tableColumns = getTableColumnNames(tableName);
        Set<String> requiredColumns = getRequiredColumns(tableName);
        
        SchemaValidationResult result = new SchemaValidationResult();
        result.setTableName(tableName);
        result.setCsvColumns(csvColumns);
        result.setTableColumns(new ArrayList<>(tableColumns));
        
        // Vérifier les colonnes manquantes dans le CSV
        Set<String> missingInCSV = new HashSet<>();
        for (String required : requiredColumns) {
            if (!csvColumns.contains(required)) {
                missingInCSV.add(required);
            }
        }
        
        // Vérifier les colonnes en trop dans le CSV
        Set<String> extraInCSV = new HashSet<>();
        for (String csvColumn : csvColumns) {
            if (!tableColumns.contains(csvColumn)) {
                extraInCSV.add(csvColumn);
            }
        }
        
        result.setMissingColumns(new ArrayList<>(missingInCSV));
        result.setExtraColumns(new ArrayList<>(extraInCSV));
        result.setValid(missingInCSV.isEmpty() && extraInCSV.isEmpty());
        
        // Informations détaillées
        result.setTotalTableColumns(tableColumns.size());
        result.setTotalCsvColumns(csvColumns.size());
        result.setMatchingColumns(csvColumns.size() - extraInCSV.size());
        
        return result;
    }
    
    /**
     * Génère un fichier CSV template pour une table donnée
     */
    public static String generateCSVTemplate(String tableName) {
        List<ColumnInfo> schema = getRealTableSchema(tableName);
        StringBuilder template = new StringBuilder();
        
        // En-têtes
        List<String> headers = new ArrayList<>();
        for (ColumnInfo column : schema) {
            headers.add(column.getName());
        }
        template.append(String.join(";", headers)).append("\n");
        
        // Ligne d'exemple avec des valeurs par défaut
        List<String> exampleValues = new ArrayList<>();
        for (ColumnInfo column : schema) {
            String exampleValue = generateExampleValue(column);
            exampleValues.add(exampleValue);
        }
        template.append(String.join(";", exampleValues)).append("\n");
        
        return template.toString();
    }
    
    /**
     * Génère une valeur d'exemple pour une colonne donnée
     */
    private static String generateExampleValue(ColumnInfo column) {
        String type = column.getType().toLowerCase();
        String name = column.getName().toLowerCase();
        
        if (column.isAutoIncrement()) {
            return ""; // Laisser vide pour auto-increment
        }
        
        if (type.contains("varchar") || type.contains("text")) {
            if (name.contains("matricule")) return "MAT001";
            if (name.contains("nom")) return "Dupont";
            if (name.contains("prenom")) return "Jean";
            if (name.contains("telephone")) return "0123456789";
            if (name.contains("email")) return "exemple@email.com";
            if (name.contains("sexe")) return "Homme";
            if (name.contains("rang")) return "Sergent";
            if (name.contains("grade")) return "Sergent";
            if (name.contains("statut")) return "Actif";
            return "Exemple";
        }
        
        if (type.contains("int")) {
            if (name.contains("age")) return "25";
            if (name.contains("nombre")) return "1";
            if (name.contains("quantite")) return "10";
            return "1";
        }
        
        if (type.contains("date")) {
            return "2024-01-01";
        }
        
        if (type.contains("decimal") || type.contains("float")) {
            return "10.5";
        }
        
        if (type.contains("boolean") || type.contains("tinyint(1)")) {
            return "0";
        }
        
        return "valeur";
    }
    
    /**
     * Affiche le schéma complet d'une table de manière formatée
     */
    public static String getFormattedTableSchema(String tableName) {
        List<ColumnInfo> schema = getRealTableSchema(tableName);
        StringBuilder formatted = new StringBuilder();
        
        formatted.append("=== SCHÉMA DE LA TABLE: ").append(tableName.toUpperCase()).append(" ===\n\n");
        
        formatted.append(String.format("%-20s %-20s %-10s %-10s %-15s%n", 
                "COLONNE", "TYPE", "NULL", "CLÉ", "AUTO_INCREMENT"));
        formatted.append("-".repeat(80)).append("\n");
        
        for (ColumnInfo column : schema) {
            formatted.append(String.format("%-20s %-20s %-10s %-10s %-15s%n",
                    column.getName(),
                    column.getType(),
                    column.isNullable() ? "OUI" : "NON",
                    column.isPrimaryKey() ? "PRI" : "",
                    column.isAutoIncrement() ? "OUI" : ""));
        }
        
        formatted.append("\n=== COLONNES OBLIGATOIRES ===\n");
        Set<String> required = getRequiredColumns(tableName);
        for (String col : required) {
            formatted.append("- ").append(col).append("\n");
        }
        
        return formatted.toString();
    }
    
    /**
     * Efface le cache (utile après des modifications de schéma)
     */
    public static void clearCache() {
        SCHEMA_CACHE.clear();
        COLUMN_NAMES_CACHE.clear();
        LOGGER.info("Cache de schéma effacé");
    }
    
    /**
     * Vérifie si une table existe
     */
    public static boolean tableExists(String tableName) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables("master", null, tableName, null)) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la vérification d'existence de table " + tableName, e);
            return false;
        }
    }
    
    /**
     * Classe améliorée pour les informations de colonne
     */
    public static class ColumnInfo {
        private final String name;
        private final String type;
        private final boolean nullable;
        private final boolean primaryKey;
        private final boolean autoIncrement;
        
        public ColumnInfo(String name, String type, boolean nullable, boolean primaryKey, boolean autoIncrement) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
            this.primaryKey = primaryKey;
            this.autoIncrement = autoIncrement;
        }
        
        // Getters
        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isNullable() { return nullable; }
        public boolean isPrimaryKey() { return primaryKey; }
        public boolean isAutoIncrement() { return autoIncrement; }
        
        @Override
        public String toString() {
            return String.format("%s (%s)%s%s%s", 
                name, 
                type,
                primaryKey ? " [PK]" : "",
                !nullable ? " [NOT NULL]" : "",
                autoIncrement ? " [AUTO_INC]" : "");
        }
    }
    
    /**
     * Résultat de validation de schéma amélioré
     */
    public static class SchemaValidationResult {
        private String tableName;
        private List<String> csvColumns;
        private List<String> tableColumns;
        private List<String> missingColumns = new ArrayList<>();
        private List<String> extraColumns = new ArrayList<>();
        private boolean valid;
        private int totalTableColumns;
        private int totalCsvColumns;
        private int matchingColumns;
        
        // Getters et setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        
        public List<String> getCsvColumns() { return csvColumns; }
        public void setCsvColumns(List<String> csvColumns) { this.csvColumns = csvColumns; }
        
        public List<String> getTableColumns() { return tableColumns; }
        public void setTableColumns(List<String> tableColumns) { this.tableColumns = tableColumns; }
        
        public List<String> getMissingColumns() { return missingColumns; }
        public void setMissingColumns(List<String> missingColumns) { this.missingColumns = missingColumns; }
        
        public List<String> getExtraColumns() { return extraColumns; }
        public void setExtraColumns(List<String> extraColumns) { this.extraColumns = extraColumns; }
        
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public int getTotalTableColumns() { return totalTableColumns; }
        public void setTotalTableColumns(int totalTableColumns) { this.totalTableColumns = totalTableColumns; }
        
        public int getTotalCsvColumns() { return totalCsvColumns; }
        public void setTotalCsvColumns(int totalCsvColumns) { this.totalCsvColumns = totalCsvColumns; }
        
        public int getMatchingColumns() { return matchingColumns; }
        public void setMatchingColumns(int matchingColumns) { this.matchingColumns = matchingColumns; }
        
        /**
         * Génère un rapport détaillé de validation
         */
        public String getDetailedReport() {
            StringBuilder report = new StringBuilder();
            
            report.append("=== RAPPORT DE VALIDATION - TABLE: ").append(tableName.toUpperCase()).append(" ===\n\n");
            
            report.append("STATISTIQUES:\n");
            report.append("- Colonnes dans la table: ").append(totalTableColumns).append("\n");
            report.append("- Colonnes dans le CSV: ").append(totalCsvColumns).append("\n");
            report.append("- Colonnes correspondantes: ").append(matchingColumns).append("\n");
            report.append("- Compatibilité: ").append(valid ? "✅ VALIDE" : "❌ INVALIDE").append("\n\n");
            
            if (!missingColumns.isEmpty()) {
                report.append("❌ COLONNES MANQUANTES DANS LE CSV (").append(missingColumns.size()).append("):\n");
                for (String col : missingColumns) {
                    report.append("  - ").append(col).append("\n");
                }
                report.append("\n");
            }
            
            if (!extraColumns.isEmpty()) {
                report.append("⚠️  COLONNES EN TROP DANS LE CSV (").append(extraColumns.size()).append("):\n");
                for (String col : extraColumns) {
                    report.append("  - ").append(col).append("\n");
                }
                report.append("\n");
            }
            
            if (valid) {
                report.append("✅ TOUTES LES COLONNES SONT COMPATIBLES!\n");
            } else {
                report.append("SUGGESTIONS:\n");
                report.append("1. Vérifiez que votre CSV contient toutes les colonnes obligatoires\n");
                report.append("2. Supprimez les colonnes en trop ou mettez à jour le schéma de table\n");
                report.append("3. Utilisez le template CSV généré pour cette table\n");
            }
            
            return report.toString();
        }
        
        public String getErrorMessage() {
            StringBuilder sb = new StringBuilder();
            if (!missingColumns.isEmpty()) {
                sb.append("Colonnes manquantes: ").append(String.join(", ", missingColumns));
            }
            if (!extraColumns.isEmpty()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Colonnes en trop: ").append(String.join(", ", extraColumns));
            }
            return sb.toString();
        }
    }
}