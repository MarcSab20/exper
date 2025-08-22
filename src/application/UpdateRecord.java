package application;

import java.sql.Timestamp;
import java.util.Date;

public class UpdateRecord {
    private final int id;
    private final String tableName;
    private final Timestamp updateDate;
    private final String status;
    private final String description;
    private final int recordsUpdated;
    private final int recordsInserted;
    
    public UpdateRecord(int id, String tableName, Timestamp updateDate, String status, 
                        String description, int recordsUpdated, int recordsInserted) {
        this.id = id;
        this.tableName = tableName;
        this.updateDate = updateDate;
        this.status = status;
        this.description = description;
        this.recordsUpdated = recordsUpdated;
        this.recordsInserted = recordsInserted;
    }
    
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
    
    public int getTotalRecords() {
        return recordsUpdated + recordsInserted;
    }
    
    public boolean hasErrors() {
        return "Échec".equals(status) || "Partiel".equals(status);
    }
}