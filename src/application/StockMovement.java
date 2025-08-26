package application;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Classe pour représenter les mouvements de stock (approvisionnement/retrait)
 */
public class StockMovement {
    private int id;
    private int stockId;
    private String type; // "APPROVISIONNEMENT" ou "RETRAIT"
    private int quantite;
    private String description;
    private LocalDateTime dateMovement;
    private String utilisateur;
    private int quantiteAvant;
    private int quantiteApres;
    
    // Format de date standard
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public StockMovement() {
        this.dateMovement = LocalDateTime.now();
        this.utilisateur = UserSession.getCurrentUser();
    }
    
    public StockMovement(int stockId, String type, int quantite, String description, 
                        int quantiteAvant, int quantiteApres) {
        this();
        this.stockId = stockId;
        this.type = type;
        this.quantite = quantite;
        this.description = description;
        this.quantiteAvant = quantiteAvant;
        this.quantiteApres = quantiteApres;
    }
    
    public StockMovement(int id, int stockId, String type, int quantite, String description,
                        LocalDateTime dateMovement, String utilisateur, int quantiteAvant, int quantiteApres) {
        this.id = id;
        this.stockId = stockId;
        this.type = type;
        this.quantite = quantite;
        this.description = description;
        this.dateMovement = dateMovement;
        this.utilisateur = utilisateur;
        this.quantiteAvant = quantiteAvant;
        this.quantiteApres = quantiteApres;
    }
    
    // Getters et setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getStockId() { return stockId; }
    public void setStockId(int stockId) { this.stockId = stockId; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public int getQuantite() { return quantite; }
    public void setQuantite(int quantite) { this.quantite = quantite; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public LocalDateTime getDateMovement() { return dateMovement; }
    public void setDateMovement(LocalDateTime dateMovement) { this.dateMovement = dateMovement; }
    
    public String getUtilisateur() { return utilisateur; }
    public void setUtilisateur(String utilisateur) { this.utilisateur = utilisateur; }
    
    public int getQuantiteAvant() { return quantiteAvant; }
    public void setQuantiteAvant(int quantiteAvant) { this.quantiteAvant = quantiteAvant; }
    
    public int getQuantiteApres() { return quantiteApres; }
    public void setQuantiteApres(int quantiteApres) { this.quantiteApres = quantiteApres; }
    
    // Méthodes utilitaires
    public String getFormattedDate() {
        return dateMovement.format(DATE_FORMATTER);
    }
    
    public String getTypeIcon() {
        return "APPROVISIONNEMENT".equals(type) ? "⬆️" : "⬇️";
    }
    
    public String getQuantiteWithSign() {
        String sign = "APPROVISIONNEMENT".equals(type) ? "+" : "-";
        return sign + quantite;
    }
    
    @Override
    public String toString() {
        return String.format("%s %s: %s (%s)", 
                           getFormattedDate(), 
                           getTypeIcon(), 
                           getQuantiteWithSign(),
                           description);
    }
}