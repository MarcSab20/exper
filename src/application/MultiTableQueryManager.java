package application;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionnaire de requêtes multi-tables permettant d'interroger plusieurs tables
 * liées par la clé matricule automatiquement
 */
public class MultiTableQueryManager {
    private static final Logger LOGGER = Logger.getLogger(MultiTableQueryManager.class.getName());
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    // Cache pour les colonnes par table
    private static final Map<String, List<String>> TABLE_COLUMNS_CACHE = new HashMap<>();
    
    /**
     * Détermine automatiquement les tables nécessaires pour les colonnes utilisées
     */
    public static QueryInfo buildMultiTableQuery(List<String> usedColumns, List<String> constraints, 
                                                String service, String formatSortie) {
        try {
            Map<String, Set<String>> tablesForColumns = findTablesForColumns(usedColumns, service);
            Set<String> requiredTables = new HashSet<>();
            
            // Ajouter les tables nécessaires pour chaque colonne
            for (String column : usedColumns) {
                if (tablesForColumns.containsKey(column)) {
                    requiredTables.addAll(tablesForColumns.get(column));
                }
            }
            
            // Ajouter les tables nécessaires pour les contraintes
            for (String constraint : constraints) {
                String columnInConstraint = extractColumnFromConstraint(constraint);
                if (columnInConstraint != null && tablesForColumns.containsKey(columnInConstraint)) {
                    requiredTables.addAll(tablesForColumns.get(columnInConstraint));
                }
            }
            
            // Si aucune table spécifique n'est trouvée, utiliser identite_personnelle par défaut
            if (requiredTables.isEmpty()) {
                requiredTables.add("identite_personnelle");
            }
            
            // Construire la requête
            String query = buildJoinQuery(new ArrayList<>(requiredTables), usedColumns, constraints, formatSortie);
            
            return new QueryInfo(query, new ArrayList<>(requiredTables), tablesForColumns);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la construction de la requête multi-table", e);
            throw new RuntimeException("Erreur lors de la construction de la requête: " + e.getMessage());
        }
    }
    
    /**
     * Trouve les tables qui contiennent chaque colonne
     */
    private static Map<String, Set<String>> findTablesForColumns(List<String> columns, String service) {
        Map<String, Set<String>> result = new HashMap<>();
        List<String> availableTables = ServicePermissions.getTablesForService(service);
        
        for (String column : columns) {
            Set<String> tablesWithColumn = new HashSet<>();
            
            for (String table : availableTables) {
                List<String> tableColumns = getTableColumns(table);
                if (tableColumns.contains(column)) {
                    tablesWithColumn.add(table);
                }
            }
            
            if (!tablesWithColumn.isEmpty()) {
                result.put(column, tablesWithColumn);
            }
        }
        
        return result;
    }
    
    /**
     * Obtient les colonnes d'une table (avec cache)
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
     * Extrait le nom de colonne d'une contrainte
     */
    private static String extractColumnFromConstraint(String constraint) {
        // Format attendu: "colonne operateur valeur"
        String[] parts = constraint.split("\\s+");
        return parts.length > 0 ? parts[0] : null;
    }
    
    /**
     * Construit une requête avec JOINs automatiques
     */
    private static String buildJoinQuery(List<String> tables, List<String> usedColumns, 
    	List<String> constraints, String formatSortie) {
    	StringBuilder query = new StringBuilder();
	
    	// SELECT clause
    	query.append("SELECT ");
	
    	if ("Graphique".equals(formatSortie)) {
    		// Pour les graphiques, on a besoin d'un COUNT et du GROUP BY
    		if (usedColumns.size() == 1) {
    			query.append("COUNT(*) AS count, ");
    			query.append(buildQualifiedColumnName(usedColumns.get(0), tables));
    		} else {
    			// Pour les graphiques à 2 variables
    			query.append(buildQualifiedColumnName(usedColumns.get(0), tables)).append(", ");
    			query.append(buildQualifiedColumnName(usedColumns.get(1), tables));
    			if (usedColumns.size() > 2) {
    				query.append(", COUNT(*) AS count");
    			}
    		}
    	} else {
    		// Pour les listes et tableaux
    		List<String> qualifiedColumns = new ArrayList<>();
    		for (String column : usedColumns) {
    			qualifiedColumns.add(buildQualifiedColumnName(column, tables));
    		}
    		query.append(String.join(", ", qualifiedColumns));
    	}
	
    	// FROM clause avec JOINs INNER au lieu de LEFT JOIN pour éviter les valeurs nulles
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
    				// Utiliser INNER JOIN au lieu de LEFT JOIN pour de meilleures performances
    				// et éviter les résultats avec des valeurs nulles
    				query.append(" INNER JOIN ").append(table);
    				query.append(" ON ").append(mainTable).append(".matricule = ").append(table).append(".matricule");
    			}
    		}
    	}
	
    	// WHERE clause
    	if (!constraints.isEmpty()) {
    		query.append(" WHERE ");
    		List<String> qualifiedConstraints = new ArrayList<>();
	
    		for (String constraint : constraints) {
    			String qualifiedConstraint = qualifyConstraint(constraint, tables);
    			qualifiedConstraints.add(qualifiedConstraint);
    		}
	
    		query.append(String.join(" AND ", qualifiedConstraints));
    	}
	
    	// GROUP BY pour les graphiques
    	if ("Graphique".equals(formatSortie)) {
    		query.append(" GROUP BY ");
    		if (usedColumns.size() == 1) {
    			query.append(buildQualifiedColumnName(usedColumns.get(0), tables));
    		} else {
    			List<String> groupByColumns = new ArrayList<>();
    			for (String column : usedColumns) {
    				if (!column.equalsIgnoreCase("count")) {
    					groupByColumns.add(buildQualifiedColumnName(column, tables));
    				}
    			}
    			query.append(String.join(", ", groupByColumns));
    		}
    	}

    	return query.toString();
    }
   
    
    /**
     * Construit un nom de colonne qualifié (table.colonne)
     */
    private static String buildQualifiedColumnName(String column, List<String> tables) {
        // Trouver la table qui contient cette colonne
        for (String table : tables) {
            List<String> tableColumns = getTableColumns(table);
            if (tableColumns.contains(column)) {
                return table + "." + column;
            }
        }
        
        // Si pas trouvé, utiliser la première table
        if (!tables.isEmpty()) {
            return tables.get(0) + "." + column;
        }
        
        return column;
    }
    
    /**
     * Qualifie une contrainte avec le nom de table approprié
     */
    private static String qualifyConstraint(String constraint, List<String> tables) {
        String[] parts = constraint.split("\\s+", 3);
        if (parts.length < 3) {
            return constraint;
        }
        
        String column = parts[0];
        String operator = parts[1];
        String value = parts[2];
        
        String qualifiedColumn = buildQualifiedColumnName(column, tables);
        return qualifiedColumn + " " + operator + " " + value;
    }
    
    /**
     * Trouve la table principale (généralement identite_personnelle ou celle avec le plus de colonnes utilisées)
     */
    private static String findMainTable(List<String> tables) {
        // Priorité à identite_personnelle si présente
        if (tables.contains("identite_personnelle")) {
            return "identite_personnelle";
        }
        
        // Sinon, la première table de la liste
        return tables.get(0);
    }
    
    /**
     * Obtient les colonnes disponibles pour un service avec préfixe de table
     */
    public static List<String> getAvailableColumnsWithTables(String service) {
        List<String> result = new ArrayList<>();
        List<String> tables = ServicePermissions.getTablesForService(service);
        
        for (String table : tables) {
            List<String> columns = getTableColumns(table);
            for (String column : columns) {
                if (!result.contains(column)) {
                    result.add(column);
                }
            }
        }
        
        Collections.sort(result);
        return result;
    }
    
    /**
     * Efface le cache des colonnes
     */
    public static void clearCache() {
        TABLE_COLUMNS_CACHE.clear();
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