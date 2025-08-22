package application;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CSVProcessor {
    private static final Logger LOGGER = Logger.getLogger(CSVProcessor.class.getName());
    private static final String CSV_SEPARATOR = ";"; // Séparateur par défaut (peut être changé)
    
    /**
     * Vérifie si le fichier CSV est compatible avec le schéma de la table
     * @param csvFile Le fichier CSV
     * @param tableName Le nom de la table
     * @return Vrai si le schéma est compatible, faux sinon
     * @throws IOException En cas d'erreur de lecture du fichier
     */
    public static boolean validateCSVSchema(File csvFile, String tableName) throws IOException {
        List<ColumnInfo> tableSchema = DatabaseManager.getTableSchema(tableName);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Le fichier CSV est vide");
            }
            
            // Extraire les en-têtes du CSV
            String[] headers = headerLine.split(CSV_SEPARATOR);
            
            // Vérifier si toutes les colonnes obligatoires sont présentes
            for (ColumnInfo column : tableSchema) {
                if (!column.isNullable() && !column.isPrimaryKey() && !Arrays.asList(headers).contains(column.getName())) {
                    return false;
                }
            }
            
            // Vérifier si le CSV contient des colonnes qui n'existent pas dans la table
            for (String header : headers) {
                boolean columnExists = false;
                for (ColumnInfo column : tableSchema) {
                    if (column.getName().equalsIgnoreCase(header.trim())) {
                        columnExists = true;
                        break;
                    }
                }
                if (!columnExists) {
                    return false;
                }
            }
            
            return true;
        }
    }
    
    /**
     * Effectue la mise à jour de la table à partir du fichier CSV
     * @param csvFile Le fichier CSV
     * @param tableName Le nom de la table
     * @param primaryKeyColumn La colonne de clé primaire (généralement le nom pour la table personnel)
     * @param updateCallback Callback pour les mises à jour de progression
     * @return Un objet contenant les résultats de la mise à jour
     * @throws Exception En cas d'erreur
     */
    public static UpdateResult processCSVUpdate(File csvFile, String tableName, String primaryKeyColumn, 
                                        ProgressCallback updateCallback) throws Exception {
        
        int recordsUpdated = 0;
        int recordsInserted = 0;
        StringBuilder errors = new StringBuilder();
        
        // Étape 1: Valider le schéma
        if (!validateCSVSchema(csvFile, tableName)) {
            throw new Exception("Le format du fichier CSV n'est pas compatible avec la structure de la table " + tableName);
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String line;
            String headerLine = reader.readLine();
            
            if (headerLine == null) {
                throw new IOException("Le fichier CSV est vide");
            }
            
            // Extraire les en-têtes du CSV
            String[] headers = headerLine.split(CSV_SEPARATOR);
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim();
            }
            
            // Compter le nombre total de lignes pour la barre de progression
            int totalLines = 0;
            try (LineNumberReader lnr = new LineNumberReader(new FileReader(csvFile))) {
                while (lnr.readLine() != null) {
                    totalLines++;
                }
                // Soustraire 1 pour l'en-tête
                totalLines--;
            }
            
            // Traiter chaque ligne
            int currentLine = 0;
            Connection conn = DatabaseManager.getConnection();
            // Désactiver l'auto-commit pour la transaction
            conn.setAutoCommit(false);
            
            try {
                while ((line = reader.readLine()) != null) {
                    currentLine++;
                    if (updateCallback != null) {
                        updateCallback.onProgress((double) currentLine / totalLines);
                    }
                    
                    try {
                        String[] values = line.split(CSV_SEPARATOR);
                        Map<String, String> rowData = new HashMap<>();
                        
                        // Convertir la ligne en map clé-valeur
                        for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                            rowData.put(headers[i], values[i].trim());
                        }
                        
                        // Vérifier si la clé primaire est présente
                        if (!rowData.containsKey(primaryKeyColumn) || rowData.get(primaryKeyColumn).isEmpty()) {
                            errors.append("Ligne ").append(currentLine)
                                  .append(": Valeur de clé primaire manquante\n");
                            continue;
                        }
                        
                        // Vérifier si l'enregistrement existe déjà
                        String primaryKeyValue = rowData.get(primaryKeyColumn);
                        boolean recordExists = checkIfRecordExists(conn, tableName, primaryKeyColumn, primaryKeyValue);
                        
                        if (recordExists) {
                            // Mettre à jour l'enregistrement existant
                            updateRecord(conn, tableName, primaryKeyColumn, primaryKeyValue, rowData, headers);
                            recordsUpdated++;
                        } else {
                            // Insérer un nouvel enregistrement
                            insertRecord(conn, tableName, rowData, headers);
                            recordsInserted++;
                        }
                    } catch (Exception e) {
                        errors.append("Erreur à la ligne ").append(currentLine)
                              .append(": ").append(e.getMessage()).append("\n");
                        LOGGER.log(Level.WARNING, "Erreur de traitement à la ligne " + currentLine, e);
                    }
                }
                
                // Valider la transaction
                conn.commit();
                
            } catch (Exception e) {
                // Annuler la transaction en cas d'erreur
                conn.rollback();
                throw e;
            } finally {
                // Réactiver l'auto-commit
                conn.setAutoCommit(true);
            }
        }
        
        return new UpdateResult(recordsUpdated, recordsInserted, errors.toString());
    }
    
    private static boolean checkIfRecordExists(Connection conn, String tableName, 
                                             String primaryKeyColumn, String primaryKeyValue) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE " + primaryKeyColumn + " = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, primaryKeyValue);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        
        return false;
    }
    
    private static void updateRecord(Connection conn, String tableName, String primaryKeyColumn, 
                                   String primaryKeyValue, Map<String, String> rowData, String[] headers) throws SQLException {
        
        StringBuilder sql = new StringBuilder("UPDATE " + tableName + " SET ");
        List<String> setClauses = new ArrayList<>();
        List<String> values = new ArrayList<>();
        
        for (String column : headers) {
            if (!column.equals(primaryKeyColumn) && rowData.containsKey(column)) {
                setClauses.add(column + " = ?");
                values.add(rowData.get(column));
            }
        }
        
        sql.append(String.join(", ", setClauses));
        sql.append(" WHERE ").append(primaryKeyColumn).append(" = ?");
        
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                stmt.setString(i + 1, values.get(i));
            }
            stmt.setString(values.size() + 1, primaryKeyValue);
            
            stmt.executeUpdate();
        }
    }
    
    private static void insertRecord(Connection conn, String tableName, Map<String, String> rowData, 
                                   String[] headers) throws SQLException {
        
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<String> values = new ArrayList<>();
        
        for (String column : headers) {
            if (rowData.containsKey(column)) {
                columns.add(column);
                placeholders.add("?");
                values.add(rowData.get(column));
            }
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
    
    // Interface pour les callback de progression
    public interface ProgressCallback {
        void onProgress(double progress);
    }
    
    // Classe pour retourner les résultats de la mise à jour
    public static class UpdateResult {
        private final int recordsUpdated;
        private final int recordsInserted;
        private final String errors;
        
        public UpdateResult(int recordsUpdated, int recordsInserted, String errors) {
            this.recordsUpdated = recordsUpdated;
            this.recordsInserted = recordsInserted;
            this.errors = errors;
        }
        
        public int getRecordsUpdated() { return recordsUpdated; }
        public int getRecordsInserted() { return recordsInserted; }
        public String getErrors() { return errors; }
        public int getTotalRecords() { return recordsUpdated + recordsInserted; }
        public boolean hasErrors() { return errors != null && !errors.isEmpty(); }
    }
}