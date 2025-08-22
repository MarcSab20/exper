package application;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;
import javafx.scene.control.Button;
import java.io.IOException;

public class MainOpsController {
    @FXML
    private StackPane contentArea;
    
    private Node currentView;
    private Button currentActiveButton;

    @FXML
    public void initialize() {
        // Charger la vue Accueil par défaut
        showAccueil();
    }

    @FXML
    private void showAccueil() {
        loadView("AccueilOps.fxml", "Accueil");
    }

    @FXML
    private void showDashboard() {
        loadView("dashboard.fxml", "Dashboard");
    }

    @FXML
    private void showRequetes() {
        loadView("requetes.fxml", "Requêtes");
    }

    @FXML
    private void showMisesAJour() {
        loadView("mises-a-jour.fxml", "Mises à jour");
    }

    @FXML
    private void showGraphes() {
        loadView("graphes.fxml", "Requếtes Spéciales");
    }

    @FXML
    private void showHistorique() {
        loadView("historique.fxml", "Historique");
    }

    @FXML
    private void showParametres() {
        loadView("parametres.fxml", "Paramètres");
    }

    private void loadView(String fxmlFile, String title) {
        try {
            // Charger la nouvelle vue
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/application/" + fxmlFile));
            Node view = loader.load();
            
            // Mettre à jour la vue courante
            if (currentView != null) {
                contentArea.getChildren().remove(currentView);
            }
            contentArea.getChildren().add(view);
            currentView = view;

            // Mettre à jour le bouton actif seulement si la scène est déjà prête
            if (contentArea.getScene() != null) {
                updateActiveButton(title);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateActiveButton(String title) {
        // Retirer la classe active du bouton précédent
        if (currentActiveButton != null) {
            currentActiveButton.getStyleClass().remove("active");
        }
        
        // Assurez-vous que la scène est initialisée avant de chercher les boutons
        if (contentArea.getScene() != null) {
            for (Node node : contentArea.getScene().getRoot().lookupAll(".sidebar-button")) {
                if (node instanceof Button && ((Button) node).getText().equals(title)) {
                    currentActiveButton = (Button) node;
                    currentActiveButton.getStyleClass().add("active");
                    break;
                }
            }
        }
    }
}
