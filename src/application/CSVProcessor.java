package application;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processeur CSV amélioré qui utilise le schéma réel de la base de données
 */
public class CSVProcessor {
    private static final Logger LOGGER = Logger.getLogger(CSVProcessor.class.getName());
    private static final String CSV_SEPARATOR = ","; // Séparateur par défaut
    
    /**
     * Valide la cohérence entre le fichier CSV et la table de destination
     * Utilise le schéma réel de la base de données
     */
    public static TableSchemaManager.SchemaValidationResult validateCSVSchema(File csvFile, String tableName) throws IOException {
        List<String> csvColumns = extractCSVHeaders(csvFile);
        return TableSchemaManager.validateCSVCompatibility(csvColumns, tableName);
    }
    
    /**
     * Extrait les en-têtes du fichier CSV avec détection d'encodage améliorée
     */
    private static List<String> extractCSVHeaders(File csvFile) throws IOException {
        // Essayer plusieurs encodages
        String[] encodings = {"UTF-8", "ISO-8859-1", "Windows-1252"};
        
        for (String encoding : encodings) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), encoding))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    throw new IOException("Le fichier CSV est vide");
                }
                
                // Nettoyer les en-têtes
                String[] headers = headerLine.split(CSV_SEPARATOR);
                List<String> headerList = new ArrayList<>();
                for (String header : headers) {
                    // Nettoyer les BOM et espaces
                    String cleanHeader = header.trim().replaceAll("^\uFEFF", "");
                    if (!cleanHeader.isEmpty()) {
                        headerList.add(cleanHeader);
                    }
                }
                
                if (!headerList.isEmpty()) {
                    LOGGER.info("En-têtes CSV extraits avec l'encodage " + encoding + ": " + headerList);
                    return headerList;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Échec avec l'encodage " + encoding, e);
            }
        }
        
        throw new IOException("Impossible de lire les en-têtes du fichier CSV");
    }
    
    /**
     * Effectue la mise à jour intelligente avec différenciation insertion/modification
     * Version améliorée avec meilleure gestion des erreurs
     */
    public static EnhancedUpdateResult processEnhancedCSVUpdate(File csvFile, String tableName, 
                                                               ProgressCallback updateCallback) throws Exception {
        
        int recordsInserted = 0;
        int recordsUpdated = 0;
        int recordsUnchanged = 0;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validation du schéma avec le nouveau système
        TableSchemaManager.SchemaValidationResult validation = validateCSVSchema(csvFile, tableName);
        if (!validation.isValid()) {
            throw new Exception("Schéma CSV incompatible:\n" + validation.getDetailedReport());
        }
        
        String primaryKey = ServicePermissions.getPrimaryKeyColumn(tableName);
        Set<String> tableColumns = TableSchemaManager.getTableColumnNames(tableName);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            String[] headers = headerLine.split(CSV_SEPARATOR);
            
            // Nettoyer les en-têtes
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim().replaceAll("^\uFEFF", "");
            }
            
            // Filtrer les colonnes valides
            List<String> validHeaders = new ArrayList<>();
            List<Integer> validIndexes = new ArrayList<>();
            
            for (int i = 0; i < headers.length; i++) {
                if (tableColumns.contains(headers[i])) {
                    validHeaders.add(headers[i]);
                    validIndexes.add(i);
                } else {
                    warnings.add("Colonne ignorée (inexistante dans la table): " + headers[i]);
                }
            }
            
            LOGGER.info("Colonnes valides identifiées: " + validHeaders);
            
            // Compter les lignes pour la progression
            int totalLines = countCSVLines(csvFile) - 1; // -1 pour l'en-tête
            
            int currentLine = 0;
            Connection conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);
            
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    currentLine++;
                    if (updateCallback != null) {
                        updateCallback.onProgress((double) currentLine / totalLines);
                    }
                    
                    try {
                        String[] values = line.split(CSV_SEPARATOR, -1); // -1 pour garder les valeurs vides
                        Map<String, String> rowData = new HashMap<>();
                        
                        // Convertir la ligne en map en utilisant seulement les colonnes valides
                        for (int i = 0; i < validIndexes.size(); i++) {
                            int columnIndex = validIndexes.get(i);
                            String columnName = validHeaders.get(i);
                            
                            String value = "";
                            if (columnIndex < values.length) {
                                value = values[columnIndex].trim();
                            }
                            
                            rowData.put(columnName, value);
                        }
                        
                        // Traitement selon le type de clé primaire
                        if ("id".equals(primaryKey)) {
                            // Pour les tables avec ID auto-incrémenté, toujours insérer
                            insertRecord(conn, tableName, rowData, validHeaders);
                            recordsInserted++;
                        } else {
                            // Pour les tables avec matricule, vérifier existence
                            String keyValue = rowData.get(primaryKey);
                            if (keyValue == null || keyValue.isEmpty()) {
                                errors.add("Ligne " + currentLine + ": Valeur de clé primaire manquante (" + primaryKey + ")");
                                continue;
                            }
                            
                            Map<String, String> existingRecord = getExistingRecord(conn, tableName, primaryKey, keyValue);
                            
                            if (existingRecord.isEmpty()) {
                                // Nouvel enregistrement
                                insertRecord(conn, tableName, rowData, validHeaders);
                                recordsInserted++;
                            } else {
                                // Vérifier si des modifications sont nécessaires
                                if (hasChanges(existingRecord, rowData, validHeaders)) {
                                    updateRecord(conn, tableName, primaryKey, keyValue, rowData, validHeaders);
                                    recordsUpdated++;
                                } else {
                                    recordsUnchanged++;
                                }
                            }
                        }
                        
                    } catch (Exception e) {
                        String errorMsg = "Erreur à la ligne " + currentLine + ": " + e.getMessage();
                        errors.add(errorMsg);
                        LOGGER.log(Level.WARNING, errorMsg, e);
                    }
                }
                
                conn.commit();
                LOGGER.info("Mise à jour terminée avec succès");
                
            } catch (Exception e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Erreur lors de la mise à jour, rollback effectué", e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        
        return new EnhancedUpdateResult(recordsInserted, recordsUpdated, recordsUnchanged, errors, warnings);
    }
    
    /**
     * Compte le nombre de lignes dans un fichier CSV
     */
    private static int countCSVLines(File file) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Récupère un enregistrement existant de la base de données
     */
    private static Map<String, String> getExistingRecord(Connection conn, String tableName, 
                                                        String primaryKey, String keyValue) throws SQLException {
        Map<String, String> record = new HashMap<>();
        String sql = "SELECT * FROM " + tableName + " WHERE " + primaryKey + " = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, keyValue);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        String value = rs.getString(i);
                        record.put(columnName, value != null ? value : "");
                    }
                }
            }
        }
        
        return record;
    }
    
    /**
     * Vérifie s'il y a des changements entre l'enregistrement existant et les nouvelles données
     */
    private static boolean hasChanges(Map<String, String> existing, Map<String, String> newData, List<String> headers) {
        for (String column : headers) {
            String existingValue = existing.getOrDefault(column, "");
            String newValue = newData.getOrDefault(column, "");
            
            if (!existingValue.equals(newValue)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Insère un nouvel enregistrement avec gestion améliorée des types
     */
    private static void insertRecord(Connection conn, String tableName, Map<String, String> rowData, 
                                   List<String> headers) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        // Obtenir les informations de schéma pour la conversion de types
        List<TableSchemaManager.ColumnInfo> schema = TableSchemaManager.getRealTableSchema(tableName);
        Map<String, String> columnTypes = new HashMap<>();
        for (TableSchemaManager.ColumnInfo col : schema) {
            columnTypes.put(col.getName(), col.getType());
        }
        
        for (String column : headers) {
            String value = rowData.get(column);
            if (value != null && !value.isEmpty()) {
                columns.add(column);
                placeholders.add("?");
                
                // Convertir selon le type de colonne
                Object convertedValue = convertValueForDatabase(value, columnTypes.get(column));
                values.add(convertedValue);
            }
        }
        
        if (columns.isEmpty()) {
            throw new SQLException("Aucune donnée à insérer");
        }
        
        String sql = "INSERT INTO " + tableName + " (" + String.join(", ", columns) + ") " +
                     "VALUES (" + String.join(", ", placeholders) + ")";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                setParameterValue(stmt, i + 1, values.get(i));
            }
            stmt.executeUpdate();
        }
    }
    
    /**
     * Met à jour un enregistrement existant avec gestion améliorée des types
     */
    private static void updateRecord(Connection conn, String tableName, String primaryKey, 
                                   String keyValue, Map<String, String> rowData, List<String> headers) throws SQLException {
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        // Obtenir les informations de schéma pour la conversion de types
        List<TableSchemaManager.ColumnInfo> schema = TableSchemaManager.getRealTableSchema(tableName);
        Map<String, String> columnTypes = new HashMap<>();
        for (TableSchemaManager.ColumnInfo col : schema) {
            columnTypes.put(col.getName(), col.getType());
        }
        
        for (String column : headers) {
            if (!column.equals(primaryKey)) {
                String value = rowData.get(column);
                setClauses.add(column + " = ?");
                
                // Convertir selon le type de colonne
                Object convertedValue = convertValueForDatabase(value, columnTypes.get(column));
                values.add(convertedValue);
            }
        }
        
        if (setClauses.isEmpty()) {
            return; // Rien à mettre à jour
        }
        
        String sql = "UPDATE " + tableName + " SET " + String.join(", ", setClauses) + 
                     " WHERE " + primaryKey + " = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                setParameterValue(stmt, i + 1, values.get(i));
            }
            stmt.setString(values.size() + 1, keyValue);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Convertit une valeur selon le type de colonne de la base de données
     */
    private static Object convertValueForDatabase(String value, String columnType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        value = value.trim();
        String type = columnType.toLowerCase();
        
        try {
            if (type.contains("int")) {
                return Integer.parseInt(value);
            } else if (type.contains("decimal") || type.contains("float") || type.contains("double")) {
                return Double.parseDouble(value);
            } else if (type.contains("date")) {
                // Gérer différents formats de date
                if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    return java.sql.Date.valueOf(value);
                } else if (value.matches("\\d{2}/\\d{2}/\\d{4}")) {
                    String[] parts = value.split("/");
                    return java.sql.Date.valueOf(parts[2] + "-" + parts[1] + "-" + parts[0]);
                } else {
                    return java.sql.Date.valueOf(value); // Laisser SQL faire la conversion
                }
            } else if (type.contains("boolean") || type.contains("tinyint(1)")) {
                return "1".equals(value) || "true".equalsIgnoreCase(value) || "oui".equalsIgnoreCase(value);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur de conversion pour la valeur '" + value + "' vers le type " + columnType, e);
        }
        
        return value; // Retourner comme String par défaut
    }
    
    /**
     * Définit la valeur d'un paramètre PreparedStatement selon son type
     */
    private static void setParameterValue(PreparedStatement stmt, int index, Object value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.VARCHAR);
        } else if (value instanceof Integer) {
            stmt.setInt(index, (Integer) value);
        } else if (value instanceof Double) {
            stmt.setDouble(index, (Double) value);
        } else if (value instanceof java.sql.Date) {
            stmt.setDate(index, (java.sql.Date) value);
        } else if (value instanceof Boolean) {
            stmt.setBoolean(index, (Boolean) value);
        } else {
            stmt.setString(index, value.toString());
        }
    }
    
    /**
     * Génère un template CSV pour une table donnée
     */
    public static void generateCSVTemplate(String tableName, File outputFile) throws IOException {
        String template = TableSchemaManager.generateCSVTemplate(tableName);
        
        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write(template);
        }
        
        LOGGER.info("Template CSV généré pour " + tableName + " dans " + outputFile.getAbsolutePath());
    }
    
    // Interface pour les callback de progression
    public interface ProgressCallback {
        void onProgress(double progress);
    }
    
    // Classe pour les résultats améliorés
    public static class EnhancedUpdateResult {
        private final int recordsInserted;
        private final int recordsUpdated;
        private final int recordsUnchanged;
        private final List<String> errors;
        private final List<String> warnings;
        
        public EnhancedUpdateResult(int recordsInserted, int recordsUpdated, int recordsUnchanged, 
                                   List<String> errors, List<String> warnings) {
            this.recordsInserted = recordsInserted;
            this.recordsUpdated = recordsUpdated;
            this.recordsUnchanged = recordsUnchanged;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }
        
        public int getRecordsInserted() { return recordsInserted; }
        public int getRecordsUpdated() { return recordsUpdated; }
        public int getRecordsUnchanged() { return recordsUnchanged; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public int getTotalRecords() { return recordsInserted + recordsUpdated + recordsUnchanged; }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public boolean hasChanges() { return recordsInserted > 0 || recordsUpdated > 0; }
        
        public String getDetailedSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("=== RÉSUMÉ DE LA MISE À JOUR ===\n");
            summary.append("Nouveaux enregistrements: ").append(recordsInserted).append("\n");
            summary.append("Enregistrements modifiés: ").append(recordsUpdated).append("\n");
            summary.append("Enregistrements inchangés: ").append(recordsUnchanged).append("\n");
            summary.append("Total traité: ").append(getTotalRecords()).append("\n");
            
            if (hasWarnings()) {
                summary.append("\n⚠️ AVERTISSEMENTS (").append(warnings.size()).append("):\n");
                for (String warning : warnings) {
                    summary.append("- ").append(warning).append("\n");
                }
            }
            
            if (hasErrors()) {
                summary.append("\n❌ ERREURS (").append(errors.size()).append("):\n");
                for (String error : errors) {
                    summary.append("- ").append(error).append("\n");
                }
            }
            
            return summary.toString();
        }
    }
}