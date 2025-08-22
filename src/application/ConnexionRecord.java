package application;

import java.util.Date;
import java.time.LocalDateTime;

public class ConnexionRecord {
    private final int id;
    private final Date date;
    private final String user;
    private final String action;
    private final String status;

    public ConnexionRecord(int id, Date date, String user, String action, String status) {
        this.id = id;
        this.date = date;
        this.user = user;
        this.action = action;
        this.status = status;
    }
    
    // Constructeur sans ID pour les nouvelles entrées
    public ConnexionRecord(Date date, String user, String action, String status) {
        this(-1, date, user, action, status);
    }

    public int getId() { return id; }
    public Date getDate() { return date; }
    public String getUser() { return user; }
    public String getAction() { return action; }
    public String getStatus() { return status; }
}