package application;

import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ListView;
import javafx.fxml.Initializable;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.net.URL;
import java.util.ResourceBundle;

public class AccueilRhController implements Initializable {
    @FXML
    private TableView<Effectif> effectifsTable;
    @FXML
    private TableColumn<Effectif, String> uniteColumn;
    @FXML
    private TableColumn<Effectif, Integer> totalColumn;
    @FXML
    private TableColumn<Effectif, Integer> presentsColumn;
    @FXML
    private TableColumn<Effectif, Integer> enMissionColumn;
    
    @FXML
    private TableView<Formation> formationsTable;
    @FXML
    private TableColumn<Formation, String> formationColumn;
    @FXML
    private TableColumn<Formation, String> dateDebutColumn;
    @FXML
    private TableColumn<Formation, String> dateFinColumn;
    @FXML
    private TableColumn<Formation, Integer> participantsColumn;
    
    @FXML
    private ListView<String> demandesList;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeEffectifsTable();
        initializeFormationsTable();
        initializeDemandesList();
    }

    private void initializeEffectifsTable() {
        uniteColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getUnite()));
        totalColumn.setCellValueFactory(cellData -> 
            new SimpleIntegerProperty(cellData.getValue().getTotal()).asObject());
        presentsColumn.setCellValueFactory(cellData -> 
            new SimpleIntegerProperty(cellData.getValue().getPresents()).asObject());
        enMissionColumn.setCellValueFactory(cellData -> 
            new SimpleIntegerProperty(cellData.getValue().getEnMission()).asObject());

        // Données d'exemple pour les effectifs
        ObservableList<Effectif> effectifs = FXCollections.observableArrayList(
            new Effectif("Unité Alpha", 100, 85, 15),
            new Effectif("Unité Bravo", 80, 70, 10),
            new Effectif("Unité Charlie", 120, 100, 20),
            new Effectif("Support", 50, 45, 5),
            new Effectif("Administration", 30, 28, 2)
        );
        
        effectifsTable.setItems(effectifs);
    }

    private void initializeFormationsTable() {
        formationColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getFormation()));
        dateDebutColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getDateDebut()));
        dateFinColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getDateFin()));
        participantsColumn.setCellValueFactory(cellData -> 
            new SimpleIntegerProperty(cellData.getValue().getParticipants()).asObject());

        // Données d'exemple pour les formations
        ObservableList<Formation> formations = FXCollections.observableArrayList(
            new Formation("Formation tactique", "2024-03-01", "2024-03-15", 20),
            new Formation("Secourisme", "2024-03-10", "2024-03-20", 15),
            new Formation("Management", "2024-03-15", "2024-03-25", 10),
            new Formation("Informatique", "2024-04-01", "2024-04-10", 12),
            new Formation("Communication", "2024-04-15", "2024-04-25", 18)
        );
        
        formationsTable.setItems(formations);
    }

    private void initializeDemandesList() {
        // Données d'exemple pour les demandes
        ObservableList<String> demandes = FXCollections.observableArrayList(
            "Demande de mutation - Sergent Martin",
            "Demande de formation - Caporal Dubois",
            "Demande de congé - Lieutenant Bernard",
            "Demande de stage - Adjudant Robert",
            "Demande de reconversion - Capitaine Leroy"
        );
        
        demandesList.setItems(demandes);
    }
}

// Classe pour les effectifs
class Effectif {
    private String unite;
    private int total;
    private int presents;
    private int enMission;

    public Effectif(String unite, int total, int presents, int enMission) {
        this.unite = unite;
        this.total = total;
        this.presents = presents;
        this.enMission = enMission;
    }

    public String getUnite() { return unite; }
    public int getTotal() { return total; }
    public int getPresents() { return presents; }
    public int getEnMission() { return enMission; }
}

// Classe pour les formations
class Formation {
    private String formation;
    private String dateDebut;
    private String dateFin;
    private int participants;

    public Formation(String formation, String dateDebut, String dateFin, int participants) {
        this.formation = formation;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.participants = participants;
    }

    public String getFormation() { return formation; }
    public String getDateDebut() { return dateDebut; }
    public String getDateFin() { return dateFin; }
    public int getParticipants() { return participants; }
}