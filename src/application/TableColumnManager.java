package application;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionnaire des colonnes disponibles selon les services
 */
public class TableColumnManager {
    private static final Logger LOGGER = Logger.getLogger(TableColumnManager.class.getName());
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/master";
    private static final String DB_USER = "marco";
    private static final String DB_PASSWORD = "29Papa278.";
    
    /**
     * Obtient toutes les colonnes disponibles pour un service donné
     */
    public static List<String> getAvailableColumnsForService(String service) {
        Set<String> allColumns = new LinkedHashSet<>();
        List<String> tables = ServicePermissions.getTablesForService(service);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (String tableName : tables) {
                List<String> tableColumns = getColumnsForTable(conn, tableName);
                allColumns.addAll(tableColumns);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des colonnes", e);
        }
        
        return new ArrayList<>(allColumns);
    }
    
    /**
     * Obtient les colonnes d'une table spécifique
     */
    public static List<String> getColumnsForTable(String tableName) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            return getColumnsForTable(conn, tableName);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des colonnes pour " + tableName, e);
            return new ArrayList<>();
        }
    }
    
    private static List<String> getColumnsForTable(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns("master", null, tableName, null)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                columns.add(columnName);
            }
        }
        
        return columns;
    }
    
    /**
     * Obtient le type d'une colonne spécifique
     */
    public static Map<String, String> getColumnTypesForService(String service) {
        Map<String, String> columnTypes = new HashMap<>();
        List<String> tables = ServicePermissions.getTablesForService(service);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (String tableName : tables) {
                DatabaseMetaData metaData = conn.getMetaData();
                try (ResultSet rs = metaData.getColumns("master", null, tableName, null)) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        String dataType = rs.getString("TYPE_NAME");
                        
                        // Mapper les types SQL vers nos types simplifiés
                        String mappedType = mapSqlTypeToSimpleType(dataType);
                        columnTypes.put(columnName, mappedType);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des types de colonnes", e);
        }
        
        return columnTypes;
    }
    
    /**
     * Obtient les valeurs distinctes pour les colonnes de type STRING
     */
    public static Map<String, List<String>> getDistinctValuesForService(String service) {
        Map<String, List<String>> distinctValues = new HashMap<>();
        List<String> tables = ServicePermissions.getTablesForService(service);
        Map<String, String> columnTypes = getColumnTypesForService(service);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Pour chaque table, récupérer les valeurs distinctes des colonnes STRING
            for (String tableName : tables) {
                List<String> tableColumns = getColumnsForTable(conn, tableName);
                
                for (String column : tableColumns) {
                    if ("STRING".equals(columnTypes.get(column)) && !distinctValues.containsKey(column)) {
                        List<String> values = getDistinctValuesForColumn(conn, tableName, column);
                        if (!values.isEmpty()) {
                            distinctValues.put(column, values);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la récupération des valeurs distinctes", e);
        }
        
        return distinctValues;
    }
    
    private static List<String> getDistinctValuesForColumn(Connection conn, String tableName, String columnName) {
        List<String> values = new ArrayList<>();
        String query = "SELECT DISTINCT " + columnName + " FROM " + tableName + 
                      " WHERE " + columnName + " IS NOT NULL AND " + columnName + " != '' " +
                      " ORDER BY " + columnName + " LIMIT 100"; // Limiter pour éviter trop de résultats
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                String value = rs.getString(1);
                if (value != null && !value.trim().isEmpty()) {
                    values.add(value);
                }
            }
        } catch (SQLException e) {
            // Log mais ne pas arrêter pour une colonne
            LOGGER.log(Level.WARNING, "Erreur pour la colonne " + columnName + " de " + tableName, e);
        }
        
        return values;
    }
    
    private static String mapSqlTypeToSimpleType(String sqlType) {
        switch (sqlType.toUpperCase()) {
            case "VARCHAR":
            case "CHAR":
            case "TEXT":
            case "ENUM":
                return "STRING";
            case "INT":
            case "TINYINT":
            case "SMALLINT":
            case "BIGINT":
                return "INTEGER";
            case "DECIMAL":
            case "FLOAT":
            case "DOUBLE":
                return "DECIMAL";
            case "DATE":
            case "DATETIME":
            case "TIMESTAMP":
                return "DATE";
            default:
                return "STRING";
        }
    }
    
    /**
     * Obtient les colonnes appropriées pour les graphiques (exclut les IDs, etc.)
     */
    public static List<String> getGraphableColumnsForService(String service) {
        List<String> allColumns = getAvailableColumnsForService(service);
        Map<String, String> columnTypes = getColumnTypesForService(service);
        
        return allColumns.stream()
                .filter(column -> isColumnGraphable(column, columnTypes.get(column)))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    private static boolean isColumnGraphable(String columnName, String columnType) {
        // Exclure les colonnes techniques
        if (columnName.toLowerCase().contains("id") && !columnName.toLowerCase().contains("identifiant")) {
            return false;
        }
        
        // Inclure principalement les colonnes STRING et quelques numériques significatives
        return "STRING".equals(columnType) || 
               (("INTEGER".equals(columnType) || "DATE".equals(columnType)) && 
                !columnName.toLowerCase().startsWith("id"));
    }
}