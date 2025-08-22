package application;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CSVProcessor {
    private static final Logger LOGGER = Logger.getLogger(CSVProcessor.class.getName());
    private static final String CSV_SEPARATOR = ";"; // Séparateur par défaut
    
    /**
     * Valide la cohérence entre le fichier CSV et la table de destination
     */
    public static ValidationResult validateCSVSchema(File csvFile, String tableName) throws IOException {
        List<String> csvColumns = extractCSVHeaders(csvFile);
        List<String> tableColumns = getTableColumns(tableName);
        
        ValidationResult result = new ValidationResult();
        
        // Vérifier les colonnes manquantes dans le CSV
        List<String> missingColumns = new ArrayList<>();
        List<String> requiredColumns = getRequiredColumns(tableName);
        
        for (String required : requiredColumns) {
            if (!csvColumns.contains(required)) {
                missingColumns.add(required);
            }
        }
        
        // Vérifier les colonnes en trop dans le CSV
        List<String> extraColumns = new ArrayList<>();
        for (String csvColumn : csvColumns) {
            if (!tableColumns.contains(csvColumn)) {
                extraColumns.add(csvColumn);
            }
        }
        
        result.setValid(missingColumns.isEmpty() && extraColumns.isEmpty());
        result.setMissingColumns(missingColumns);
        result.setExtraColumns(extraColumns);
        result.setTableColumns(tableColumns);
        result.setCsvColumns(csvColumns);
        
        return result;
    }
    
    /**
     * Extrait les en-têtes du fichier CSV
     */
    private static List<String> extractCSVHeaders(File csvFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Le fichier CSV est vide");
            }
            
            String[] headers = headerLine.split(CSV_SEPARATOR);
            List<String> headerList = new ArrayList<>();
            for (String header : headers) {
                headerList.add(header.trim());
            }
            return headerList;
        }
    }
    
    /**
     * Obtient les colonnes de la table depuis la base de données
     */
    private static List<String> getTableColumns(String tableName) {
        List<String> columns = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getColumns("exploit", null, tableName, null);
            
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des colonnes de la table " + tableName, e);
        }
        
        return columns;
    }
    
    /**
     * Obtient les colonnes obligatoires (NOT NULL) pour une table
     */
    private static List<String> getRequiredColumns(String tableName) {
        List<String> required = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getColumns("exploit", null, tableName, null);
            
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String isNullable = rs.getString("IS_NULLABLE");
                String isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
                
                // Ajouter aux obligatoires si NOT NULL et pas auto-increment
                if ("NO".equals(isNullable) && !"YES".equals(isAutoIncrement)) {
                    required.add(columnName);
                }
            }
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des colonnes obligatoires", e);
        }
        
        return required;
    }
    
    /**
     * Effectue la mise à jour intelligente avec différenciation insertion/modification
     */
    public static EnhancedUpdateResult processEnhancedCSVUpdate(File csvFile, String tableName, 
                                                               ProgressCallback updateCallback) throws Exception {
        
        int recordsInserted = 0;
        int recordsUpdated = 0;
        int recordsUnchanged = 0;
        StringBuilder errors = new StringBuilder();
        
        // Validation du schéma
        ValidationResult validation = validateCSVSchema(csvFile, tableName);
        if (!validation.isValid()) {
            throw new Exception("Schéma CSV incompatible: " + validation.getErrorMessage());
        }
        
        String primaryKey = ServicePermissions.getPrimaryKeyColumn(tableName);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            String[] headers = headerLine.split(CSV_SEPARATOR);
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim();
            }
            
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
                        String[] values = line.split(CSV_SEPARATOR);
                        Map<String, String> rowData = new HashMap<>();
                        
                        // Convertir la ligne en map
                        for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                            rowData.put(headers[i], values[i].trim());
                        }
                        
                        // Traitement selon le type de clé primaire
                        if ("id".equals(primaryKey)) {
                            // Pour les tables avec ID auto-incrémenté, toujours insérer
                            insertRecord(conn, tableName, rowData, headers);
                            recordsInserted++;
                        } else {
                            // Pour les tables avec matricule, vérifier existence
                            String keyValue = rowData.get(primaryKey);
                            if (keyValue == null || keyValue.isEmpty()) {
                                errors.append("Ligne ").append(currentLine)
                                      .append(": Valeur de clé primaire manquante (").append(primaryKey).append(")\n");
                                continue;
                            }
                            
                            Map<String, String> existingRecord = getExistingRecord(conn, tableName, primaryKey, keyValue);
                            
                            if (existingRecord.isEmpty()) {
                                // Nouvel enregistrement
                                insertRecord(conn, tableName, rowData, headers);
                                recordsInserted++;
                            } else {
                                // Vérifier si des modifications sont nécessaires
                                if (hasChanges(existingRecord, rowData, headers)) {
                                    updateRecord(conn, tableName, primaryKey, keyValue, rowData, headers);
                                    recordsUpdated++;
                                } else {
                                    recordsUnchanged++;
                                }
                            }
                        }
                        
                    } catch (Exception e) {
                        errors.append("Erreur à la ligne ").append(currentLine)
                              .append(": ").append(e.getMessage()).append("\n");
                        LOGGER.log(Level.WARNING, "Erreur de traitement à la ligne " + currentLine, e);
                    }
                }
                
                conn.commit();
                
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        
        return new EnhancedUpdateResult(recordsInserted, recordsUpdated, recordsUnchanged, errors.toString());
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
    private static boolean hasChanges(Map<String, String> existing, Map<String, String> newData, String[] headers) {
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
     * Insère un nouvel enregistrement
     */
    private static void insertRecord(Connection conn, String tableName, Map<String, String> rowData, 
                                   String[] headers) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<String> values = new ArrayList<>();
        
        for (String column : headers) {
            if (rowData.containsKey(column) && !rowData.get(column).isEmpty()) {
                columns.add(column);
                placeholders.add("?");
                values.add(rowData.get(column));
            }
        }
        
        if (columns.isEmpty()) {
            throw new SQLException("Aucune donnée à insérer");
        }
        
        String sql = "INSERT INTO " + tableName + " (" + String.join(", ", columns) + ") " +
                     "VALUES (" + String.join(", ", placeholders) + ")";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                stmt.setString(i + 1, values.get(i));
            }
            stmt.executeUpdate();
        }
    }
    
    /**
     * Met à jour un enregistrement existant
     */
    private static void updateRecord(Connection conn, String tableName, String primaryKey, 
                                   String keyValue, Map<String, String> rowData, String[] headers) throws SQLException {
        List<String> setClauses = new ArrayList<>();
        List<String> values = new ArrayList<>();
        
        for (String column : headers) {
            if (!column.equals(primaryKey) && rowData.containsKey(column)) {
                setClauses.add(column + " = ?");
                values.add(rowData.get(column));
            }
        }
        
        if (setClauses.isEmpty()) {
            return; // Rien à mettre à jour
        }
        
        String sql = "UPDATE " + tableName + " SET " + String.join(", ", setClauses) + 
                     " WHERE " + primaryKey + " = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                stmt.setString(i + 1, values.get(i));
            }
            stmt.setString(values.size() + 1, keyValue);
            stmt.executeUpdate();
        }
    }
    
    // Interface pour les callback de progression
    public interface ProgressCallback {
        void onProgress(double progress);
    }
    
    // Classe pour les résultats de validation
    public static class ValidationResult {
        private boolean valid;
        private List<String> missingColumns = new ArrayList<>();
        private List<String> extraColumns = new ArrayList<>();
        private List<String> tableColumns = new ArrayList<>();
        private List<String> csvColumns = new ArrayList<>();
        
        // Getters et setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public List<String> getMissingColumns() { return missingColumns; }
        public void setMissingColumns(List<String> missingColumns) { this.missingColumns = missingColumns; }
        
        public List<String> getExtraColumns() { return extraColumns; }
        public void setExtraColumns(List<String> extraColumns) { this.extraColumns = extraColumns; }
        
        public List<String> getTableColumns() { return tableColumns; }
        public void setTableColumns(List<String> tableColumns) { this.tableColumns = tableColumns; }
        
        public List<String> getCsvColumns() { return csvColumns; }
        public void setCsvColumns(List<String> csvColumns) { this.csvColumns = csvColumns; }
        
        public String getErrorMessage() {
            StringBuilder sb = new StringBuilder();
            if (!missingColumns.isEmpty()) {
                sb.append("Colonnes manquantes: ").append(String.join(", ", missingColumns)).append(". ");
            }
            if (!extraColumns.isEmpty()) {
                sb.append("Colonnes en trop: ").append(String.join(", ", extraColumns)).append(".");
            }
            return sb.toString();
        }
    }
    
    // Classe pour les résultats améliorés
    public static class EnhancedUpdateResult {
        private final int recordsInserted;
        private final int recordsUpdated;
        private final int recordsUnchanged;
        private final String errors;
        
        public EnhancedUpdateResult(int recordsInserted, int recordsUpdated, int recordsUnchanged, String errors) {
            this.recordsInserted = recordsInserted;
            this.recordsUpdated = recordsUpdated;
            this.recordsUnchanged = recordsUnchanged;
            this.errors = errors;
        }
        
        public int getRecordsInserted() { return recordsInserted; }
        public int getRecordsUpdated() { return recordsUpdated; }
        public int getRecordsUnchanged() { return recordsUnchanged; }
        public String getErrors() { return errors; }
        public int getTotalRecords() { return recordsInserted + recordsUpdated + recordsUnchanged; }
        public boolean hasErrors() { return errors != null && !errors.isEmpty(); }
        public boolean hasChanges() { return recordsInserted > 0 || recordsUpdated > 0; }
    }
}