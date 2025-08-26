package application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Utilitaires de validation pour les fonctionnalités logistiques
 */
public class LogisticValidationUtils {
    private static final Logger LOGGER = Logger.getLogger(LogisticValidationUtils.class.getName());
    
    // Patterns de validation
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-_àáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ]+$");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-_.,!?()àáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ]+$");
    
    // Constantes de validation
    private static final int MIN_DESIGNATION_LENGTH = 3;
    private static final int MAX_DESIGNATION_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MIN_QUANTITY = 0;
    private static final int MAX_QUANTITY = 999999;
    private static final int MIN_CRITICAL_VALUE = 1;
    private static final int MAX_CRITICAL_VALUE = 999999;
    
    /**
     * Résultat de validation
     */
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        
        public ValidationResult() {
            this.valid = true;
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
        }
        
        public boolean isValid() { return valid && errors.isEmpty(); }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        public void addError(String error) {
            errors.add(error);
            valid = false;
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public String getErrorsAsString() {
            return String.join("\n", errors);
        }
        
        public String getWarningsAsString() {
            return String.join("\n", warnings);
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
    
    /**
     * Valide un objet Stock
     */
    public static ValidationResult validateStock(Stock stock) {
        ValidationResult result = new ValidationResult();
        
        // Validation de la désignation
        validateDesignation(stock.getDesignation(), result);
        
        // Validation de la quantité
        validateQuantity(stock.getQuantite(), result);
        
        // Validation de l'état
        validateState(stock.getEtat(), result);
        
        // Validation de la description
        validateDescription(stock.getDescription(), result);
        
        // Validation de la valeur critique
        validateCriticalValue(stock.getValeurCritique(), result);
        
        // Validation de cohérence quantité/valeur critique
        validateQuantityCriticalCoherence(stock.getQuantite(), stock.getValeurCritique(), result);
        
        LOGGER.fine("Validation Stock '" + stock.getDesignation() + "': " + 
                   (result.isValid() ? "VALIDE" : "INVALIDE (" + result.getErrors().size() + " erreurs)"));
        
        return result;
    }
    
    /**
     * Valide un objet Maintenance
     */
    public static ValidationResult validateMaintenance(Maintenance maintenance) {
        ValidationResult result = new ValidationResult();
        
        // Validation de la désignation
        validateDesignation(maintenance.getDesignation(), result);
        
        // Validation de la date
        validateMaintenanceDate(maintenance.getDate(), result);
        
        // Validation du type
        validateMaintenanceType(maintenance.getType(), result);
        
        // Validation de la description
        validateDescription(maintenance.getDescription(), result);
        
        // Validation de cohérence date/statut
        validateMaintenanceDateStatusCoherence(maintenance, result);
        
        LOGGER.fine("Validation Maintenance '" + maintenance.getDesignation() + "': " + 
                   (result.isValid() ? "VALIDE" : "INVALIDE (" + result.getErrors().size() + " erreurs)"));
        
        return result;
    }
    
    /**
     * Valide un mouvement de stock
     */
    public static ValidationResult validateStockMovement(StockMovement movement, Stock stock) {
        ValidationResult result = new ValidationResult();
        
        // Validation du type de mouvement
        if (movement.getType() == null || 
            (!movement.getType().equals("APPROVISIONNEMENT") && !movement.getType().equals("RETRAIT"))) {
            result.addError("Le type de mouvement doit être 'APPROVISIONNEMENT' ou 'RETRAIT'");
        }
        
        // Validation de la quantité
        if (movement.getQuantite() <= 0) {
            result.addError("La quantité du mouvement doit être supérieure à zéro");
        } else if (movement.getQuantite() > MAX_QUANTITY) {
            result.addError("La quantité du mouvement ne peut pas dépasser " + MAX_QUANTITY);
        }
        
        // Validation spécifique aux retraits
        if ("RETRAIT".equals(movement.getType())) {
            if (movement.getQuantite() > stock.getQuantite()) {
                result.addError("Impossible de retirer " + movement.getQuantite() + 
                               " unités (stock actuel: " + stock.getQuantite() + ")");
            }
            
            int quantiteApresRetrait = stock.getQuantite() - movement.getQuantite();
            if (quantiteApresRetrait < stock.getValeurCritique()) {
                result.addWarning("Ce retrait fera passer le stock sous le seuil critique (" + 
                                 stock.getValeurCritique() + " unités)");
            }
        }
        
        // Validation de la description
        validateMovementDescription(movement.getDescription(), result);
        
        // Validation de l'utilisateur
        if (movement.getUtilisateur() == null || movement.getUtilisateur().trim().isEmpty()) {
            result.addError("L'utilisateur du mouvement est obligatoire");
        }
        
        // Validation des quantités avant/après
        validateMovementQuantityConsistency(movement, stock, result);
        
        LOGGER.fine("Validation Mouvement '" + movement.getType() + "': " + 
                   (result.isValid() ? "VALIDE" : "INVALIDE (" + result.getErrors().size() + " erreurs)"));
        
        return result;
    }
    
    /**
     * Valide une désignation
     */
    private static void validateDesignation(String designation, ValidationResult result) {
        if (designation == null || designation.trim().isEmpty()) {
            result.addError("La désignation est obligatoire");
            return;
        }
        
        String trimmedDesignation = designation.trim();
        
        if (trimmedDesignation.length() < MIN_DESIGNATION_LENGTH) {
            result.addError("La désignation doit contenir au moins " + MIN_DESIGNATION_LENGTH + " caractères");
        }
        
        if (trimmedDesignation.length() > MAX_DESIGNATION_LENGTH) {
            result.addError("La désignation ne peut pas dépasser " + MAX_DESIGNATION_LENGTH + " caractères");
        }
        
        if (!ALPHANUMERIC_PATTERN.matcher(trimmedDesignation).matches()) {
            result.addError("La désignation ne peut contenir que des lettres, chiffres, espaces et tirets");
        }
        
        // Vérification de doublons potentiels (suggestions)
        if (trimmedDesignation.toLowerCase().contains("test") || 
            trimmedDesignation.toLowerCase().contains("exemple")) {
            result.addWarning("La désignation semble être un exemple de test");
        }
    }
    
    /**
     * Valide une quantité
     */
    private static void validateQuantity(int quantity, ValidationResult result) {
        if (quantity < MIN_QUANTITY) {
            result.addError("La quantité ne peut pas être négative");
        }
        
        if (quantity > MAX_QUANTITY) {
            result.addError("La quantité ne peut pas dépasser " + MAX_QUANTITY);
        }
        
        // Avertissement pour les quantités très élevées
        if (quantity > 10000) {
            result.addWarning("Quantité très élevée (" + quantity + ") - Vérifiez la saisie");
        }
    }
    
    /**
     * Valide un état
     */
    private static void validateState(String state, ValidationResult result) {
        if (state == null || state.trim().isEmpty()) {
            result.addWarning("L'état n'est pas renseigné");
            return;
        }
        
        String trimmedState = state.trim();
        
        if (trimmedState.length() > 50) {
            result.addError("L'état ne peut pas dépasser 50 caractères");
        }
        
        if (!ALPHANUMERIC_PATTERN.matcher(trimmedState).matches()) {
            result.addError("L'état ne peut contenir que des lettres, chiffres, espaces et tirets");
        }
    }
    
    /**
     * Valide une description
     */
    private static void validateDescription(String description, ValidationResult result) {
        if (description != null && !description.trim().isEmpty()) {
            String trimmedDescription = description.trim();
            
            if (trimmedDescription.length() > MAX_DESCRIPTION_LENGTH) {
                result.addError("La description ne peut pas dépasser " + MAX_DESCRIPTION_LENGTH + " caractères");
            }
            
            if (!DESCRIPTION_PATTERN.matcher(trimmedDescription).matches()) {
                result.addError("La description contient des caractères non autorisés");
            }
        }
    }
    
    /**
     * Valide une valeur critique
     */
    private static void validateCriticalValue(int criticalValue, ValidationResult result) {
        if (criticalValue < MIN_CRITICAL_VALUE) {
            result.addError("La valeur critique doit être supérieure à " + MIN_CRITICAL_VALUE);
        }
        
        if (criticalValue > MAX_CRITICAL_VALUE) {
            result.addError("La valeur critique ne peut pas dépasser " + MAX_CRITICAL_VALUE);
        }
    }
    
    /**
     * Valide la cohérence quantité/valeur critique
     */
    private static void validateQuantityCriticalCoherence(int quantity, int criticalValue, ValidationResult result) {
        if (quantity == 0 && criticalValue > 0) {
            result.addWarning("Stock vide avec une valeur critique définie");
        }
        
        if (quantity < criticalValue) {
            result.addWarning("Stock actuel (" + quantity + ") inférieur au seuil critique (" + criticalValue + ")");
        }
        
        if (criticalValue > quantity * 2) {
            result.addWarning("Seuil critique très élevé par rapport au stock actuel");
        }
    }
    
    /**
     * Valide une date de maintenance
     */
    private static void validateMaintenanceDate(String dateStr, ValidationResult result) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            result.addError("La date de maintenance est obligatoire");
            return;
        }
        
        try {
            LocalDate date = LocalDate.parse(dateStr.trim());
            LocalDate today = LocalDate.now();
            
            // Vérifier que la date n'est pas trop ancienne
            if (date.isBefore(today.minusYears(1))) {
                result.addWarning("Date de maintenance très ancienne (" + date + ")");
            }
            
            // Vérifier que la date n'est pas trop lointaine
            if (date.isAfter(today.plusYears(5))) {
                result.addWarning("Date de maintenance très lointaine (" + date + ")");
            }
            
        } catch (DateTimeParseException e) {
            result.addError("Format de date invalide. Utilisez le format YYYY-MM-DD");
        }
    }
    
    /**
     * Valide un type de maintenance
     */
    private static void validateMaintenanceType(String type, ValidationResult result) {
        if (type == null || type.trim().isEmpty()) {
            result.addError("Le type de maintenance est obligatoire");
            return;
        }
        
        String trimmedType = type.trim();
        
        // Types valides
        List<String> validTypes = List.of(
            "Préventive", "Corrective", "Prédictive", 
            "Conditionnelle", "Systématique"
        );
        
        if (!validTypes.contains(trimmedType)) {
            result.addWarning("Type de maintenance non standard: " + trimmedType);
        }
        
        if (trimmedType.length() > 50) {
            result.addError("Le type de maintenance ne peut pas dépasser 50 caractères");
        }
    }
    
    /**
     * Valide la cohérence date/statut de maintenance
     */
    private static void validateMaintenanceDateStatusCoherence(Maintenance maintenance, ValidationResult result) {
        if (maintenance.isEffectuee() && !"BLEU".equals(maintenance.getStatut())) {
            result.addWarning("Maintenance marquée comme effectuée mais statut incohérent");
        }
        
        if (!maintenance.isEffectuee() && "BLEU".equals(maintenance.getStatut())) {
            result.addWarning("Statut 'effectuée' mais maintenance non marquée comme terminée");
        }
        
        try {
            LocalDate maintenanceDate = LocalDate.parse(maintenance.getDate());
            LocalDate today = LocalDate.now();
            
            if (maintenanceDate.isBefore(today) && !maintenance.isEffectuee()) {
                result.addWarning("Maintenance en retard: prévue le " + maintenance.getDate());
            }
            
        } catch (DateTimeParseException e) {
            // Déjà signalé dans validateMaintenanceDate
        }
    }
    
    /**
     * Valide une description de mouvement
     */
    private static void validateMovementDescription(String description, ValidationResult result) {
        if (description == null || description.trim().isEmpty()) {
            result.addError("La description du mouvement est obligatoire");
            return;
        }
        
        String trimmedDescription = description.trim();
        
        if (trimmedDescription.length() < 5) {
            result.addError("La description du mouvement doit contenir au moins 5 caractères");
        }
        
        if (trimmedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            result.addError("La description du mouvement ne peut pas dépasser " + MAX_DESCRIPTION_LENGTH + " caractères");
        }
        
        if (!DESCRIPTION_PATTERN.matcher(trimmedDescription).matches()) {
            result.addError("La description du mouvement contient des caractères non autorisés");
        }
    }
    
    /**
     * Valide la cohérence des quantités dans un mouvement
     */
    private static void validateMovementQuantityConsistency(StockMovement movement, Stock stock, ValidationResult result) {
        if (movement.getQuantiteAvant() != stock.getQuantite()) {
            result.addError("La quantité avant mouvement ne correspond pas au stock actuel");
        }
        
        int expectedQuantityAfter;
        if ("APPROVISIONNEMENT".equals(movement.getType())) {
            expectedQuantityAfter = movement.getQuantiteAvant() + movement.getQuantite();
        } else {
            expectedQuantityAfter = movement.getQuantiteAvant() - movement.getQuantite();
        }
        
        if (movement.getQuantiteApres() != expectedQuantityAfter) {
            result.addError("Erreur de calcul dans les quantités du mouvement");
        }
        
        if (movement.getQuantiteApres() < 0) {
            result.addError("La quantité après mouvement ne peut pas être négative");
        }
    }
    
    /**
     * Sanitise une chaîne de caractères
     */
    public static String sanitizeString(String input) {
        if (input == null) {
            return "";
        }
        
        return input.trim()
                   .replaceAll("\\s+", " ") // Remplacer les espaces multiples par un seul
                   .replaceAll("[<>\"'&]", ""); // Retirer les caractères potentiellement dangereux
    }
    
    /**
     * Sanitise un entier (s'assure qu'il est dans les limites)
     */
    public static int sanitizeInteger(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Valide et corrige une date
     */
    public static LocalDate validateAndCorrectDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDate.now();
        }
        
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            LOGGER.warning("Format de date invalide: " + dateStr + ". Utilisation de la date actuelle.");
            return LocalDate.now();
        }
    }
    
    /**
     * Génère un résumé de validation pour l'interface utilisateur
     */
    public static String formatValidationSummary(ValidationResult result) {
        StringBuilder summary = new StringBuilder();
        
        if (result.isValid()) {
            summary.append("✅ Validation réussie");
            if (result.hasWarnings()) {
                summary.append(" (avec ").append(result.getWarnings().size()).append(" avertissement(s))");
            }
        } else {
            summary.append("❌ Validation échouée (").append(result.getErrors().size()).append(" erreur(s))");
            if (result.hasWarnings()) {
                summary.append(" et ").append(result.getWarnings().size()).append(" avertissement(s)");
            }
        }
        
        if (!result.getErrors().isEmpty()) {
            summary.append("\n\nERREURS:\n");
            for (int i = 0; i < result.getErrors().size(); i++) {
                summary.append((i + 1)).append(". ").append(result.getErrors().get(i)).append("\n");
            }
        }
        
        if (!result.getWarnings().isEmpty()) {
            summary.append("\nAVERTISSEMENTS:\n");
            for (int i = 0; i < result.getWarnings().size(); i++) {
                summary.append((i + 1)).append(". ").append(result.getWarnings().get(i)).append("\n");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Valide un lot d'objets Stock
     */
    public static List<ValidationResult> validateStockBatch(List<Stock> stocks) {
        List<ValidationResult> results = new ArrayList<>();
        List<String> designations = new ArrayList<>();
        
        for (Stock stock : stocks) {
            ValidationResult result = validateStock(stock);
            
            // Vérification des doublons de désignation
            if (designations.contains(stock.getDesignation().toLowerCase().trim())) {
                result.addError("Désignation en doublon: " + stock.getDesignation());
            } else {
                designations.add(stock.getDesignation().toLowerCase().trim());
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * Valide un lot d'objets Maintenance
     */
    public static List<ValidationResult> validateMaintenanceBatch(List<Maintenance> maintenances) {
        List<ValidationResult> results = new ArrayList<>();
        
        for (Maintenance maintenance : maintenances) {
            ValidationResult result = validateMaintenance(maintenance);
            results.add(result);
        }
        
        return results;
    }
}