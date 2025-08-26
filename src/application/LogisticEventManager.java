package application;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Pos;
import javafx.scene.Scene;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestionnaire d'événements et de notifications pour le système logistique
 */
public class LogisticEventManager {
    private static final Logger LOGGER = Logger.getLogger(LogisticEventManager.class.getName());
    private static LogisticEventManager instance;
    
    // File d'événements en attente
    private final Queue<LogisticEvent> eventQueue = new ConcurrentLinkedQueue<>();
    
    // Listeners pour les différents types d'événements
    private final Map<EventType, List<EventListener>> listeners = new EnumMap<>(EventType.class);
    
    // Système de notification en temps réel
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private boolean notificationsEnabled = true;
    
    // Configuration des seuils d'alerte
    private AlertConfiguration alertConfig;
    
    /**
     * Types d'événements logistiques
     */
    public enum EventType {
        STOCK_CRITICAL,
        STOCK_LOW,
        STOCK_MOVEMENT,
        STOCK_ADDED,
        STOCK_REMOVED,
        MAINTENANCE_DUE,
        MAINTENANCE_OVERDUE,
        MAINTENANCE_COMPLETED,
        MAINTENANCE_ADDED,
        MAINTENANCE_REMOVED,
        SYSTEM_ERROR,
        USER_ACTION,
        DATA_EXPORT,
        DATA_IMPORT,
        VALIDATION_ERROR,
        ALERT_RESOLVED
    }
    
    /**
     * Priorités des événements
     */
    public enum Priority {
        LOW(1),
        MEDIUM(2),
        HIGH(3),
        CRITICAL(4),
        EMERGENCY(5);
        
        private final int level;
        
        Priority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * Interface pour les listeners d'événements
     */
    public interface EventListener {
        void handleEvent(LogisticEvent event);
    }
    
    /**
     * Classe représentant un événement logistique
     */
    public static class LogisticEvent {
        private final EventType type;
        private final Priority priority;
        private final String title;
        private final String message;
        private final String source;
        private final LocalDateTime timestamp;
        private final Map<String, Object> data;
        private boolean handled;
        
        public LogisticEvent(EventType type, Priority priority, String title, String message, String source) {
            this.type = type;
            this.priority = priority;
            this.title = title;
            this.message = message;
            this.source = source;
            this.timestamp = LocalDateTime.now();
            this.data = new HashMap<>();
            this.handled = false;
        }
        
        // Getters
        public EventType getType() { return type; }
        public Priority getPriority() { return priority; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getSource() { return source; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getData() { return data; }
        public boolean isHandled() { return handled; }
        
        public void setHandled(boolean handled) { this.handled = handled; }
        
        public void addData(String key, Object value) {
            data.put(key, value);
        }
        
        public String getFormattedTimestamp() {
            return timestamp.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s: %s (%s)", 
                                priority, title, message, getFormattedTimestamp());
        }
    }
    
    /**
     * Configuration des alertes
     */
    public static class AlertConfiguration {
        private boolean showCriticalAlerts = true;
        private boolean showStockAlerts = true;
        private boolean showMaintenanceAlerts = true;
        private boolean showSystemAlerts = true;
        private boolean soundEnabled = false;
        private int alertDisplayDuration = 5000; // millisecondes
        private int maxConcurrentAlerts = 3;
        
        // Getters et setters
        public boolean isShowCriticalAlerts() { return showCriticalAlerts; }
        public void setShowCriticalAlerts(boolean showCriticalAlerts) { this.showCriticalAlerts = showCriticalAlerts; }
        
        public boolean isShowStockAlerts() { return showStockAlerts; }
        public void setShowStockAlerts(boolean showStockAlerts) { this.showStockAlerts = showStockAlerts; }
        
        public boolean isShowMaintenanceAlerts() { return showMaintenanceAlerts; }
        public void setShowMaintenanceAlerts(boolean showMaintenanceAlerts) { this.showMaintenanceAlerts = showMaintenanceAlerts; }
        
        public boolean isShowSystemAlerts() { return showSystemAlerts; }
        public void setShowSystemAlerts(boolean showSystemAlerts) { this.showSystemAlerts = showSystemAlerts; }
        
        public boolean isSoundEnabled() { return soundEnabled; }
        public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }
        
        public int getAlertDisplayDuration() { return alertDisplayDuration; }
        public void setAlertDisplayDuration(int alertDisplayDuration) { this.alertDisplayDuration = alertDisplayDuration; }
        
        public int getMaxConcurrentAlerts() { return maxConcurrentAlerts; }
        public void setMaxConcurrentAlerts(int maxConcurrentAlerts) { this.maxConcurrentAlerts = maxConcurrentAlerts; }
    }
    
    /**
     * Constructeur privé pour le singleton
     */
    private LogisticEventManager() {
        // Initialiser la configuration par défaut
        this.alertConfig = new AlertConfiguration();
        
        // Démarrer le processeur d'événements
        startEventProcessor();
        
        LOGGER.info("Gestionnaire d'événements logistiques initialisé");
    }
    
    /**
     * Obtenir l'instance singleton
     */
    public static synchronized LogisticEventManager getInstance() {
        if (instance == null) {
            instance = new LogisticEventManager();
        }
        return instance;
    }
    
    /**
     * Publier un événement
     */
    public void publishEvent(LogisticEvent event) {
        if (event == null) return;
        
        // Ajouter l'événement à la file
        eventQueue.offer(event);
        
        // Log l'événement
        LOGGER.info("Événement publié: " + event);
        
        // Traitement immédiat pour les événements critiques
        if (event.getPriority() == Priority.EMERGENCY || event.getPriority() == Priority.CRITICAL) {
            processEventImmediately(event);
        }
    }
    
    /**
     * Méthodes de convenance pour publier différents types d'événements
     */
    public void publishStockCriticalEvent(Stock stock) {
        LogisticEvent event = new LogisticEvent(
            EventType.STOCK_CRITICAL,
            Priority.CRITICAL,
            "Stock Critique",
            String.format("Le stock '%s' est critique (quantité: %d, seuil: %d)",
                         stock.getDesignation(), stock.getQuantite(), stock.getValeurCritique()),
            "StockManager"
        );
        event.addData("stock", stock);
        publishEvent(event);
    }
    
    public void publishStockLowEvent(Stock stock) {
        LogisticEvent event = new LogisticEvent(
            EventType.STOCK_LOW,
            Priority.HIGH,
            "Stock Faible",
            String.format("Le stock '%s' est faible (quantité: %d, seuil: %d)",
                         stock.getDesignation(), stock.getQuantite(), stock.getValeurCritique()),
            "StockManager"
        );
        event.addData("stock", stock);
        publishEvent(event);
    }
    
    public void publishStockMovementEvent(Stock stock, String movementType, int quantity, String description) {
        Priority priority = "RETRAIT".equals(movementType) ? Priority.MEDIUM : Priority.LOW;
        
        LogisticEvent event = new LogisticEvent(
            EventType.STOCK_MOVEMENT,
            priority,
            "Mouvement de Stock",
            String.format("%s de %d unités pour '%s': %s",
                         movementType, quantity, stock.getDesignation(), description),
            "MovementManager"
        );
        event.addData("stock", stock);
        event.addData("movementType", movementType);
        event.addData("quantity", quantity);
        event.addData("description", description);
        publishEvent(event);
    }
    
    public void publishMaintenanceOverdueEvent(Maintenance maintenance) {
        LogisticEvent event = new LogisticEvent(
            EventType.MAINTENANCE_OVERDUE,
            Priority.CRITICAL,
            "Maintenance en Retard",
            String.format("La maintenance '%s' est en retard (date prévue: %s)",
                         maintenance.getDesignation(), maintenance.getDate()),
            "MaintenanceManager"
        );
        event.addData("maintenance", maintenance);
        publishEvent(event);
    }
    
    public void publishMaintenanceDueEvent(Maintenance maintenance) {
        LogisticEvent event = new LogisticEvent(
            EventType.MAINTENANCE_DUE,
            Priority.HIGH,
            "Maintenance à Effectuer",
            String.format("La maintenance '%s' doit être effectuée (date prévue: %s)",
                         maintenance.getDesignation(), maintenance.getDate()),
            "MaintenanceManager"
        );
        event.addData("maintenance", maintenance);
        publishEvent(event);
    }
    
    public void publishUserActionEvent(String action, String details) {
        LogisticEvent event = new LogisticEvent(
            EventType.USER_ACTION,
            Priority.LOW,
            "Action Utilisateur",
            String.format("Utilisateur %s: %s - %s",
                         UserSession.getCurrentUser(), action, details),
            "UserInterface"
        );
        event.addData("user", UserSession.getCurrentUser());
        event.addData("action", action);
        event.addData("details", details);
        publishEvent(event);
    }
    
    public void publishSystemErrorEvent(String error, Throwable exception) {
        LogisticEvent event = new LogisticEvent(
            EventType.SYSTEM_ERROR,
            Priority.HIGH,
            "Erreur Système",
            error,
            "System"
        );
        if (exception != null) {
            event.addData("exception", exception);
            event.addData("stackTrace", Arrays.toString(exception.getStackTrace()));
        }
        publishEvent(event);
    }
    
    public void publishValidationErrorEvent(String validationError, Object validatedObject) {
        LogisticEvent event = new LogisticEvent(
            EventType.VALIDATION_ERROR,
            Priority.MEDIUM,
            "Erreur de Validation",
            validationError,
            "ValidationManager"
        );
        event.addData("validatedObject", validatedObject);
        publishEvent(event);
    }
    
    /**
     * Ajouter un listener pour un type d'événement
     */
    public void addEventListener(EventType eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
        LOGGER.fine("Listener ajouté pour " + eventType);
    }
    
    /**
     * Retirer un listener
     */
    public void removeEventListener(EventType eventType, EventListener listener) {
        List<EventListener> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }
    
    /**
     * Démarrer le processeur d'événements
     */
    private void startEventProcessor() {
        // Processeur d'événements principal
        scheduler.scheduleWithFixedDelay(this::processEvents, 1, 2, TimeUnit.SECONDS);
        
        // Vérificateur d'alertes périodique
        scheduler.scheduleWithFixedDelay(this::checkPeriodicAlerts, 30, 300, TimeUnit.SECONDS); // Toutes les 5 minutes
        
        LOGGER.info("Processeur d'événements démarré");
    }
    
    /**
     * Traiter les événements en file
     */
    private void processEvents() {
        LogisticEvent event;
        int processedCount = 0;
        
        while ((event = eventQueue.poll()) != null && processedCount < 10) { // Limite de traitement par cycle
            processEvent(event);
            processedCount++;
        }
    }
    
    /**
     * Traiter un événement immédiatement
     */
    private void processEventImmediately(LogisticEvent event) {
        Platform.runLater(() -> processEvent(event));
    }
    
    /**
     * Traiter un événement spécifique
     */
    private void processEvent(LogisticEvent event) {
        try {
            // Notifier les listeners
            List<EventListener> eventListeners = listeners.get(event.getType());
            if (eventListeners != null) {
                for (EventListener listener : eventListeners) {
                    try {
                        listener.handleEvent(event);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Erreur dans le listener d'événements", e);
                    }
                }
            }
            
            // Afficher les notifications si activées
            if (notificationsEnabled && shouldShowNotification(event)) {
                showNotification(event);
            }
            
            // Enregistrer l'événement dans la base de données si nécessaire
            if (shouldLogToDatabase(event)) {
                logEventToDatabase(event);
            }
            
            event.setHandled(true);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du traitement de l'événement: " + event, e);
        }
    }
    
    /**
     * Vérifier si une notification doit être affichée
     */
    private boolean shouldShowNotification(LogisticEvent event) {
        switch (event.getType()) {
            case STOCK_CRITICAL:
            case STOCK_LOW:
                return alertConfig.isShowStockAlerts();
            case MAINTENANCE_DUE:
            case MAINTENANCE_OVERDUE:
                return alertConfig.isShowMaintenanceAlerts();
            case SYSTEM_ERROR:
                return alertConfig.isShowSystemAlerts();
            default:
                return event.getPriority().getLevel() >= Priority.HIGH.getLevel() && alertConfig.isShowCriticalAlerts();
        }
    }
    
    /**
     * Afficher une notification
     */
    private void showNotification(LogisticEvent event) {
        Platform.runLater(() -> {
            try {
                // Créer une fenêtre de notification non-bloquante
                Stage notificationStage = new Stage();
                notificationStage.initStyle(StageStyle.UTILITY);
                notificationStage.setAlwaysOnTop(true);
                notificationStage.setResizable(false);
                
                // Contenu de la notification
                VBox content = new VBox(10);
                content.setAlignment(Pos.CENTER);
                content.setStyle(getNotificationStyle(event.getPriority()));
                content.setPrefWidth(300);
                content.setPrefHeight(100);
                
                Label titleLabel = new Label(event.getTitle());
                titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                
                Label messageLabel = new Label(event.getMessage());
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(280);
                
                Label timeLabel = new Label(event.getFormattedTimestamp());
                timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
                
                content.getChildren().addAll(titleLabel, messageLabel, timeLabel);
                
                Scene scene = new Scene(content);
                notificationStage.setScene(scene);
                notificationStage.setTitle("Notification Logistique");
                
                // Positionner la notification en haut à droite
                notificationStage.setX(javafx.stage.Screen.getPrimary().getVisualBounds().getMaxX() - 320);
                notificationStage.setY(50);
                
                notificationStage.show();
                
                // Fermer automatiquement après un délai
                scheduler.schedule(() -> Platform.runLater(notificationStage::close), 
                                 alertConfig.getAlertDisplayDuration(), TimeUnit.MILLISECONDS);
                
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erreur lors de l'affichage de la notification", e);
            }
        });
    }
    
    /**
     * Obtenir le style CSS pour les notifications selon la priorité
     */
    private String getNotificationStyle(Priority priority) {
        String baseStyle = "-fx-padding: 15px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-border-width: 2px;";
        
        switch (priority) {
            case EMERGENCY:
                return baseStyle + " -fx-background-color: #ffebee; -fx-border-color: #f44336; -fx-text-fill: #c62828;";
            case CRITICAL:
                return baseStyle + " -fx-background-color: #fff3e0; -fx-border-color: #ff9800; -fx-text-fill: #e65100;";
            case HIGH:
                return baseStyle + " -fx-background-color: #f3e5f5; -fx-border-color: #9c27b0; -fx-text-fill: #6a1b9a;";
            case MEDIUM:
                return baseStyle + " -fx-background-color: #e8f5e8; -fx-border-color: #4caf50; -fx-text-fill: #2e7d32;";
            case LOW:
            default:
                return baseStyle + " -fx-background-color: #e3f2fd; -fx-border-color: #2196f3; -fx-text-fill: #1565c0;";
        }
    }
    
    /**
     * Vérifier si l'événement doit être enregistré en base
     */
    private boolean shouldLogToDatabase(LogisticEvent event) {
        // Enregistrer les événements importants
        return event.getPriority().getLevel() >= Priority.MEDIUM.getLevel() ||
               event.getType() == EventType.STOCK_MOVEMENT ||
               event.getType() == EventType.USER_ACTION;
    }
    
    /**
     * Enregistrer l'événement dans la base de données
     */
    private void logEventToDatabase(LogisticEvent event) {
        try {
            String details = String.format("Événement: %s | Priorité: %s | Source: %s | Message: %s",
                                          event.getType(), event.getPriority(), event.getSource(), event.getMessage());
            
            // Utiliser le système d'historique existant
            switch (event.getType()) {
                case STOCK_MOVEMENT:
                case STOCK_ADDED:
                case STOCK_REMOVED:
                    HistoryManager.logUpdate("Stocks", details);
                    break;
                case MAINTENANCE_COMPLETED:
                case MAINTENANCE_ADDED:
                case MAINTENANCE_REMOVED:
                    HistoryManager.logUpdate("Maintenances", details);
                    break;
                default:
                    HistoryManager.logUpdate("Système", details);
                    break;
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors de l'enregistrement de l'événement en base", e);
        }
    }
    
    /**
     * Vérifications périodiques d'alertes
     */
    private void checkPeriodicAlerts() {
        try {
            // Cette méthode peut être appelée périodiquement pour vérifier
            // les conditions d'alerte même quand il n'y a pas d'activité utilisateur
            LOGGER.fine("Vérification périodique des alertes effectuée");
            
            // Ici, on pourrait ajouter des vérifications automatiques comme :
            // - Vérifier les maintenances à venir
            // - Vérifier les stocks qui approchent du seuil critique
            // - Nettoyer les anciens événements
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erreur lors de la vérification périodique", e);
        }
    }
    
    /**
     * Obtenir la configuration des alertes
     */
    public AlertConfiguration getAlertConfiguration() {
        return alertConfig;
    }
    
    /**
     * Définir la configuration des alertes
     */
    public void setAlertConfiguration(AlertConfiguration config) {
        this.alertConfig = config;
    }
    
    /**
     * Activer/désactiver les notifications
     */
    public void setNotificationsEnabled(boolean enabled) {
        this.notificationsEnabled = enabled;
        LOGGER.info("Notifications " + (enabled ? "activées" : "désactivées"));
    }
    
    /**
     * Obtenir les statistiques des événements
     */
    public Map<EventType, Integer> getEventStatistics() {
        // Cette méthode pourrait retourner des statistiques sur les événements traités
        // Pour l'instant, on retourne une map vide
        return new HashMap<>();
    }
    
    /**
     * Arrêter le gestionnaire d'événements
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Gestionnaire d'événements arrêté");
    }
    
    /**
     * Méthode de test pour déclencher des événements de démonstration
     */
    public void triggerTestEvents() {
        publishUserActionEvent("Test", "Événements de démonstration déclenchés");
        
        // Créer des événements de test avec différentes priorités
        publishEvent(new LogisticEvent(EventType.SYSTEM_ERROR, Priority.LOW, 
                                      "Test", "Événement de test - Priorité Basse", "TestSystem"));
        
        publishEvent(new LogisticEvent(EventType.USER_ACTION, Priority.MEDIUM, 
                                      "Test", "Événement de test - Priorité Moyenne", "TestSystem"));
        
        publishEvent(new LogisticEvent(EventType.MAINTENANCE_DUE, Priority.HIGH, 
                                      "Test", "Événement de test - Priorité Haute", "TestSystem"));
        
        publishEvent(new LogisticEvent(EventType.STOCK_CRITICAL, Priority.CRITICAL, 
                                      "Test", "Événement de test - Priorité Critique", "TestSystem"));
    }
}