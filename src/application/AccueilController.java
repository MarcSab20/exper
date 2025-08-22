package application;

import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.control.TableColumn;
import java.util.Date;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;


public class AccueilController {
    @FXML
    private ImageView organigrammeView;
    
    @FXML
    private TableView<OperationRecord> recentOperationsTable;
    
    @FXML
    private TableColumn<OperationRecord, Date> dateColumn;
    
    @FXML
    private TableColumn<OperationRecord, String> operationColumn;
    
    @FXML
    private TableColumn<OperationRecord, String> userColumn;

    @FXML
    public void initialize() {
        // Initialiser l'organigramme
    	Image organigrammeImage = new Image("file:path/to/your/organigramme.png"); // Remplacez par votre image
        organigrammeView.setImage(organigrammeImage);
        
        

        // Configurer les colonnes du tableau
        dateColumn.setCellValueFactory(cellData -> 
            new SimpleObjectProperty<>(cellData.getValue().getDate()));
        
        operationColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getOperation()));
        
        userColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getUser()));

        // Charger les opérations récentes
        loadRecentOperations();
    }

    private void loadRecentOperations() {
        // TODO: Charger les vraies opérations depuis la base de données
        ObservableList<OperationRecord> operations = FXCollections.observableArrayList(
            new OperationRecord(new Date(), "Mise à jour des données", "Admin"),
            new OperationRecord(new Date(), "Génération des graphes", "Admin"),
            new OperationRecord(new Date(), "Consultation des requêtes", "Admin")
        );
        
        recentOperationsTable.setItems(operations);
    }
}

// Classe pour représenter une opération
class OperationRecord {
    private final Date date;
    private final String operation;
    private final String user;

    public OperationRecord(Date date, String operation, String user) {
        this.date = date;
        this.operation = operation;
        this.user = user;
    }

    public Date getDate() { return date; }
    public String getOperation() { return operation; }
    public String getUser() { return user; }
}