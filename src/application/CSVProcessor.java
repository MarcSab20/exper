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
             // Détecter le séparateur automatiquement
                String separator = detectSeparator(headerLine);
                LOGGER.info("Séparateur détecté pour la mise à jour: '" + (separator.equals("\t") ? "TAB" : separator) + "'");

                // Nettoyer le BOM si présent
                if (headerLine.startsWith("\uFEFF")) {
                    headerLine = headerLine.substring(1);
                }

                String[] headers = headerLine.split(java.util.regex.Pattern.quote(separator));
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
    public static EnhancedUpdateResult processEnhancedCSVUpdateSecureWithValidation(File csvFile, String tableName, 
            ProgressCallback updateCallback) throws Exception {

        LOGGER.info("=== DÉBUT DE LA MISE À JOUR SÉCURISÉE AVEC VALIDATION ===");
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int recordsInserted = 0;
        int recordsUpdated = 0;
        int recordsUnchanged = 0;
        
        try {
            // Étape 1 : Pré-validation des données CSV
            LOGGER.info("Étape 1 : Pré-validation des matricules");
            CSVValidationResult validation = preValidateCSVData(csvFile, tableName);
            
            if (updateCallback != null) updateCallback.onProgress(0.1);
            
            // Si il y a des matricules invalides, les signaler comme avertissements
            if (!validation.isAllRecordsValid() && !validation.getInvalidMatricules().isEmpty()) {
                for (String matricule : validation.getInvalidMatricules()) {
                    if (!matricule.startsWith("LIGNE_INCOMPLETE_")) {
                        warnings.add("⚠️ Matricule ignoré (non trouvé dans identite_personnelle): " + matricule);
                    }
                }
                LOGGER.info("Matricules invalides détectés: " + validation.getInvalidMatricules().size());
            }
            
            // Étape 2 : Créer un fichier CSV temporaire avec seulement les données valides
            File validatedCsvFile;
            if (validation.isAllRecordsValid()) {
                validatedCsvFile = csvFile; // Utiliser le fichier original
                LOGGER.info("Tous les matricules sont valides, utilisation du fichier original");
            } else {
                LOGGER.info("Création d'un fichier CSV temporaire avec les données valides");
                validatedCsvFile = createValidatedCSVFile(validation);
                LOGGER.info("Fichier CSV temporaire créé: " + validatedCsvFile.getAbsolutePath());
            }
            
            if (updateCallback != null) updateCallback.onProgress(0.2);
            
            // Étape 3 : Traitement du CSV validé avec la méthode de base
            LOGGER.info("Étape 3 : Traitement du CSV validé");
            EnhancedUpdateResult result = processBasicCSVUpdate(validatedCsvFile, tableName, updateCallback);
            
            // Étape 4 : Fusionner les avertissements
            errors.addAll(result.getErrors());
            warnings.addAll(result.getWarnings());
            recordsInserted = result.getRecordsInserted();
            recordsUpdated = result.getRecordsUpdated();
            recordsUnchanged = result.getRecordsUnchanged();
            
            // Nettoyer le fichier temporaire si nécessaire
            if (validatedCsvFile != csvFile) {
                try {
                    validatedCsvFile.delete();
                    LOGGER.info("Fichier CSV temporaire supprimé");
                } catch (Exception e) {
                    LOGGER.warning("Impossible de supprimer le fichier temporaire: " + e.getMessage());
                }
            }
            
            if (updateCallback != null) updateCallback.onProgress(1.0);
            
            LOGGER.info("=== MISE À JOUR TERMINÉE AVEC VALIDATION ===");
            LOGGER.info("Résultat: " + recordsInserted + " insérés, " + recordsUpdated + " modifiés, " + 
                       recordsUnchanged + " inchangés");
            
            return new EnhancedUpdateResult(recordsInserted, recordsUpdated, recordsUnchanged, errors, warnings);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la mise à jour avec validation", e);
            errors.add("Erreur critique: " + e.getMessage());
            return new EnhancedUpdateResult(recordsInserted, recordsUpdated, recordsUnchanged, errors, warnings);
        }
    }

    /**
     * CORRECTION: Gestion des erreurs de batch
     */
    private static void handleBatchExceptionWithFKCheck(BatchUpdateException e, int currentLine, 
        List<String> errors, List<String> warnings) {
    	LOGGER.log(Level.WARNING, "Erreur de batch ligne ~" + currentLine, e);
	
    	// Vérifier si c'est une erreur de clé étrangère
    	String errorMessage = e.getMessage();
		boolean isForeignKeyError = errorMessage != null && 
		(errorMessage.contains("foreign key constraint fails") || 
		errorMessage.contains("Cannot add or update a child row"));
		
		if (isForeignKeyError) {
			// Pour les erreurs de clé étrangère, ajouter un avertissement au lieu d'une erreur
			warnings.add(String.format("⚠️ Ligne ~%d ignorée: matricule inexistant dans identite_personnelle", currentLine));
	
			// Extraire le matricule de l'erreur si possible
			if (errorMessage.contains("matricule")) {
				warnings.add("Détail: " + extractMatriculeFromError(errorMessage));
			}
		} else {
			// Pour les autres erreurs, traitement normal
			SQLException nextEx = e.getNextException();
			while (nextEx != null) {
				errors.add("Erreur batch ligne ~" + currentLine + ": " + nextEx.getMessage());
				nextEx = nextEx.getNextException();
			}
	
			if (e.getNextException() == null) {
				errors.add("Erreur batch ligne ~" + currentLine + ": " + e.getMessage());
			}
		}
	}
    
    /**
     * NOUVELLE MÉTHODE : Traitement CSV de base sans validation de clés étrangères
     */
    private static EnhancedUpdateResult processBasicCSVUpdate(File csvFile, String tableName, 
            ProgressCallback updateCallback) throws Exception {
        
        LOGGER.info("=== DÉBUT DU TRAITEMENT CSV DE BASE ===");
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int recordsInserted = 0;
        int recordsUpdated = 0;
        int recordsUnchanged = 0;
        
        try (Connection connection = getSecureConnection()) {
            connection.setAutoCommit(false);
            
            // Lire l'en-tête et détecter le séparateur
            String separator = null;
            List<String> headers = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    throw new Exception("Fichier CSV vide");
                }
                
                separator = detectSeparator(headerLine);
                
                // Nettoyer le BOM si présent
                if (headerLine.startsWith("\uFEFF")) {
                    headerLine = headerLine.substring(1);
                }
                
                String[] headerArray = headerLine.split(java.util.regex.Pattern.quote(separator));
                for (String header : headerArray) {
                    headers.add(header.trim());
                }
            }
            
            // Obtenir les colonnes de la table et construire la requête
            Set<String> tableColumns = TableSchemaManager.getTableColumnNames(tableName);
            List<String> validColumns = new ArrayList<>();
            
            for (String header : headers) {
                if (tableColumns.contains(header)) {
                    validColumns.add(header);
                } else {
                    warnings.add("Colonne ignorée (non présente dans la table): " + header);
                }
            }
            
            if (validColumns.isEmpty()) {
                throw new Exception("Aucune colonne valide trouvée dans le CSV");
            }
            
            String upsertQuery = buildReplaceQueryForValidColumns(tableName, validColumns);
            
            // Traitement des données par batch
            final int BATCH_SIZE = 1000;
            int currentLine = 0;
            int processedLines = 0;
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8));
                 PreparedStatement stmt = connection.prepareStatement(upsertQuery)) {
                
                reader.readLine(); // Ignorer l'en-tête
                String line;
                
                while ((line = reader.readLine()) != null) {
                    currentLine++;
                    
                    try {
                        String[] values = line.split(java.util.regex.Pattern.quote(separator), -1);
                        
                        // Vérifier qu'on a assez de valeurs
                        if (values.length < validColumns.size()) {
                            warnings.add("Ligne " + currentLine + " ignorée: pas assez de colonnes");
                            continue;
                        }
                        
                        // Remplir les paramètres de la requête
                        for (int i = 0; i < validColumns.size(); i++) {
                            String value = i < values.length ? values[i].trim() : "";
                            if (value.isEmpty()) {
                                stmt.setNull(i + 1, Types.VARCHAR);
                            } else {
                                // Conversion selon le type de colonne
                                String columnType = getColumnType(tableName, validColumns.get(i));
                                Object convertedValue = convertValueForDatabase(value, columnType);
                                setParameterValue(stmt, i + 1, convertedValue);
                            }
                        }
                        
                        stmt.addBatch();
                        processedLines++;
                        
                        // Exécuter le batch périodiquement
                        if (processedLines % BATCH_SIZE == 0) {
                            try {
                                int[] results = stmt.executeBatch();
                                recordsInserted += countResults(results);
                                
                                if (updateCallback != null) {
                                    double progress = 0.2 + (0.7 * processedLines / Math.max(1, currentLine));
                                    updateCallback.onProgress(Math.min(progress, 0.9));
                                }
                            } catch (BatchUpdateException e) {
                                handleBatchExceptionWithFKCheck(e, currentLine, errors, warnings);
                            }
                        }
                        
                    } catch (Exception e) {
                        warnings.add("Erreur ligne " + currentLine + ": " + e.getMessage());
                    }
                }
                
                // Exécuter le dernier batch
                if (processedLines % BATCH_SIZE != 0) {
                    try {
                        int[] results = stmt.executeBatch();
                        recordsInserted += countResults(results);
                    } catch (BatchUpdateException e) {
                        handleBatchExceptionWithFKCheck(e, currentLine, errors, warnings);
                    }
                }
            }
            
            connection.commit();
            
            LOGGER.info("=== TRAITEMENT CSV DE BASE TERMINÉ ===");
            LOGGER.info("Lignes traitées: " + processedLines);
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur SQL lors du traitement CSV", e);
            errors.add("Erreur de base de données: " + e.getMessage());
        }
        
        return new EnhancedUpdateResult(recordsInserted, recordsUpdated, recordsUnchanged, errors, warnings);
    }
    
    /**
     * MÉTHODE UTILITAIRE : Compte les résultats d'un batch
     */
    private static int countResults(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result > 0) {
                count++;
            }
        }
        return count;
    }

    
    /**
     * NOUVELLE MÉTHODE : Détection simple du séparateur
     */
    private static String detectSeparator(String headerLine) {
        // Nettoyer le BOM si présent
        if (headerLine.startsWith("\uFEFF")) {
            headerLine = headerLine.substring(1);
        }
        
        LOGGER.info("Ligne d'en-tête brute: '" + headerLine + "'");
        LOGGER.info("Longueur de la ligne: " + headerLine.length());
        
        // Compter les occurrences de chaque séparateur possible
        int tabCount = 0;
        int semicolonCount = 0;
        int commaCount = 0;
        
        // Méthode plus précise pour compter les tabulations
        for (int i = 0; i < headerLine.length(); i++) {
            char c = headerLine.charAt(i);
            if (c == '\t') {
                tabCount++;
            } else if (c == ';') {
                semicolonCount++;
            } else if (c == ',') {
                commaCount++;
            }
        }
        
        LOGGER.info("Séparateurs détectés - TAB: " + tabCount + ", point-virgule: " + semicolonCount + ", virgule: " + commaCount);
        
        // Retourner le séparateur le plus fréquent
        if (tabCount > 0 && tabCount >= semicolonCount && tabCount >= commaCount) {
            LOGGER.info("Séparateur sélectionné: TABULATION");
            return "\t";
        } else if (semicolonCount > 0 && semicolonCount >= commaCount) {
            LOGGER.info("Séparateur sélectionné: POINT-VIRGULE");
            return ";";
        } else {
            LOGGER.info("Séparateur sélectionné: VIRGULE (défaut)");
            return ",";
        }
    }
    
    /**
     * MÉTHODE UTILITAIRE : Extraire le matricule d'un message d'erreur de clé étrangère
     */
    private static String extractMatriculeFromError(String errorMessage) {
        // Tenter d'extraire le matricule du message d'erreur
        // Cette méthode peut être améliorée selon le format exact des messages
        try {
            // Exemple de message: "foreign key constraint fails (master.table, CONSTRAINT fk FOREIGN KEY (matricule) REFERENCES identite_personnelle (matricule))"
            if (errorMessage.contains("matricule")) {
                return "Problème de référence matricule";
            }
        } catch (Exception ex) {
            // Ignorer les erreurs d'extraction
        }
        return "Référence invalide";
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
     * NOUVELLE MÉTHODE : Pré-validation des matricules et nettoyage des données CSV
     */
    private static CSVValidationResult preValidateCSVData(File csvFile, String tableName) throws Exception {
        CSVValidationResult result = new CSVValidationResult();
        
        // Obtenir les matricules valides
        Set<String> validMatricules = getValidMatricules(tableName);
        
        if (validMatricules.isEmpty()) {
            LOGGER.warning("Aucun matricule de référence trouvé - tous les enregistrements seront acceptés");
            result.setAllRecordsValid(true);
            return result;
        }

        List<String> validLines = new ArrayList<>();
        List<String> invalidMatricules = new ArrayList<>();
        int totalLines = 0;
        int validRecords = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new Exception("Fichier CSV vide");
            }
            
            // Détecter le séparateur automatiquement
            String separator = detectSeparator(headerLine);
            
            // Ajouter l'en-tête aux lignes valides
            validLines.add(headerLine);
            
            // Nettoyer le BOM si présent
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            
            String[] headers = headerLine.split(java.util.regex.Pattern.quote(separator));
            
            // Nettoyer les en-têtes
            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim();
            }
            
            // Trouver l'index de la colonne matricule
            int matriculeIndex = -1;
            for (int i = 0; i < headers.length; i++) {
                if ("matricule".equalsIgnoreCase(headers[i])) {
                    matriculeIndex = i;
                    break;
                }
            }
            
            if (matriculeIndex == -1) {
                LOGGER.info("Aucune colonne 'matricule' trouvée - validation de clé étrangère ignorée");
                result.setAllRecordsValid(true);
                return result;
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                String[] values = line.split(java.util.regex.Pattern.quote(separator), -1);
                
                if (matriculeIndex < values.length) {
                    String matricule = values[matriculeIndex].trim();
                    
                    if (matricule.isEmpty()) {
                        // Enregistrer comme invalide mais ne pas compter dans les statistiques
                        continue;
                    }
                    
                    if (validMatricules.contains(matricule)) {
                        validLines.add(line);
                        validRecords++;
                    } else {
                        invalidMatricules.add(matricule);
                    }
                } else {
                    // Ligne avec colonnes manquantes - ignorer
                    invalidMatricules.add("LIGNE_INCOMPLETE_" + totalLines);
                }
            }
        }
        
        result.setTotalRecords(totalLines);
        result.setValidRecords(validRecords);
        result.setInvalidMatricules(invalidMatricules);
        result.setValidLines(validLines);
        
        LOGGER.info(String.format("Pré-validation terminée: %d/%d enregistrements valides, %d matricules invalides", 
                                  validRecords, totalLines, invalidMatricules.size()));
        
        return result;
    }

    
    /**
     * NOUVELLE MÉTHODE : Créer un fichier CSV temporaire avec seulement les enregistrements valides
     */
    private static File createValidatedCSVFile(CSVValidationResult validationResult) throws IOException {
        File tempFile = File.createTempFile("validated_csv_", ".csv");
        tempFile.deleteOnExit();
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile, StandardCharsets.UTF_8))) {
            for (String line : validationResult.getValidLines()) {
                writer.write(line);
                writer.newLine();
            }
        }
        
        return tempFile;
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
    
    // Lire les matricules du CSV avec détection automatique du séparateur
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new Exception("Fichier CSV vide");
        }
        
        // Détecter le séparateur automatiquement
        String separator = detectSeparator(headerLine);
        
        // Nettoyer le BOM si présent
        if (headerLine.startsWith("\uFEFF")) {
            headerLine = headerLine.substring(1);
        }
        
        LOGGER.info("En-tête brut: '" + headerLine + "'");
        LOGGER.info("Séparateur utilisé: '" + (separator.equals("\t") ? "TAB" : separator) + "'");
        
        String[] headers = headerLine.split(java.util.regex.Pattern.quote(separator));
        int matriculeIndex = -1;
        
        // Debug: afficher tous les en-têtes
        LOGGER.info("=== EN-TÊTES DÉTECTÉS ===");
        for (int i = 0; i < headers.length; i++) {
            String cleanHeader = headers[i].trim();
            LOGGER.info("En-tête[" + i + "]: '" + cleanHeader + "'");
            if ("matricule".equalsIgnoreCase(cleanHeader)) {
                matriculeIndex = i;
                LOGGER.info("✅ Colonne 'matricule' trouvée à l'index: " + i);
            }
        }
        
        if (matriculeIndex == -1) {
            // Recherche plus permissive
            for (int i = 0; i < headers.length; i++) {
                String cleanHeader = headers[i].trim().toLowerCase();
                if (cleanHeader.contains("matricule") || cleanHeader.contains("matr")) {
                    matriculeIndex = i;
                    LOGGER.info("✅ Colonne matricule trouvée (recherche étendue) à l'index: " + i + " ('" + headers[i] + "')");
                    break;
                }
            }
        }
        
        if (matriculeIndex == -1) {
            StringBuilder availableHeaders = new StringBuilder();
            for (String header : headers) {
                if (availableHeaders.length() > 0) availableHeaders.append(", ");
                availableHeaders.append("'").append(header.trim()).append("'");
            }
            throw new Exception("Colonne 'matricule' non trouvée. En-têtes disponibles: " + availableHeaders.toString());
        }
        
        // Lire les données
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String[] values = line.split(java.util.regex.Pattern.quote(separator), -1);
            if (matriculeIndex < values.length) {
                String matricule = values[matriculeIndex].trim();
                if (!matricule.isEmpty()) {
                    csvMatricules.add(matricule);
                }
            } else {
                LOGGER.warning("Ligne " + lineNumber + " n'a pas assez de colonnes");
            }
        }
        
        LOGGER.info("Total matricules extraits: " + csvMatricules.size());
    }
    
    // Identifier les matricules manquants
    Set<String> missingMatricules = new HashSet<>(csvMatricules);
    missingMatricules.removeAll(validMatricules);
    
    LOGGER.info("Matricules manquants: " + missingMatricules.size());
    
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
     * MÉTHODE UTILITAIRE : Créer un rapport détaillé de validation
     */
    public static String createValidationReport(CSVValidationResult validation, String tableName) {
        StringBuilder report = new StringBuilder();
        
        report.append("=== RAPPORT DE VALIDATION DES MATRICULES ===\n");
        report.append("Table de destination: ").append(tableName).append("\n");
        report.append("Total d'enregistrements dans le CSV: ").append(validation.getTotalRecords()).append("\n");
        report.append("Enregistrements avec matricules valides: ").append(validation.getValidRecords()).append("\n");
        report.append("Matricules invalides: ").append(validation.getInvalidMatricules().size()).append("\n");
        report.append("Pourcentage de validité: ").append(String.format("%.1f%%", validation.getValidPercentage())).append("\n\n");
        
        if (!validation.getInvalidMatricules().isEmpty()) {
            report.append("MATRICULES NON TROUVÉS DANS identite_personnelle:\n");
            List<String> examples = validation.getInvalidMatricules().stream()
                                             .filter(m -> !m.startsWith("LIGNE_INCOMPLETE_"))
                                             .limit(20)
                                             .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            for (String matricule : examples) {
                report.append("  - ").append(matricule).append("\n");
            }
            
            if (validation.getInvalidMatricules().size() > 20) {
                report.append("  ... et ").append(validation.getInvalidMatricules().size() - 20).append(" autres\n");
            }
            
            report.append("\nRECOMMANDATIONS:\n");
            report.append("- Vérifiez que les matricules existent dans la table identite_personnelle\n");
            report.append("- Corrigez les matricules erronés dans votre fichier CSV\n");
            report.append("- Ou ajoutez d'abord les matricules manquants dans identite_personnelle\n");
        }
        
        return report.toString();
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
    
    /**
     * NOUVELLE CLASSE : Résultat de validation CSV
     */
    public static class CSVValidationResult {
        private int totalRecords;
        private int validRecords;
        private List<String> invalidMatricules = new ArrayList<>();
        private List<String> validLines = new ArrayList<>();
        private boolean allRecordsValid = false;
        
        // Getters et setters
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
        
        public int getValidRecords() { return validRecords; }
        public void setValidRecords(int validRecords) { this.validRecords = validRecords; }
        
        public List<String> getInvalidMatricules() { return invalidMatricules; }
        public void setInvalidMatricules(List<String> invalidMatricules) { this.invalidMatricules = invalidMatricules; }
        
        public List<String> getValidLines() { return validLines; }
        public void setValidLines(List<String> validLines) { this.validLines = validLines; }
        
        public boolean isAllRecordsValid() { return allRecordsValid; }
        public void setAllRecordsValid(boolean allRecordsValid) { this.allRecordsValid = allRecordsValid; }
        
        public double getValidPercentage() {
            return totalRecords > 0 ? (double) validRecords / totalRecords * 100.0 : 0.0;
        }
        
        public String getSummary() {
            return String.format("Validation: %d/%d enregistrements valides (%.1f%%)", 
                               validRecords, totalRecords, getValidPercentage());
        }
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