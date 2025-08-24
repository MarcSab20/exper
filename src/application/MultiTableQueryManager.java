package application;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionnaire de requêtes multi-tables amélioré avec meilleure détection des colonnes
 */
public class MultiTableQueryManager {
    private static final Logger LOGGER = Logger.getLogger(MultiTableQueryManager.class.getName());
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    // Cache amélioré pour les colonnes par table
    private static final Map<String, List<String>> TABLE_COLUMNS_CACHE = new HashMap<>();
    private static final Map<String, String> COLUMN_TO_TABLE_CACHE = new HashMap<>();
    
    /**
     * AMÉLIORATION : Construction intelligente de requête multi-tables avec détection automatique
     */
    public static QueryInfo buildMultiTableQuery(List<String> usedColumns, List<String> constraints, 
                                                String service, String formatSortie) {
        try {
            // Construire le cache des colonnes si nécessaire
            buildColumnCache(service);
            
            // Analyser les contraintes pour identifier les colonnes utilisées
            Set<String> allUsedColumns = new HashSet<>(usedColumns);
            for (String constraint : constraints) {
                String columnInConstraint = extractColumnFromConstraint(constraint);
                if (columnInConstraint != null) {
                    allUsedColumns.add(columnInConstraint);
                }
            }
            
            // Identifier les tables nécessaires pour toutes les colonnes utilisées
            Set<String> requiredTables = new HashSet<>();
            Map<String, String> columnToTableMapping = new HashMap<>();
            
            for (String column : allUsedColumns) {
                String tableForColumn = findTableForColumn(column, service);
                if (tableForColumn != null) {
                    requiredTables.add(tableForColumn);
                    columnToTableMapping.put(column, tableForColumn);
                    LOGGER.info("Colonne '" + column + "' trouvée dans la table '" + tableForColumn + "'");
                } else {
                    LOGGER.warning("Colonne '" + column + "' non trouvée dans aucune table accessible au service " + service);
                }
            }
            
            // Si aucune table spécifique n'est trouvée, utiliser identite_personnelle par défaut
            if (requiredTables.isEmpty()) {
                requiredTables.add("identite_personnelle");
                LOGGER.info("Aucune table spécifique trouvée, utilisation de identite_personnelle par défaut");
            }
            
            // Construire la requête avec les bonnes tables
            String query = buildJoinQueryWithMapping(new ArrayList<>(requiredTables), usedColumns, 
                                                   constraints, formatSortie, columnToTableMapping);
            
            // Créer le mapping pour le résultat
            Map<String, Set<String>> tablesForColumns = new HashMap<>();
            for (String column : allUsedColumns) {
                String table = columnToTableMapping.get(column);
                if (table != null) {
                    tablesForColumns.computeIfAbsent(column, k -> new HashSet<>()).add(table);
                }
            }
            
            return new QueryInfo(query, new ArrayList<>(requiredTables), tablesForColumns);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la construction de la requête multi-table", e);
            throw new RuntimeException("Erreur lors de la construction de la requête: " + e.getMessage());
        }
    }
    
    /**
     * NOUVELLE MÉTHODE : Construit le cache des colonnes pour un service
     */
    private static void buildColumnCache(String service) {
        List<String> availableTables = ServicePermissions.getTablesForService(service);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (String tableName : availableTables) {
                if (!TABLE_COLUMNS_CACHE.containsKey(tableName)) {
                    List<String> columns = getTableColumns(tableName, conn);
                    TABLE_COLUMNS_CACHE.put(tableName, columns);
                    
                    // Construire le cache inverse (colonne -> table)
                    for (String column : columns) {
                        if (!COLUMN_TO_TABLE_CACHE.containsKey(column)) {
                            COLUMN_TO_TABLE_CACHE.put(column, tableName);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la construction du cache des colonnes", e);
        }
    }
    
    /**
     * NOUVELLE MÉTHODE : Trouve la table qui contient une colonne spécifique
     */
    private static String findTableForColumn(String columnName, String service) {
        // Vérifier d'abord le cache inverse
        if (COLUMN_TO_TABLE_CACHE.containsKey(columnName)) {
            String cachedTable = COLUMN_TO_TABLE_CACHE.get(columnName);
            // Vérifier que cette table est accessible pour le service
            if (ServicePermissions.getTablesForService(service).contains(cachedTable)) {
                return cachedTable;
            }
        }
        
        // Recherche manuelle dans toutes les tables du service
        List<String> availableTables = ServicePermissions.getTablesForService(service);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (String tableName : availableTables) {
                List<String> columns = getTableColumns(tableName, conn);
                if (columns.contains(columnName)) {
                    COLUMN_TO_TABLE_CACHE.put(columnName, tableName);
                    return tableName;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la recherche de la table pour la colonne " + columnName, e);
        }
        
        return null;
    }
    
    /**
     * MÉTHODE AMÉLIORÉE : Obtient les colonnes d'une table avec connexion partagée
     */
    private static List<String> getTableColumns(String tableName, Connection conn) throws SQLException {
        if (TABLE_COLUMNS_CACHE.containsKey(tableName)) {
            return TABLE_COLUMNS_CACHE.get(tableName);
        }
        
        List<String> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns("master", null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        
        TABLE_COLUMNS_CACHE.put(tableName, columns);
        return columns;
    }
    
    /**
     * Obtient les colonnes d'une table (méthode publique existante)
     */
    private static List<String> getTableColumns(String tableName) {
        if (TABLE_COLUMNS_CACHE.containsKey(tableName)) {
            return TABLE_COLUMNS_CACHE.get(tableName);
        }
        
        List<String> columns = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns("master", null, tableName, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la récupération des colonnes pour " + tableName, e);
        }
        
        TABLE_COLUMNS_CACHE.put(tableName, columns);
        return columns;
    }
    
    /**
     * MÉTHODE AMÉLIORÉE : Construit une requête avec JOINs utilisant le mapping des colonnes
     */
    private static String buildJoinQueryWithMapping(List<String> tables, List<String> usedColumns, 
            List<String> constraints, String formatSortie, Map<String, String> columnToTableMapping) {
        
        StringBuilder query = new StringBuilder();
        
        // SELECT clause
        query.append("SELECT ");
        
        if ("Graphique".equals(formatSortie)) {
            // Pour les graphiques, on a besoin d'un COUNT et du GROUP BY
            if (usedColumns.size() == 1) {
                query.append("COUNT(*) AS count, ");
                String column = usedColumns.get(0);
                String table = columnToTableMapping.get(column);
                if (table != null) {
                    query.append(table).append(".").append(column);
                } else {
                    query.append(column);
                }
            } else {
                // Pour les graphiques à 2 variables
                for (int i = 0; i < usedColumns.size(); i++) {
                    if (i > 0) query.append(", ");
                    String column = usedColumns.get(i);
                    String table = columnToTableMapping.get(column);
                    if (table != null) {
                        query.append(table).append(".").append(column);
                    } else {
                        query.append(column);
                    }
                }
                if (usedColumns.size() > 2) {
                    query.append(", COUNT(*) AS count");
                }
            }
        } else {
            // Pour les listes et tableaux
            List<String> qualifiedColumns = new ArrayList<>();
            for (String column : usedColumns) {
                String table = columnToTableMapping.get(column);
                if (table != null) {
                    qualifiedColumns.add(table + "." + column);
                } else {
                    qualifiedColumns.add(column);
                }
            }
            query.append(String.join(", ", qualifiedColumns));
        }
        
        // FROM clause avec JOINs INNER
        query.append(" FROM ");
        
        if (tables.size() == 1) {
            // Une seule table
            query.append(tables.get(0));
        } else {
            // Plusieurs tables - construire les JOINs
            String mainTable = findMainTable(tables);
            query.append(mainTable);
            
            for (String table : tables) {
                if (!table.equals(mainTable)) {
                    // Vérifier que les deux tables ont la colonne matricule
                    if (hasMatriculeColumn(mainTable) && hasMatriculeColumn(table)) {
                        query.append(" INNER JOIN ").append(table);
                        query.append(" ON ").append(mainTable).append(".matricule = ").append(table).append(".matricule");
                    } else {
                        LOGGER.warning("Impossible de joindre " + table + " avec " + mainTable + 
                                     " - colonne matricule manquante");
                    }
                }
            }
        }
        
        // WHERE clause avec colonnes qualifiées
        if (!constraints.isEmpty()) {
            query.append(" WHERE ");
            List<String> qualifiedConstraints = new ArrayList<>();
            
            for (String constraint : constraints) {
                String qualifiedConstraint = qualifyConstraintWithMapping(constraint, columnToTableMapping);
                qualifiedConstraints.add(qualifiedConstraint);
            }
            
            query.append(String.join(" AND ", qualifiedConstraints));
        }
        
        // GROUP BY pour les graphiques
        if ("Graphique".equals(formatSortie)) {
            query.append(" GROUP BY ");
            if (usedColumns.size() == 1) {
                String column = usedColumns.get(0);
                String table = columnToTableMapping.get(column);
                if (table != null) {
                    query.append(table).append(".").append(column);
                } else {
                    query.append(column);
                }
            } else {
                List<String> groupByColumns = new ArrayList<>();
                for (String column : usedColumns) {
                    if (!column.equalsIgnoreCase("count")) {
                        String table = columnToTableMapping.get(column);
                        if (table != null) {
                            groupByColumns.add(table + "." + column);
                        } else {
                            groupByColumns.add(column);
                        }
                    }
                }
                query.append(String.join(", ", groupByColumns));
            }
        }
        
        LOGGER.info("Requête construite: " + query.toString());
        return query.toString();
    }
    
    /**
     * NOUVELLE MÉTHODE : Qualifie une contrainte avec le mapping des tables
     */
    private static String qualifyConstraintWithMapping(String constraint, Map<String, String> columnToTableMapping) {
        String[] parts = constraint.split("\\s+", 3);
        if (parts.length < 3) {
            return constraint;
        }
        
        String column = parts[0];
        String operator = parts[1];
        String value = parts[2];
        
        String table = columnToTableMapping.get(column);
        if (table != null) {
            return table + "." + column + " " + operator + " " + value;
        } else {
            LOGGER.warning("Table non trouvée pour la colonne " + column + " dans la contrainte: " + constraint);
            return constraint;
        }
    }
    
    /**
     * NOUVELLE MÉTHODE : Vérifie si une table a la colonne matricule
     */
    private static boolean hasMatriculeColumn(String tableName) {
        List<String> columns = getTableColumns(tableName);
        return columns.contains("matricule");
    }
    
    /**
     * Extrait le nom de colonne d'une contrainte
     */
    private static String extractColumnFromConstraint(String constraint) {
        String[] parts = constraint.split("\\s+");
        return parts.length > 0 ? parts[0] : null;
    }
    
    /**
     * Trouve la table principale (généralement identite_personnelle si présente)
     */
    private static String findMainTable(List<String> tables) {
        if (tables.contains("identite_personnelle")) {
            return "identite_personnelle";
        }
        return tables.get(0);
    }
    
    /**
     * Obtient les colonnes disponibles pour un service avec préfixe de table
     */
    public static List<String> getAvailableColumnsWithTables(String service) {
        buildColumnCache(service);
        
        Set<String> result = new LinkedHashSet<>();
        List<String> tables = ServicePermissions.getTablesForService(service);
        
        for (String table : tables) {
            List<String> columns = getTableColumns(table);
            result.addAll(columns);
        }
        
        List<String> sortedResult = new ArrayList<>(result);
        Collections.sort(sortedResult);
        return sortedResult;
    }
    
    /**
     * Efface le cache des colonnes
     */
    public static void clearCache() {
        TABLE_COLUMNS_CACHE.clear();
        COLUMN_TO_TABLE_CACHE.clear();
        LOGGER.info("Cache multi-table effacé");
    }
    
    /**
     * NOUVELLE MÉTHODE : Debug - Affiche les informations des colonnes par table
     */
    public static void debugTableColumns(String service) {
        LOGGER.info("=== DEBUG COLONNES PAR TABLE - SERVICE: " + service + " ===");
        List<String> tables = ServicePermissions.getTablesForService(service);
        
        for (String table : tables) {
            List<String> columns = getTableColumns(table);
            LOGGER.info("Table " + table + " (" + columns.size() + " colonnes): " + String.join(", ", columns));
        }
        
        LOGGER.info("=== CACHE COLONNE -> TABLE ===");
        for (Map.Entry<String, String> entry : COLUMN_TO_TABLE_CACHE.entrySet()) {
            LOGGER.info("Colonne '" + entry.getKey() + "' -> Table '" + entry.getValue() + "'");
        }
    }
    
    /**
     * Classe pour stocker les informations d'une requête construite
     */
    public static class QueryInfo {
        private final String query;
        private final List<String> usedTables;
        private final Map<String, Set<String>> tablesForColumns;
        
        public QueryInfo(String query, List<String> usedTables, Map<String, Set<String>> tablesForColumns) {
            this.query = query;
            this.usedTables = usedTables;
            this.tablesForColumns = tablesForColumns;
        }
        
        public String getQuery() {
            return query;
        }
        
        public List<String> getUsedTables() {
            return usedTables;
        }
        
        public Map<String, Set<String>> getTablesForColumns() {
            return tablesForColumns;
        }
        
        public String getDebugInfo() {
            StringBuilder info = new StringBuilder();
            info.append("=== INFORMATIONS DE REQUÊTE ===\n");
            info.append("Tables utilisées: ").append(String.join(", ", usedTables)).append("\n");
            info.append("Colonnes par table:\n");
            for (Map.Entry<String, Set<String>> entry : tablesForColumns.entrySet()) {
                info.append("  ").append(entry.getKey()).append(" -> ").append(String.join(", ", entry.getValue())).append("\n");
            }
            info.append("Requête SQL:\n").append(query);
            return info.toString();
        }
    }
}