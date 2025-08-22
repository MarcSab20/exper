package application;

import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ListView;
import javafx.fxml.Initializable;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.net.URL;
import java.util.ResourceBundle;

public class AccueilOpsController implements Initializable {
    @FXML
    private TableView<Mission> missionsTable;
    @FXML
    private TableColumn<Mission, String> codeMissionColumn;
    @FXML
    private TableColumn<Mission, String> typeMissionColumn;
    @FXML
    private TableColumn<Mission, String> statutMissionColumn;
    @FXML
    private TableColumn<Mission, String> personnelColumn;
    
    @FXML
    private TableView<Rapport> rapportsTable;
    @FXML
    private TableColumn<Rapport, String> dateRapportColumn;
    @FXML
    private TableColumn<Rapport, String> typeRapportColumn;
    @FXML
    private TableColumn<Rapport, String> statutRapportColumn;
    
    @FXML
    private ListView<String> alertesList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeMissionsTable();
        initializeRapportsTable();
        initializeAlertesList();
    }

    private void initializeMissionsTable() {
        // Configuration des colonnes du tableau des missions
        codeMissionColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getCodeMission()));
        typeMissionColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getTypeMission()));
        statutMissionColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getStatutMission()));
        personnelColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getPersonnel()));

        // Exemple de données
        ObservableList<Mission> missions = FXCollections.observableArrayList(
            new Mission("M001", "Reconnaissance", "En cours", "Équipe Alpha"),
            new Mission("M002", "Surveillance", "Planifiée", "Équipe Bravo"),
            new Mission("M003", "Logistique", "Terminée", "Équipe Charlie")
        );
        
        missionsTable.setItems(missions);
    }

    private void initializeRapportsTable() {
        // Configuration des colonnes du tableau des rapports
        dateRapportColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getDate()));
        typeRapportColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getType()));
        statutRapportColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getStatut()));

        // Exemple de données
        ObservableList<Rapport> rapports = FXCollections.observableArrayList(
            new Rapport("2024-02-20", "Quotidien", "Validé"),
            new Rapport("2024-02-19", "Situation", "En révision"),
            new Rapport("2024-02-18", "Incident", "Archivé")
        );
        
        rapportsTable.setItems(rapports);
    }

    private void initializeAlertesList() {
        ObservableList<String> alertes = FXCollections.observableArrayList(
            "Alerte 1: Maintenance requise",
            "Alerte 2: Mise à jour disponible",
            "Alerte 3: Nouveau rapport disponible"
        );
        alertesList.setItems(alertes);
    }
}

// Classe pour les missions
class Mission {
    private String codeMission;
    private String typeMission;
    private String statutMission;
    private String personnel;

    public Mission(String codeMission, String typeMission, String statutMission, String personnel) {
        this.codeMission = codeMission;
        this.typeMission = typeMission;
        this.statutMission = statutMission;
        this.personnel = personnel;
    }

    public String getCodeMission() { return codeMission; }
    public String getTypeMission() { return typeMission; }
    public String getStatutMission() { return statutMission; }
    public String getPersonnel() { return personnel; }
}

// Classe pour les rapports
class Rapport {
    private String date;
    private String type;
    private String statut;

    public Rapport(String date, String type, String statut) {
        this.date = date;
        this.type = type;
        this.statut = statut;
    }

    public String getDate() { return date; }
    public String getType() { return type; }
    public String getStatut() { return statut; }
}