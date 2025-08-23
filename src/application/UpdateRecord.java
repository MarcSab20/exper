package application;

import java.sql.Timestamp;
import java.util.Date;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.IntegerProperty;

public class UpdateRecord {
    private final int id;
    private final String tableName;
    private final Timestamp updateDate;
    private final String status;
    private final String description;
    private final int recordsUpdated;
    private final int recordsInserted;
    
    // Propriétés JavaFX pour la compatibilité avec TableView
    private final StringProperty tableNameProperty;
    private final StringProperty statusProperty;
    private final StringProperty descriptionProperty;
    private final IntegerProperty recordsInsertedProperty;
    private final IntegerProperty recordsUpdatedProperty;
    
    public UpdateRecord(int id, String tableName, Timestamp updateDate, String status, 
                        String description, int recordsUpdated, int recordsInserted) {
        this.id = id;
        this.tableName = tableName;
        this.updateDate = updateDate;
        this.status = status;
        this.description = description;
        this.recordsUpdated = recordsUpdated;
        this.recordsInserted = recordsInserted;
        
        // Initialiser les propriétés JavaFX
        this.tableNameProperty = new SimpleStringProperty(tableName);
        this.statusProperty = new SimpleStringProperty(status);
        this.descriptionProperty = new SimpleStringProperty(description);
        this.recordsInsertedProperty = new SimpleIntegerProperty(recordsInserted);
        this.recordsUpdatedProperty = new SimpleIntegerProperty(recordsUpdated);
    }
    
    // Getters classiques
    public int getId() {
        return id;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public Date getUpdateDate() {
        return updateDate;
    }
    
    public String getStatus() {
        return status;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getRecordsUpdated() {
        return recordsUpdated;
    }
    
    public int getRecordsInserted() {
        return recordsInserted;
    }
    
    // Propriétés JavaFX pour TableView
    public StringProperty tableNameProperty() { 
        return tableNameProperty; 
    }
    
    public StringProperty statusProperty() { 
        return statusProperty; 
    }
    
    public StringProperty descriptionProperty() { 
        return descriptionProperty; 
    }
    
    public IntegerProperty recordsInsertedProperty() { 
        return recordsInsertedProperty; 
    }
    
    public IntegerProperty recordsUpdatedProperty() { 
        return recordsUpdatedProperty; 
    }
    
    public int getTotalRecords() {
        return recordsUpdated + recordsInserted;
    }
    
    public boolean hasErrors() {
        return "Échec".equals(status) || "Partiel".equals(status);
    }
}