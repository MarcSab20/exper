package application;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.scene.control.cell.PropertyValueFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.*;

public class HistoriqueController {
    private static final Logger LOGGER = Logger.getLogger(HistoriqueController.class.getName());
    
    @FXML
    private DatePicker connexionDateDebut, connexionDateFin;
    @FXML
    private DatePicker modifDateDebut, modifDateFin;
    @FXML
    private ComboBox<String> tableFilter;
    @FXML
    private TableView<ConnexionRecord> connexionsTable;
    @FXML
    private TableView<ModificationRecord> modificationsTable;
    
    @FXML
    private TableColumn<ConnexionRecord, Date> connexionDateColumn;
    @FXML
    private TableColumn<ConnexionRecord, String> userColumn;
    @FXML
    private TableColumn<ConnexionRecord, String> actionColumn;
    @FXML
    private TableColumn<ConnexionRecord, String> statusColumn;
    
    @FXML
    private TableColumn<ModificationRecord, Date> modifDateColumn;
    @FXML
    private TableColumn<ModificationRecord, String> tableColumn;
    @FXML
    private TableColumn<ModificationRecord, String> typeColumn;
    @FXML
    private TableColumn<ModificationRecord, String> modifUserColumn;
    @FXML
    private TableColumn<ModificationRecord, String> detailsColumn;

    @FXML
    public void initialize() {
        // Initialiser les filtres
        tableFilter.getItems().addAll(
            "Accueil",
            "Dashboard",
            "Mise À Jour",
            "Requêtes"
        );
        tableFilter.setValue("Accueil"); // Valeur par défaut

        // Configurer les dates par défaut
        LocalDate now = LocalDate.now();
        connexionDateDebut.setValue(now.minusMonths(1));
        connexionDateFin.setValue(now);
        modifDateDebut.setValue(now.minusMonths(1));
        modifDateFin.setValue(now);
        
        // Configurer les colonnes pour l'historique des connexions
        connexionDateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        userColumn.setCellValueFactory(new PropertyValueFactory<>("user"));
        actionColumn.setCellValueFactory(new PropertyValueFactory<>("action"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        // Configurer les colonnes pour l'historique des modifications
        modifDateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        tableColumn.setCellValueFactory(new PropertyValueFactory<>("table"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        modifUserColumn.setCellValueFactory(new PropertyValueFactory<>("user"));
        detailsColumn.setCellValueFactory(new PropertyValueFactory<>("details"));

        // Charger les données initiales
        chargerHistoriqueConnexions();
        chargerHistoriqueModifications();
    }

    @FXML
    private void filtrerConnexions() {
        LocalDate debut = connexionDateDebut.getValue();
        LocalDate fin = connexionDateFin.getValue();
        
        if (debut == null || fin == null) {
            showAlert("Erreur", "Veuillez sélectionner des dates valides");
            return;
        }
        
        // Vérifier que la date de début est antérieure à la date de fin
        if (debut.isAfter(fin)) {
            showAlert("Erreur", "La date de début doit être antérieure à la date de fin");
            return;
        }

        chargerHistoriqueConnexions();
    }

    @FXML
    private void filtrerModifications() {
        LocalDate debut = modifDateDebut.getValue();
        LocalDate fin = modifDateFin.getValue();
        String table = tableFilter.getValue();
        
        if (debut == null || fin == null) {
            showAlert("Erreur", "Veuillez sélectionner des dates valides");
            return;
        }
        
        // Vérifier que la date de début est antérieure à la date de fin
        if (debut.isAfter(fin)) {
            showAlert("Erreur", "La date de début doit être antérieure à la date de fin");
            return;
        }

        chargerHistoriqueModifications();
    }

    private void chargerHistoriqueConnexions() {
        List<ConnexionRecord> connexions = new ArrayList<>();
        
        // Convertir les dates LocalDate en Date
        Date dateDebut = convertLocalDateToDate(connexionDateDebut.getValue());
        Date dateFin = convertLocalDateToDate(connexionDateFin.getValue().plusDays(1)); // +1 jour pour inclure la date de fin
        
        String sql = "SELECT id, date, utilisateur, action, statut FROM historique_connexions " +
                     "WHERE date BETWEEN ? AND ? ORDER BY date DESC";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, new Timestamp(dateDebut.getTime()));
            stmt.setTimestamp(2, new Timestamp(dateFin.getTime()));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    connexions.add(new ConnexionRecord(
                        rs.getInt("id"),
                        rs.getTimestamp("date"),
                        rs.getString("utilisateur"),
                        rs.getString("action"),
                        rs.getString("statut")
                    ));
                }
            }
            
            connexionsTable.setItems(FXCollections.observableArrayList(connexions));
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement de l'historique des connexions", e);
            showAlert("Erreur", "Impossible de charger l'historique des connexions");
        }
    }

    private void chargerHistoriqueModifications() {
        List<ModificationRecord> modifications = new ArrayList<>();
        
        // Convertir les dates LocalDate en Date
        Date dateDebut = convertLocalDateToDate(modifDateDebut.getValue());
        Date dateFin = convertLocalDateToDate(modifDateFin.getValue().plusDays(1)); // +1 jour pour inclure la date de fin
        String tableFiltre = tableFilter.getValue();
        
        String sql = "SELECT id, date, table_modifiee, type_modification, utilisateur, details " +
                     "FROM historique_modifications WHERE date BETWEEN ? AND ? ";
        
        if (tableFiltre != null && !tableFiltre.isEmpty()) {
            sql += "AND table_modifiee = ? ";
        }
        
        sql += "ORDER BY date DESC";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setTimestamp(1, new Timestamp(dateDebut.getTime()));
            stmt.setTimestamp(2, new Timestamp(dateFin.getTime()));
            
            if (tableFiltre != null && !tableFiltre.isEmpty()) {
                stmt.setString(3, tableFiltre);
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    modifications.add(new ModificationRecord(
                        rs.getInt("id"),
                        rs.getTimestamp("date"),
                        rs.getString("table_modifiee"),
                        rs.getString("type_modification"),
                        rs.getString("utilisateur"),
                        rs.getString("details")
                    ));
                }
            }
            
            modificationsTable.setItems(FXCollections.observableArrayList(modifications));
            
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Erreur lors du chargement de l'historique des modifications", e);
            showAlert("Erreur", "Impossible de charger l'historique des modifications");
        }
    }

    @FXML
    private void exporterExcel() {
        // Créer un nouveau classeur Excel
        try (Workbook workbook = new XSSFWorkbook()) {
            // Créer la feuille pour les connexions
            Sheet sheet1 = workbook.createSheet("Historique Connexions");
            
            // Créer l'en-tête
            Row headerRow = sheet1.createRow(0);
            headerRow.createCell(0).setCellValue("Date");
            headerRow.createCell(1).setCellValue("Utilisateur");
            headerRow.createCell(2).setCellValue("Action");
            headerRow.createCell(3).setCellValue("Statut");
            
            // Remplir avec les données
            int rowNum = 1;
            for (ConnexionRecord record : connexionsTable.getItems()) {
                Row row = sheet1.createRow(rowNum++);
                row.createCell(0).setCellValue(record.getDate().toString());
                row.createCell(1).setCellValue(record.getUser());
                row.createCell(2).setCellValue(record.getAction());
                row.createCell(3).setCellValue(record.getStatus());
            }
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < 4; i++) {
                sheet1.autoSizeColumn(i);
            }
            
            // Créer la feuille pour les modifications
            Sheet sheet2 = workbook.createSheet("Historique Modifications");
            
            // Créer l'en-tête
            headerRow = sheet2.createRow(0);
            headerRow.createCell(0).setCellValue("Date");
            headerRow.createCell(1).setCellValue("Table");
            headerRow.createCell(2).setCellValue("Type");
            headerRow.createCell(3).setCellValue("Utilisateur");
            headerRow.createCell(4).setCellValue("Détails");
            
            // Remplir avec les données
            rowNum = 1;
            for (ModificationRecord record : modificationsTable.getItems()) {
                Row row = sheet2.createRow(rowNum++);
                row.createCell(0).setCellValue(record.getDate().toString());
                row.createCell(1).setCellValue(record.getTable());
                row.createCell(2).setCellValue(record.getType());
                row.createCell(3).setCellValue(record.getUser());
                row.createCell(4).setCellValue(record.getDetails());
            }
            
            // Ajuster la largeur des colonnes
            for (int i = 0; i < 5; i++) {
                sheet2.autoSizeColumn(i);
            }
            
            // Enregistrer le fichier
            File file = new File("Historique_" + System.currentTimeMillis() + ".xlsx");
            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
                showInformation("Export réussi", "Le fichier a été exporté à l'emplacement : " + file.getAbsolutePath());
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'export Excel", e);
            showAlert("Erreur", "Impossible d'exporter en Excel : " + e.getMessage());
        }
    }

    @FXML
    private void exporterPDF() {
        try {
            // Créer un document PDF
            Document document = new Document();
            File file = new File("Historique_" + System.currentTimeMillis() + ".pdf");
            PdfWriter.getInstance(document, new FileOutputStream(file));
            
            document.open();
            
            // Ajouter un titre
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph("Rapport d'historique", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" ")); // Espace
            
            // Historique des connexions
            Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            document.add(new Paragraph("Historique des connexions", sectionFont));
            document.add(new Paragraph(" ")); // Espace
            
            PdfPTable table1 = new PdfPTable(4);
            table1.setWidthPercentage(100);
            
            // En-tête
            table1.addCell("Date");
            table1.addCell("Utilisateur");
            table1.addCell("Action");
            table1.addCell("Statut");
            
            // Données
            for (ConnexionRecord record : connexionsTable.getItems()) {
                table1.addCell(record.getDate().toString());
                table1.addCell(record.getUser());
                table1.addCell(record.getAction());
                table1.addCell(record.getStatus());
            }
            
            document.add(table1);
            document.add(new Paragraph(" ")); // Espace
            
            // Historique des modifications
            document.add(new Paragraph("Historique des modifications", sectionFont));
            document.add(new Paragraph(" ")); // Espace
            
            PdfPTable table2 = new PdfPTable(5);
            table2.setWidthPercentage(100);
            
            // En-tête
            table2.addCell("Date");
            table2.addCell("Table");
            table2.addCell("Type");
            table2.addCell("Utilisateur");
            table2.addCell("Détails");
            
            // Données
            for (ModificationRecord record : modificationsTable.getItems()) {
                table2.addCell(record.getDate().toString());
                table2.addCell(record.getTable());
                table2.addCell(record.getType());
                table2.addCell(record.getUser());
                table2.addCell(record.getDetails());
            }
            
            document.add(table2);
            
            document.close();
            
            showInformation("Export réussi", "Le fichier a été exporté à l'emplacement : " + file.getAbsolutePath());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de l'export PDF", e);
            showAlert("Erreur", "Impossible d'exporter en PDF : " + e.getMessage());
        }
    }

    private Date convertLocalDateToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    private void showInformation(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}