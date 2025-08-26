package application;

import java.time.LocalDateTime;

public class Stock {
	    private int id;
	    private String designation;
	    private int quantite;
	    private String etat;
	    private String description;
	    private int valeurCritique;
	    private String statut;
	    private LocalDateTime dateCreation;
	    private int quantiteInitiale;
	    
	    public Stock() {
	        this(0, "", 0, "", "", 0, "VERT", LocalDateTime.now() , 0);
	    }

	    public Stock(int id) {
	    	this.id = id;
	    }

	    public Stock(int id, String designation, int quantite, String etat, String description, int valeurCritique, String statut, LocalDateTime dateCreation, int quantiteInitiale) {
	        this.id = id;
	        this.designation = designation;
	        this.quantite = quantite;
	        this.etat = etat;
	        this.description = description;
	        this.valeurCritique = valeurCritique;
	        this.statut = statut;
	        this.dateCreation = dateCreation;
	        this.quantiteInitiale = quantiteInitiale;
	    }

	    public int getId() { return id; }
	    public void setId(int id) { this.id = id; }
	    
	    public String getDesignation() { return designation; }
	    public void setDesignation(String designation) { this.designation = designation; }
	    
	    public int getQuantite() { return quantite; }
	    public void setQuantite(int quantite) { this.quantite = quantite; }
	    
	    public String getEtat() { return etat; }
	    public void setEtat(String etat) { this.etat = etat; }
	    
	    public String getDescription() { return description; }
	    public void setDescription(String description) { this.description = description; }
	    
	    public int getValeurCritique() { return valeurCritique; }
	    public void setValeurCritique(int valeurCritique) { this.valeurCritique = valeurCritique; }
	    
	    public String getStatut() { return statut; }
	    public void setStatut(String statut) { this.statut = statut; }

		public LocalDateTime getDateCreation() {
			return dateCreation;
		}

		public void setDateCreation(LocalDateTime dateCreation) {
			this.dateCreation = dateCreation;
		}

		public int getQuantiteInitiale() {
			return quantiteInitiale;
		}

		public void setQuantiteInitiale(int quantiteInitiale) {
			this.quantiteInitiale = quantiteInitiale;
		}
	    
}


