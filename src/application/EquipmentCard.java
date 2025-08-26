package application;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Classe pour représenter la fiche complète d'un équipement
 */
public class EquipmentCard {
    private Stock stock;
    private LocalDateTime dateCreation;
    private int quantiteInitiale;
    private List<StockMovement> mouvements;
    private int totalApprovisionements;
    private int totalRetraits;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    public EquipmentCard(Stock stock, LocalDateTime dateCreation, int quantiteInitiale, List<StockMovement> mouvements) {
        this.stock = stock;
        this.dateCreation = dateCreation;
        this.quantiteInitiale = quantiteInitiale;
        this.mouvements = mouvements;
        calculateTotals();
    }
    
    private void calculateTotals() {
        totalApprovisionements = 0;
        totalRetraits = 0;
        
        for (StockMovement mouvement : mouvements) {
            if ("APPROVISIONNEMENT".equals(mouvement.getType())) {
                totalApprovisionements += mouvement.getQuantite();
            } else {
                totalRetraits += mouvement.getQuantite();
            }
        }
    }
    
    // Getters
    public Stock getStock() { return stock; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public int getQuantiteInitiale() { return quantiteInitiale; }
    public List<StockMovement> getMouvements() { return mouvements; }
    public int getTotalApprovisionements() { return totalApprovisionements; }
    public int getTotalRetraits() { return totalRetraits; }
    
    public String getFormattedDateCreation() {
        return dateCreation.format(DATE_FORMATTER);
    }
    
    public int getQuantiteCalculee() {
        return quantiteInitiale + totalApprovisionements - totalRetraits;
    }
    
    public int getNombreMouvements() {
        return mouvements.size();
    }
    
    public boolean hasDiscrepancy() {
        return getQuantiteCalculee() != stock.getQuantite();
    }
    
    /**
     * Génère un résumé textuel de l'équipement
     */
    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== FICHE ÉQUIPEMENT ===\n");
        summary.append("Désignation: ").append(stock.getDesignation()).append("\n");
        summary.append("Date de création: ").append(getFormattedDateCreation()).append("\n");
        summary.append("Quantité initiale: ").append(quantiteInitiale).append("\n");
        summary.append("État: ").append(stock.getEtat()).append("\n");
        summary.append("Seuil critique: ").append(stock.getValeurCritique()).append("\n");
        summary.append("Statut actuel: ").append(getStatusText()).append("\n\n");
        
        summary.append("=== MOUVEMENTS (").append(mouvements.size()).append(" au total) ===\n");
        summary.append("Total approvisionnements: +").append(totalApprovisionements).append("\n");
        summary.append("Total retraits: -").append(totalRetraits).append("\n");
        summary.append("Quantité calculée: ").append(getQuantiteCalculee()).append("\n");
        summary.append("Quantité actuelle: ").append(stock.getQuantite()).append("\n");
        
        if (hasDiscrepancy()) {
            summary.append("⚠️ ATTENTION: Discordance détectée!\n");
        }
        
        summary.append("\n=== HISTORIQUE DES MOUVEMENTS ===\n");
        for (StockMovement mouvement : mouvements) {
            summary.append(mouvement.getFormattedDate()).append(" - ");
            summary.append(mouvement.getTypeIcon()).append(" ");
            summary.append(mouvement.getType()).append(" ");
            summary.append(mouvement.getQuantiteWithSign()).append(" ");
            summary.append("(").append(mouvement.getQuantiteAvant()).append(" → ").append(mouvement.getQuantiteApres()).append(") ");
            summary.append("- ").append(mouvement.getDescription()).append(" ");
            summary.append("(").append(mouvement.getUtilisateur()).append(")\n");
        }
        
        return summary.toString();
    }
    
    private String getStatusText() {
        switch (stock.getStatut()) {
            case "VERT": return "Normal";
            case "ORANGE": return "Attention";
            case "ROUGE": return "Faible";
            case "VIOLET": return "Critique";
            default: return "Inconnu";
        }
    }
}