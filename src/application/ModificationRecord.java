package application;

import java.util.Date;
import java.time.LocalDateTime;

public class ModificationRecord {
    private final int id;
    private final Date date;
    private final String table;
    private final String type;
    private final String user;
    private final String details;

    public ModificationRecord(int id, Date date, String table, String type, String user, String details) {
        this.id = id;
        this.date = date;
        this.table = table;
        this.type = type;
        this.user = user;
        this.details = details;
    }
    
    // Constructeur sans ID pour les nouvelles entrées
    public ModificationRecord(Date date, String table, String type, String user, String details) {
        this(-1, date, table, type, user, details);
    }

    public int getId() { return id; }
    public Date getDate() { return date; }
    public String getTable() { return table; }
    public String getType() { return type; }
    public String getUser() { return user; }
    public String getDetails() { return details; }
}


