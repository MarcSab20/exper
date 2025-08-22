package application;

import javafx.fxml.Initializable;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class loginController implements Initializable {

    @FXML
    private TextField idField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ComboBox<String> serviceCombo;

    @FXML
    private Button submitBTN;
    
    private Connection connect;
    private PreparedStatement prepare;
    private ResultSet result;
    
    public void loginAccount() {
        String sql = "SELECT identifiant, password, service FROM users WHERE identifiant = ? AND password = ? AND service = ?";
        connect = database.connect();
        
        try {
            Alert alert;
            if (idField.getText().isEmpty() || passwordField.getText().isEmpty() || serviceCombo.getValue() == null) {
                alert = new Alert(AlertType.ERROR);
                alert.setTitle("Error Message");
                alert.setHeaderText(null);
                alert.setContentText("S'il vous plaît remplissez tous les champs");
                alert.showAndWait();
            } else {
                prepare = connect.prepareStatement(sql);
                prepare.setString(1, idField.getText());
                prepare.setString(2, passwordField.getText());
                prepare.setString(3, serviceCombo.getValue());
                
                result = prepare.executeQuery();
                
                if (result.next()) {
                    // Définir l'utilisateur et le service dans la session
                    UserSession.setCurrentUserAndService(idField.getText(), serviceCombo.getValue());
                    
                    // Déterminer le fichier FXML correspondant au service
                    String fxmlFile = "main.fxml"; // Valeur par défaut
                    switch (serviceCombo.getValue()) {
                        case "Logistique":
                            fxmlFile = "mainLog.fxml";
                            break;
                        case "Opérations":
                            fxmlFile = "mainOps.fxml";
                            break;
                        case "Ressources Humaines":
                            fxmlFile = "mainRh.fxml";
                            break;
                        case "admin":
                            fxmlFile = "main.fxml";
                            break;
                    }
                    
                    FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
                    Parent root = loader.load();
                    Scene scene = new Scene(root, 1024, 768);
                    scene.getStylesheets().add(getClass().getResource("loginPage.css").toExternalForm());
                    
                    Stage stage = (Stage) submitBTN.getScene().getWindow();
                    stage.setScene(scene);
                    stage.setTitle("Application de Gestion Militaire - " + serviceCombo.getValue());
                    stage.show();
                    
                    // Enregistrer la connexion réussie avec service
                    HistoryManager.logConnexionAttempt(idField.getText(), true);
                    
                } else {
                    alert = new Alert(AlertType.ERROR);
                    alert.setTitle("Error Message");
                    alert.setHeaderText(null);
                    alert.setContentText("Au moins une entrée est incorrecte, veuillez corriger");
                    HistoryManager.logConnexionAttempt(idField.getText(), false);
                    alert.showAndWait();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        serviceCombo.getItems().addAll("admin", "Logistique", "Opérations", "Ressources Humaines");
    }
}
