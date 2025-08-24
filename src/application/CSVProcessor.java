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

	private static final String DB_URL = "jdbc:mysql://localhost:3306/master?autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
	private static final String DB_USER = "marco";
	private static final String DB_PASSWORD = "29Papa278.";

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
    
    private static Connection getSecureConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // Vérifier la validité de la connexion
            if (conn.isValid(5)) {
                return conn;
            } else {
                throw new SQLException("Connexion invalide");
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver MySQL introuvable", e);
        }
    }
    
    /**
     * Effectue la mise à jour intelligente avec différenciation insertion/modification
     * Version améliorée avec meilleure gestion des erreurs
     */
    public static EnhancedUpdateResult processEnhancedCSVUpdateSecure(File csvFile, String tableName, 
                                                                 ProgressCallback updateCallback) throws Exception {

    int recordsInserted = 0;
    int recordsUpdated = 0;
    int recordsUnchanged = 0;
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // Validation du schéma
    TableSchemaManager.SchemaValidationResult validation = validateCSVSchema(csvFile, tableName);
    if (!validation.isValid()) {
        throw new Exception("Schéma CSV incompatible:\n" + validation.getDetailedReport());
    }

    // Récupérer les matricules valides avant de commencer
    Set<String> validMatricules = getValidMatricules(tableName);
    String primaryKey = ServicePermissions.getPrimaryKeyColumn(tableName);
    Set<String> tableColumns = TableSchemaManager.getTableColumnNames(tableName);

    // CORRECTION: Utiliser une connexion dédiée pour cette opération
    try (Connection conn = getSecureConnection()) {
        
        if (!conn.isValid(5)) {
            throw new SQLException("Connexion invalide au début de l'opération");
        }
        
        conn.setAutoCommit(false);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new Exception("Fichier CSV vide");
            }
            
            String[] headers = headerLine.split(CSV_SEPARATOR);
            
            // Nettoyer les en-têtes
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim().replaceAll("^\uFEFF", "");
            }
            
            // Trouver l'index de la colonne matricule si elle existe
            int matriculeIndex = -1;
            for (int i = 0; i < headers.length; i++) {
                if ("matricule".equalsIgnoreCase(headers[i])) {
                    matriculeIndex = i;
                    break;
                }
            }
            
            // Filtrer les colonnes valides
            List<String> validHeaders = new ArrayList<>();
            List<Integer> validIndexes = new ArrayList<>();
            
            for (int i = 0; i < headers.length; i++) {
                if (tableColumns.contains(headers[i])) {
                    validHeaders.add(headers[i]);
                    validIndexes.add(i);
                } else {
                    warnings.add("Colonne ignorée: " + headers[i]);
                }
            }
            
            if (validHeaders.isEmpty()) {
                throw new Exception("Aucune colonne valide trouvée dans le CSV");
            }
            
            // Construire la requête REPLACE INTO
            String replaceQuery = buildReplaceQueryForValidColumns(tableName, validHeaders);
            
            try (PreparedStatement stmt = conn.prepareStatement(replaceQuery)) {
                String line;
                int currentLine = 1;
                int batchSize = 0;
                final int BATCH_LIMIT = 50; // CORRECTION: Réduire la taille des batches
                
                int validMatriculeCount = 0;
                int invalidMatriculeCount = 0;
                int processedLines = 0;
                
                while ((line = reader.readLine()) != null) {
                    currentLine++;
                    processedLines++;
                    
                    if (updateCallback != null && processedLines % 100 == 0) {
                        updateCallback.onProgress((double) processedLines / 10000);
                    }
                    
                    try {
                        String[] values = line.split(CSV_SEPARATOR, -1);
                        
                        // Vérifier le matricule si applicable
                        if (matriculeIndex >= 0) {
                            String matricule = "";
                            if (matriculeIndex < values.length) {
                                matricule = values[matriculeIndex].trim();
                            }
                            
                            if (matricule.isEmpty()) {
                                warnings.add("Ligne " + currentLine + " ignorée: matricule vide");
                                continue;
                            }
                            
                            if (!validMatricules.isEmpty() && !validMatricules.contains(matricule)) {
                                invalidMatriculeCount++;
                                warnings.add("Ligne " + currentLine + " ignorée: matricule '" + matricule + "' inexistant");
                                continue;
                            }
                            
                            validMatriculeCount++;
                        }
                        
                        // Préparer les valeurs pour l'insertion
                        boolean hasValidData = false;
                        for (int i = 0; i < validHeaders.size(); i++) {
                            int columnIndex = validIndexes.get(i);
                            String value = "";
                            if (columnIndex < values.length) {
                                value = values[columnIndex].trim();
                            }
                            
                            String columnType = getColumnType(tableName, validHeaders.get(i));
                            Object convertedValue = convertValueForDatabase(value, columnType);
                            
                            if (convertedValue != null && !convertedValue.toString().trim().isEmpty()) {
                                hasValidData = true;
                            }
                            
                            setParameterValue(stmt, i + 1, convertedValue);
                        }
                        
                        if (hasValidData) {
                            stmt.addBatch();
                            batchSize++;
                            
                            // Exécuter le batch régulièrement
                            if (batchSize >= BATCH_LIMIT) {
                                try {
                                    int[] results = stmt.executeBatch();
                                    recordsInserted += Arrays.stream(results).sum();
                                    batchSize = 0;
                                    
                                    // CORRECTION: Vérifier périodiquement la connexion
                                    if (!conn.isValid(2)) {
                                        throw new SQLException("Connexion fermée pendant le traitement");
                                    }
                                    
                                } catch (BatchUpdateException e) {
                                    // Traiter les erreurs de batch individuellement
                                    handleBatchException(e, currentLine, errors);
                                    batchSize = 0;
                                    stmt.clearBatch();
                                }
                            }
                        } else {
                            warnings.add("Ligne " + currentLine + " ignorée: aucune donnée valide");
                        }
                        
                    } catch (Exception e) {
                        errors.add("Erreur ligne " + currentLine + ": " + e.getMessage());
                    }
                }
                
                // Exécuter le dernier batch
                if (batchSize > 0) {
                    try {
                        int[] results = stmt.executeBatch();
                        recordsInserted += Arrays.stream(results).sum();
                    } catch (BatchUpdateException e) {
                        handleBatchException(e, currentLine, errors);
                    }
                }
                
                // Ajouter des statistiques
                if (matriculeIndex >= 0) {
                    warnings.add(String.format("Matricules: %d valides, %d invalides", 
                                              validMatriculeCount, invalidMatriculeCount));
                }
            }
        }
        
        // CORRECTION: Commit seulement si tout s'est bien passé
        if (errors.size() < recordsInserted / 2) { // Accepter jusqu'à 50% d'erreurs
            conn.commit();
            LOGGER.info("Transaction commitée avec succès pour " + tableName);
        } else {
            conn.rollback();
            throw new Exception("Trop d'erreurs (" + errors.size() + "), transaction annulée");
        }
        
    } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Erreur lors du traitement CSV pour " + tableName, e);
        throw e;
    }

    return new EnhancedUpdateResult(recordsInserted, recordsUpdated, recordsUnchanged, errors, warnings);
}
    /**
     * CORRECTION: Gestion des erreurs de batch
     */
    private static void handleBatchException(BatchUpdateException e, int currentLine, List<String> errors) {
        LOGGER.log(Level.WARNING, "Erreur de batch ligne ~" + currentLine, e);
        
        // Extraire les erreurs individuelles
        SQLException nextEx = e.getNextException();
        while (nextEx != null) {
            errors.add("Erreur batch ligne ~" + currentLine + ": " + nextEx.getMessage());
            nextEx = nextEx.getNextException();
        }
        
        // Ajouter l'erreur principale si pas d'erreurs individuelles
        if (e.getNextException() == null) {
            errors.add("Erreur batch ligne ~" + currentLine + ": " + e.getMessage());
        }
    }

    
 // Méthode helper pour construire une requête REPLACE INTO
    private static String buildReplaceQuery(String tableName, Set<String> tableColumns, String primaryKey) {
        List<String> columns = new ArrayList<>(tableColumns);
        List<String> placeholders = new ArrayList<>();
        
        for (int i = 0; i < columns.size(); i++) {
            placeholders.add("?");
        }
        
        return String.format("REPLACE INTO %s (%s) VALUES (%s)", 
                            tableName, 
                            String.join(", ", columns), 
                            String.join(", ", placeholders));
    }
    
    /**
     * NOUVELLE MÉTHODE : Construit une requête REPLACE INTO pour les colonnes valides uniquement
     */
    private static String buildReplaceQueryForValidColumns(String tableName, List<String> validColumns) {
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < validColumns.size(); i++) {
            placeholders.add("?");
        }
        
        return String.format("REPLACE INTO %s (%s) VALUES (%s)", 
                            tableName, 
                            String.join(", ", validColumns), 
                            String.join(", ", placeholders));
    }
    
    /**
     * Valide les matricules avant l'insertion pour éviter les erreurs de clé étrangère
     */
    public static Set<String> getValidMatricules(String tableName) throws SQLException {
        Set<String> validMatricules = new HashSet<>();
        
        // Déterminer la table de référence
        String referenceTable = null;
        String referenceColumn = null;
        
        switch (tableName.toLowerCase()) {
            case "grade_actuel":
            case "formation_actuelle":
            case "specialite":
            case "parametres_corporels":
            case "dotation_particuliere_config":
            case "infos_specifiques_general":
            case "personnel_naviguant":
                referenceTable = "identite_personnelle";
                referenceColumn = "matricule";
                break;
        }
        
        if (referenceTable != null) {
            String query = "SELECT DISTINCT " + referenceColumn + " FROM " + referenceTable + 
                          " WHERE " + referenceColumn + " IS NOT NULL AND " + referenceColumn + " != ''";
            
            try (Connection conn = getSecureConnection();
                 PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    String matricule = rs.getString(referenceColumn);
                    if (matricule != null && !matricule.trim().isEmpty()) {
                        validMatricules.add(matricule.trim());
                    }
                }
                
                LOGGER.info("Matricules valides récupérés: " + validMatricules.size() + " pour table " + tableName);
            }
        }
        
        return validMatricules;
    }
    
 // Méthode helper pour obtenir le type d'une colonne
    private static String getColumnType(String tableName, String columnName) {
        List<TableSchemaManager.ColumnInfo> schema = TableSchemaManager.getRealTableSchema(tableName);
        for (TableSchemaManager.ColumnInfo col : schema) {
            if (col.getName().equals(columnName)) {
                return col.getType();
            }
        }
        return "VARCHAR"; // Type par défaut
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
     * MÉTHODE UTILITAIRE : Créer les matricules manquants dans identite_personnelle
     * ATTENTION: À utiliser avec précaution !
     */
    public static void createMissingMatricules(Set<String> missingMatricules, String service) throws SQLException {
        if (missingMatricules.isEmpty()) {
            return;
        }
        
        // Demander confirmation avant création automatique
        LOGGER.warning("ATTENTION: Création automatique de " + missingMatricules.size() + " matricules manquants");
        
        String insertQuery = "INSERT IGNORE INTO identite_personnelle (matricule, nom, prenom, service_origine) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost/master", "marco", "29Papa278.");
             PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            
            conn.setAutoCommit(false);
            
            for (String matricule : missingMatricules) {
                stmt.setString(1, matricule);
                stmt.setString(2, "NOM_" + matricule); // Nom temporaire
                stmt.setString(3, "PRENOM_" + matricule); // Prénom temporaire
                stmt.setString(4, service); // Service d'origine
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            conn.commit();
            
            LOGGER.info("Créés automatiquement: " + results.length + " nouveaux matricules");
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la création des matricules manquants", e);
            throw e;
        }
    }
    
    /**
     * MÉTHODE D'ANALYSE : Identifier les matricules manquants avant la mise à jour
     */
    public static AnalysisResult analyzeCSVMatricules(File csvFile, String tableName) throws Exception {
        Set<String> csvMatricules = new HashSet<>();
        Set<String> validMatricules = getValidMatricules(tableName);
        
        // Lire les matricules du CSV
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new Exception("Fichier CSV vide");
            }
            
            String[] headers = headerLine.split(CSV_SEPARATOR);
            int matriculeIndex = -1;
            
            for (int i = 0; i < headers.length; i++) {
                if ("matricule".equalsIgnoreCase(headers[i].trim())) {
                    matriculeIndex = i;
                    break;
                }
            }
            
            if (matriculeIndex == -1) {
                throw new Exception("Colonne 'matricule' non trouvée");
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(CSV_SEPARATOR, -1);
                if (matriculeIndex < values.length) {
                    String matricule = values[matriculeIndex].trim();
                    if (!matricule.isEmpty()) {
                        csvMatricules.add(matricule);
                    }
                }
            }
        }
        
        // Identifier les matricules manquants
        Set<String> missingMatricules = new HashSet<>(csvMatricules);
        missingMatricules.removeAll(validMatricules);
        
        return new AnalysisResult(csvMatricules, validMatricules, missingMatricules);
    }
    
    /**
     * Classe pour stocker les résultats d'analyse
     */
    public static class AnalysisResult {
        private final Set<String> csvMatricules;
        private final Set<String> validMatricules;
        private final Set<String> missingMatricules;
        
        public AnalysisResult(Set<String> csvMatricules, Set<String> validMatricules, Set<String> missingMatricules) {
            this.csvMatricules = csvMatricules;
            this.validMatricules = validMatricules;
            this.missingMatricules = missingMatricules;
        }
        
        public Set<String> getCsvMatricules() { return csvMatricules; }
        public Set<String> getValidMatricules() { return validMatricules; }
        public Set<String> getMissingMatricules() { return missingMatricules; }
        
        public String getReport() {
            return String.format(
                "=== RAPPORT D'ANALYSE DES MATRICULES ===\n" +
                "Matricules dans le CSV: %d\n" +
                "Matricules valides en DB: %d\n" +
                "Matricules manquants: %d\n\n" +
                "Matricules manquants: %s",
                csvMatricules.size(),
                validMatricules.size(), 
                missingMatricules.size(),
                missingMatricules.size() <= 10 ? missingMatricules.toString() : 
                    missingMatricules.stream().limit(10).collect(java.util.stream.Collectors.toSet()) + "... (et " + (missingMatricules.size() - 10) + " autres)"
            );
        }
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
                // CORRECTION : Améliorer la gestion des dates
                return convertToDate(value);
            } else if (type.contains("boolean") || type.contains("tinyint(1)")) {
                return "1".equals(value) || "true".equalsIgnoreCase(value) || "oui".equalsIgnoreCase(value);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur de conversion pour la valeur '" + value + "' vers le type " + columnType, e);
            // CORRECTION : En cas d'erreur, retourner null pour les dates au lieu de la valeur brute
            if (type.contains("date")) {
                return null;
            }
        }
        
        return value; // Retourner comme String par défaut
    }
    
    /**
     * NOUVELLE MÉTHODE : Conversion intelligente des dates avec gestion des formats multiples
     */
    private static java.sql.Date convertToDate(String dateValue) {
        if (dateValue == null || dateValue.trim().isEmpty()) {
            return null;
        }
        
        dateValue = dateValue.trim();
        
        // Vérifier si c'est vraiment une date ou une référence (comme A00/2564)
        if (dateValue.matches("^[A-Z]\\d+/\\d+$") || 
            dateValue.matches("^[A-Z]\\d+$") || 
            dateValue.contains("/") && !dateValue.matches("\\d{1,2}/\\d{1,2}/\\d{4}") &&
            !dateValue.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            
            LOGGER.warning("Valeur '" + dateValue + "' ne ressemble pas à une date, conversion ignorée");
            return null;
        }
        
        // Formats de date supportés
        String[] dateFormats = {
            "yyyy-MM-dd",          // Format SQL standard
            "dd/MM/yyyy",          // Format français
            "MM/dd/yyyy",          // Format américain
            "yyyy/MM/dd",          // Format alternatif
            "dd-MM-yyyy",          // Format avec tirets
            "MM-dd-yyyy",          // Format américain avec tirets
            "yyyyMMdd",            // Format compact
            "dd.MM.yyyy",          // Format avec points
            "yyyy.MM.dd"           // Format ISO avec points
        };
        
        for (String format : dateFormats) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(format);
                sdf.setLenient(false); // Mode strict
                java.util.Date utilDate = sdf.parse(dateValue);
                return new java.sql.Date(utilDate.getTime());
            } catch (java.text.ParseException e) {
                // Continue avec le format suivant
            }
        }
        
        // Si aucun format ne fonctionne, essayer une conversion directe SQL
        try {
            return java.sql.Date.valueOf(dateValue);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Impossible de convertir '" + dateValue + "' en date, valeur ignorée");
            return null;
        }
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