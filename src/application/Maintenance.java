package application;

public class Maintenance {
	    private int id;
	    private String date;
	    private String designation;
	    private String type;
	    private String description;
	    private boolean effectuee;
	    private String statut;
	    
	    public Maintenance() {
	        this(0, "", "", "", "", false, "VERT");
	    }

	    public Maintenance(int id) {
	    	this.id = id;
	    }
	    
	    public Maintenance(int id, String date, String designation, String type, String description, boolean effectuee, String statut) {
	        this.id = id;
	        this.date = date;
	        this.designation = designation;
	        this.type = type;
	        this.description = description;
	        this.effectuee = effectuee;
	        this.statut = statut;
	    }

	    public int getId() { return id; }
	    public void setId(int id) { this.id = id; }
	    
	    public String getDate() { return date; }
	    public void setDate(String date) { this.date = date; }
	    
	    public String getDesignation() { return designation; }
	    public void setDesignation(String designation) { this.designation = designation; }
	    
	    public String getType() { return type; }
	    public void setType(String type) { this.type = type; }
	    
	    public String getDescription() { return description; }
	    public void setDescription(String description) { this.description = description; }
	    
	    public boolean isEffectuee() { return effectuee; }
	    public void setEffectuee(boolean effectuee) { this.effectuee = effectuee; }
	    
	    public String getStatut() { return statut; }
	    public void setStatut(String statut) { this.statut = statut; }
}
