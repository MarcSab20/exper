package application;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;

import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Image;

/**
 * Gestionnaire de rapports pour les fonctionnalités logistiques améliorées
 */
public class LogisticReportManager {
    private static final Logger LOGGER = Logger.getLogger(LogisticReportManager.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    
    /**
     * Génère un rapport complet d'état logistique en PDF
     */
    public static boolean generateCompleteStatusReport(Connection connection, List<Stock> stocks, List<Maintenance> maintenances) {
        try {
            String fileName = "Rapport_Logistique_Complet_" + 
                             LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";
            File file = new File(fileName);
            
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(file));
            
            document.open();
            
            // En-tête du rapport
            addReportHeader(document, "RAPPORT LOGISTIQUE COMPLET");
            
            // Section 1: Résumé exécutif
            addExecutiveSummary(document, connection);
            
            // Section 2: État détaillé des stocks
            addDetailedStockStatus(document, stocks);
            
            // Section 3: État des maintenances
            addMaintenanceStatus(document, maintenances);
            
            // Section 4: Historique des mouvements récents
            addRecentMovements(document, connection);
            
            // Section 5: Alertes et recommandations
            addAlertsAndRecommendations(document, stocks, maintenances);
            
            document.close();
            
            LOGGER.info("Rapport complet généré: " + file.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la génération du rapport complet", e);
            return false;
        }
    }
    
    /**
     * Génère un rapport de fiche d'équipement détaillée en PDF
     */
    public static boolean generateEquipmentDetailReport(EquipmentCard equipmentCard) {
        try {
            String fileName = "Fiche_" + 
                             equipmentCard.getStock().getDesignation().replaceAll("[^a-zA-Z0-9]", "_") + 
                             "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";
            File file = new File(fileName);
            
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(file));
            
            document.open();
            
            // En-tête
            addReportHeader(document, "FICHE DÉTAILLÉE D'ÉQUIPEMENT");
            
            // Nom de l'équipement
            Font equipmentNameFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, BaseColor.DARK_GRAY);
            Paragraph equipmentName = new Paragraph(equipmentCard.getStock().getDesignation(), equipmentNameFont);
            equipmentName.setAlignment(Element.ALIGN_CENTER);
            document.add(equipmentName);
            document.add(new Paragraph(" "));
            
            // Informations générales
            addEquipmentGeneralInfo(document, equipmentCard);
            
            // Statistiques des mouvements
            addMovementStatistics(document, equipmentCard);
            
            // Historique détaillé des mouvements
            addMovementHistory(document, equipmentCard);
            
            // Graphique d'évolution (texte pour maintenant)
            addEvolutionSummary(document, equipmentCard);
            
            document.close();
            
            LOGGER.info("Fiche d'équipement générée: " + file.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la génération de la fiche d'équipement", e);
            return false;
        }
    }
    
    /**
     * Génère un rapport Excel avec plusieurs feuilles
     */
    public static boolean generateExcelReport(Connection connection, List<Stock> stocks, List<Maintenance> maintenances) {
        try {
            String fileName = "Rapport_Logistique_" + 
                             LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".xlsx";
            File file = new File(fileName);
            
            try (Workbook workbook = new XSSFWorkbook()) {
                // Feuille 1: Dashboard
                createDashboardSheet(workbook, connection);
                
                // Feuille 2: Stocks détaillés
                createStockDetailsSheet(workbook, stocks);
                
                // Feuille 3: Maintenances
                createMaintenanceSheet(workbook, maintenances);
                
                // Feuille 4: Mouvements récents
                createMovementsSheet(workbook, connection);
                
                // Feuille 5: Alertes
                createAlertsSheet(workbook, stocks, maintenances);
                
                // Sauvegarder le fichier
                try (FileOutputStream fileOut = new FileOutputStream(file)) {
                    workbook.write(fileOut);
                }
            }
            
            LOGGER.info("Rapport Excel généré: " + file.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur lors de la génération du rapport Excel", e);
            return false;
        }
    }
    
    /**
     * Ajoute l'en-tête standard du rapport
     */
    private static void addReportHeader(Document document, String title) throws Exception {
        // Titre principal
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD, BaseColor.DARK_GRAY);
        Paragraph titleParagraph = new Paragraph(title, titleFont);
        titleParagraph.setAlignment(Element.ALIGN_CENTER);
        document.add(titleParagraph);
        
        // Sous-titre avec date et heure
        Font subtitleFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL, BaseColor.GRAY);
        Paragraph subtitle = new Paragraph(
            "Généré le " + LocalDate.now().format(DATE_FORMATTER) + 
            " par " + UserSession.getCurrentUser() + 
            " - Service " + UserSession.getCurrentService(), subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        document.add(subtitle);
        
        // Ligne de séparation
        document.add(new Paragraph(" "));
        addSeparatorLine(document);
        document.add(new Paragraph(" "));
    }
    
    /**
     * Ajoute une ligne de séparation
     */
    private static void addSeparatorLine(Document document) throws Exception {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setBorderWidthBottom(1);
        cell.setBorderColorBottom(BaseColor.LIGHT_GRAY);
        cell.setFixedHeight(10);
        line.addCell(cell);
        document.add(line);
    }
    
    /**
     * Ajoute le résumé exécutif
     */
    private static void addExecutiveSummary(Document document, Connection connection) throws Exception {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.DARK_GRAY);
        document.add(new Paragraph("RÉSUMÉ EXÉCUTIF", sectionFont));
        document.add(new Paragraph(" "));
        
        // Récupérer les statistiques depuis la vue dashboard
        String sql = "SELECT * FROM logistic_dashboard LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                PdfPTable summaryTable = new PdfPTable(2);
                summaryTable.setWidthPercentage(70);
                summaryTable.setWidths(new float[]{3, 1});
                
                // Style pour les cellules
                Font labelFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
                Font valueFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
                
                addSummaryRow(summaryTable, "Total des équipements", String.valueOf(rs.getInt("total_equipements")), labelFont, valueFont);
                addSummaryRow(summaryTable, "Stocks critiques/faibles", String.valueOf(rs.getInt("stocks_critiques")), labelFont, valueFont, BaseColor.RED);
                addSummaryRow(summaryTable, "Maintenances urgentes", String.valueOf(rs.getInt("maintenances_urgentes")), labelFont, valueFont, BaseColor.ORANGE);
                addSummaryRow(summaryTable, "Mouvements (30 derniers jours)", String.valueOf(rs.getInt("mouvements_30j")), labelFont, valueFont);
                addSummaryRow(summaryTable, "- Approvisionnements", String.valueOf(rs.getInt("approvisionnements_30j")), labelFont, valueFont, BaseColor.GREEN);
                addSummaryRow(summaryTable, "- Retraits", String.valueOf(rs.getInt("retraits_30j")), labelFont, valueFont, BaseColor.BLUE);
                
                document.add(summaryTable);
            }
        }
        
        document.add(new Paragraph(" "));
    }
    
    /**
     * Ajoute une ligne au tableau de résumé
     */
    private static void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        addSummaryRow(table, label, value, labelFont, valueFont, null);
    }
    
    private static void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont, BaseColor color) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(PdfPCell.NO_BORDER);
        labelCell.setPadding(5);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(PdfPCell.NO_BORDER);
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        if (color != null) {
            valueCell.setBackgroundColor(color);
            labelCell.setBackgroundColor(new BaseColor(color.getRed(), color.getGreen(), color.getBlue(), 50));
        }
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
    
    /**
     * Ajoute l'état détaillé des stocks
     */
    private static void addDetailedStockStatus(Document document, List<Stock> stocks) throws Exception {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.DARK_GRAY);
        document.add(new Paragraph("ÉTAT DÉTAILLÉ DES STOCKS", sectionFont));
        document.add(new Paragraph(" "));
        
        PdfPTable stockTable = new PdfPTable(5);
        stockTable.setWidthPercentage(100);
        stockTable.setWidths(new float[]{3, 1, 1, 1, 1});
        
        // En-têtes
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
        String[] headers = {"Désignation", "Quantité", "Seuil Crit.", "État", "Statut"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BaseColor.DARK_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(8);
            stockTable.addCell(cell);
        }
        
        // Données
        Font cellFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL);
        for (Stock stock : stocks) {
            stockTable.addCell(new PdfPCell(new Phrase(stock.getDesignation(), cellFont)));
            stockTable.addCell(new PdfPCell(new Phrase(String.valueOf(stock.getQuantite()), cellFont)));
            stockTable.addCell(new PdfPCell(new Phrase(String.valueOf(stock.getValeurCritique()), cellFont)));
            stockTable.addCell(new PdfPCell(new Phrase(stock.getEtat(), cellFont)));
            
            PdfPCell statusCell = new PdfPCell(new Phrase(getStatusText(stock.getStatut()), cellFont));
            statusCell.setBackgroundColor(getStatusColor(stock.getStatut()));
            stockTable.addCell(statusCell);
        }
        
        document.add(stockTable);
        document.add(new Paragraph(" "));
    }
    
    /**
     * Ajoute l'état des maintenances
     */
    private static void addMaintenanceStatus(Document document, List<Maintenance> maintenances) throws Exception {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.DARK_GRAY);
        document.add(new Paragraph("ÉTAT DES MAINTENANCES", sectionFont));
        document.add(new Paragraph(" "));
        
        PdfPTable maintenanceTable = new PdfPTable(5);
        maintenanceTable.setWidthPercentage(100);
        maintenanceTable.setWidths(new float[]{2, 1, 2, 2, 1});
        
        // En-têtes
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
        String[] headers = {"Désignation", "Date", "Type", "Description", "Statut"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BaseColor.DARK_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(8);
            maintenanceTable.addCell(cell);
        }
        
        // Données
        Font cellFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL);
        for (Maintenance maintenance : maintenances) {
            maintenanceTable.addCell(new PdfPCell(new Phrase(maintenance.getDesignation(), cellFont)));
            maintenanceTable.addCell(new PdfPCell(new Phrase(maintenance.getDate(), cellFont)));
            maintenanceTable.addCell(new PdfPCell(new Phrase(maintenance.getType(), cellFont)));
            maintenanceTable.addCell(new PdfPCell(new Phrase(maintenance.getDescription(), cellFont)));
            
            String statusText = maintenance.isEffectuee() ? "Effectuée" : getMaintenanceStatusText(maintenance.getStatut());
            PdfPCell statusCell = new PdfPCell(new Phrase(statusText, cellFont));
            statusCell.setBackgroundColor(maintenance.isEffectuee() ? BaseColor.BLUE : getStatusColor(maintenance.getStatut()));
            maintenanceTable.addCell(statusCell);
        }
        
        document.add(maintenanceTable);
        document.add(new Paragraph(" "));
    }
    
    /**
     * Ajoute l'historique des mouvements récents
     */
    private static void addRecentMovements(Document document, Connection connection) throws Exception {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.DARK_GRAY);
        document.add(new Paragraph("MOUVEMENTS RÉCENTS (10 DERNIERS)", sectionFont));
        document.add(new Paragraph(" "));
        
        String sql = """
            SELECT sm.*, s.designation
            FROM stock_movements sm
            JOIN stocks s ON sm.stock_id = s.id
            ORDER BY sm.date_movement DESC
            LIMIT 10""";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            PdfPTable movementTable = new PdfPTable(5);
            movementTable.setWidthPercentage(100);
            movementTable.setWidths(new float[]{1.5f, 2, 1, 1, 2});
            
            // En-têtes
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
            String[] headers = {"Date", "Équipement", "Type", "Quantité", "Description"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(BaseColor.DARK_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(8);
                movementTable.addCell(cell);
            }
            
            // Données
            Font cellFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL);
            while (rs.next()) {
                movementTable.addCell(new PdfPCell(new Phrase(
                    rs.getTimestamp("date_movement").toLocalDateTime().format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), cellFont)));
                movementTable.addCell(new PdfPCell(new Phrase(rs.getString("designation"), cellFont)));
                
                String type = rs.getString("type");
                PdfPCell typeCell = new PdfPCell(new Phrase(type, cellFont));
                typeCell.setBackgroundColor("APPROVISIONNEMENT".equals(type) ? BaseColor.GREEN : BaseColor.ORANGE);
                movementTable.addCell(typeCell);
                
                String quantity = ("APPROVISIONNEMENT".equals(type) ? "+" : "-") + rs.getInt("quantite");
                movementTable.addCell(new PdfPCell(new Phrase(quantity, cellFont)));
                movementTable.addCell(new PdfPCell(new Phrase(rs.getString("description"), cellFont)));
            }
            
            document.add(movementTable);
        }
        
        document.add(new Paragraph(" "));
    }
    
    /**
     * Ajoute les alertes et recommandations
     */
    private static void addAlertsAndRecommendations(Document document, List<Stock> stocks, List<Maintenance> maintenances) throws Exception {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.DARK_GRAY);
        document.add(new Paragraph("ALERTES ET RECOMMANDATIONS", sectionFont));
        document.add(new Paragraph(" "));
        
        Font alertFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
        
        // Alertes stocks
        boolean hasStockAlerts = false;
        for (Stock stock : stocks) {
            if ("VIOLET".equals(stock.getStatut()) || "ROUGE".equals(stock.getStatut())) {
                if (!hasStockAlerts) {
                    document.add(new Paragraph("📦 ALERTES STOCKS:", 
                        new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.RED)));
                    hasStockAlerts = true;
                }
                document.add(new Paragraph("• " + stock.getDesignation() + 
                    " - Quantité: " + stock.getQuantite() + 
                    " (Seuil: " + stock.getValeurCritique() + ")", alertFont));
            }
        }
        
        if (hasStockAlerts) {
            document.add(new Paragraph(" "));
        }
        
        // Alertes maintenances
        boolean hasMaintenanceAlerts = false;
        for (Maintenance maintenance : maintenances) {
            if (!maintenance.isEffectuee() && ("VIOLET".equals(maintenance.getStatut()) || "ROUGE".equals(maintenance.getStatut()))) {
                if (!hasMaintenanceAlerts) {
                    document.add(new Paragraph("🔧 ALERTES MAINTENANCES:", 
                        new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.ORANGE)));
                    hasMaintenanceAlerts = true;
                }
                document.add(new Paragraph("• " + maintenance.getDesignation() + 
                    " - Date prévue: " + maintenance.getDate() + 
                    " (" + getMaintenanceStatusText(maintenance.getStatut()) + ")", alertFont));
            }
        }
        
        if (!hasStockAlerts && !hasMaintenanceAlerts) {
            document.add(new Paragraph("✅ Aucune alerte critique détectée", 
                new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.GREEN)));
        }
    }
    
    /**
     * Ajoute les informations générales de l'équipement
     */
    private static void addEquipmentGeneralInfo(Document document, EquipmentCard equipmentCard) throws Exception {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        document.add(new Paragraph("Informations générales", sectionFont));
        
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(70);
        infoTable.setWidths(new float[]{2, 1});
        
        Font labelFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
        
        addInfoRow(infoTable, "Date de création:", equipmentCard.getFormattedDateCreation(), labelFont, valueFont);
        addInfoRow(infoTable, "Quantité initiale:", String.valueOf(equipmentCard.getQuantiteInitiale()), labelFont, valueFont);
        addInfoRow(infoTable, "Quantité actuelle:", String.valueOf(equipmentCard.getStock().getQuantite()), labelFont, valueFont);
        addInfoRow(infoTable, "État:", equipmentCard.getStock().getEtat(), labelFont, valueFont);
        addInfoRow(infoTable, "Seuil critique:", String.valueOf(equipmentCard.getStock().getValeurCritique()), labelFont, valueFont);
        addInfoRow(infoTable, "Statut:", getStatusText(equipmentCard.getStock().getStatut()), labelFont, valueFont);
        
        document.add(infoTable);
        document.add(new Paragraph(" "));
    }
    
    /**
     * Ajoute les statistiques des mouvements
     */
    private static void addMovementStatistics(Document document, EquipmentCard equipmentCard) throws Exception {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        document.add(new Paragraph("Statistiques des mouvements", sectionFont));
        
        PdfPTable statsTable = new PdfPTable(2);
        statsTable.setWidthPercentage(70);
        
        Font labelFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
        
        addInfoRow(statsTable, "Nombre total de mouvements:", String.valueOf(equipmentCard.getNombreMouvements()), labelFont, valueFont);
        addInfoRow(statsTable, "Total approvisionnements:", "+" + equipmentCard.getTotalApprovisionements(), labelFont, valueFont);
        addInfoRow(statsTable, "Total retraits:", "-" + equipmentCard.getTotalRetraits(), labelFont, valueFont);
        addInfoRow(statsTable, "Quantité calculée:", String.valueOf(equipmentCard.getQuantiteCalculee()), labelFont, valueFont);
        
        if (equipmentCard.hasDiscrepancy()) {
            addInfoRow(statsTable, "⚠️ Discordance:", "OUI", labelFont, 
                      new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.RED));
        } else {
            addInfoRow(statsTable, "✅ Cohérence:", "OK", labelFont, 
                      new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.GREEN));
        }
        
        document.add(statsTable);
        document.add(new Paragraph(" "));
    }
    
    /**
     * Ajoute l'historique des mouvements
     */
    private static void addMovementHistory(Document document, EquipmentCard equipmentCard) throws Exception {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        document.add(new Paragraph("Historique des mouvements", sectionFont));
        
        if (equipmentCard.getMouvements().isEmpty()) {
            document.add(new Paragraph("Aucun mouvement enregistré", 
                        new Font(Font.FontFamily.HELVETICA, 10, Font.ITALIC, BaseColor.GRAY)));
            return;
        }
        
        PdfPTable historyTable = new PdfPTable(5);
        historyTable.setWidthPercentage(100);
        historyTable.setWidths(new float[]{1.5f, 1, 1, 1, 2});
        
        // En-têtes
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);
        String[] headers = {"Date", "Type", "Quantité", "Résultat", "Description"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BaseColor.DARK_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            historyTable.addCell(cell);
        }
        
        // Données (limitées aux 20 dernières)
        Font cellFont = new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL);
        List<StockMovement> mouvements = equipmentCard.getMouvements();
        int maxMovements = Math.min(20, mouvements.size());
        
        for (int i = 0; i < maxMovements; i++) {
            StockMovement mouvement = mouvements.get(i);
            
            historyTable.addCell(new PdfPCell(new Phrase(mouvement.getFormattedDate(), cellFont)));
            
            PdfPCell typeCell = new PdfPCell(new Phrase(mouvement.getType(), cellFont));
            typeCell.setBackgroundColor("APPROVISIONNEMENT".equals(mouvement.getType()) ? 
                                       new BaseColor(200, 255, 200) : new BaseColor(255, 200, 200));
            historyTable.addCell(typeCell);
            
            historyTable.addCell(new PdfPCell(new Phrase(mouvement.getQuantiteWithSign(), cellFont)));
            historyTable.addCell(new PdfPCell(new Phrase(
                mouvement.getQuantiteAvant() + " → " + mouvement.getQuantiteApres(), cellFont)));
            historyTable.addCell(new PdfPCell(new Phrase(mouvement.getDescription(), cellFont)));
        }
        
        document.add(historyTable);
        
        if (mouvements.size() > 20) {
            document.add(new Paragraph("... et " + (mouvements.size() - 20) + " autres mouvements", 
                        new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, BaseColor.GRAY)));
        }
        
        document.add(new Paragraph(" "));
    }
    
    /**
     * Ajoute un résumé d'évolution
     */
    private static void addEvolutionSummary(Document document, EquipmentCard equipmentCard) throws Exception {
        Font sectionFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        document.add(new Paragraph("Analyse d'évolution", sectionFont));
        
        Font analysisFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
        
        int evolution = equipmentCard.getStock().getQuantite() - equipmentCard.getQuantiteInitiale();
        String evolutionText;
        BaseColor evolutionColor;
        
        if (evolution > 0) {
            evolutionText = "↗️ Stock en augmentation (+" + evolution + " unités)";
            evolutionColor = BaseColor.GREEN;
        } else if (evolution < 0) {
            evolutionText = "↘️ Stock en diminution (" + evolution + " unités)";
            evolutionColor = BaseColor.RED;
        } else {
            evolutionText = "➡️ Stock stable (aucune variation nette)";
            evolutionColor = BaseColor.BLUE;
        }
        
        Paragraph evolutionParagraph = new Paragraph(evolutionText, 
                                                    new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, evolutionColor));
        document.add(evolutionParagraph);
        
        // Recommandations basées sur l'analyse
        if ("VIOLET".equals(equipmentCard.getStock().getStatut()) || "ROUGE".equals(equipmentCard.getStock().getStatut())) {
            document.add(new Paragraph("⚠️ Recommandation: Réapprovisionnement urgent recommandé", 
                        new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.RED)));
        } else if (equipmentCard.getMouvements().isEmpty()) {
            document.add(new Paragraph("ℹ️ Information: Aucune activité récente sur cet équipement", 
                        new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.BLUE)));
        }
    }
    
    /**
     * Ajoute une ligne d'information au tableau
     */
    private static void addInfoRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(PdfPCell.NO_BORDER);
        labelCell.setPadding(5);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(PdfPCell.NO_BORDER);
        valueCell.setPadding(5);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
    
    // Méthodes pour Excel (feuilles de calcul)
    
    /**
     * Crée la feuille de dashboard
     */
    private static void createDashboardSheet(Workbook workbook, Connection connection) throws SQLException {
        Sheet sheet = workbook.createSheet("Dashboard");
        
        // Styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        
        int rowNum = 0;
        
        // Titre
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("TABLEAU DE BORD LOGISTIQUE");
        titleCell.setCellStyle(headerStyle);
        
        rowNum++; // Ligne vide
        
        // Récupérer les données du dashboard
        String sql = "SELECT * FROM logistic_dashboard LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                // Créer les lignes de données
                addDashboardRow(sheet, rowNum++, "Total équipements:", rs.getInt("total_equipements"), headerStyle, dataStyle);
                addDashboardRow(sheet, rowNum++, "Stocks critiques:", rs.getInt("stocks_critiques"), headerStyle, dataStyle);
                addDashboardRow(sheet, rowNum++, "Maintenances urgentes:", rs.getInt("maintenances_urgentes"), headerStyle, dataStyle);
                rowNum++; // Ligne vide
                addDashboardRow(sheet, rowNum++, "Mouvements (30j):", rs.getInt("mouvements_30j"), headerStyle, dataStyle);
                addDashboardRow(sheet, rowNum++, "- Approvisionnements:", rs.getInt("approvisionnements_30j"), headerStyle, dataStyle);
                addDashboardRow(sheet, rowNum++, "- Retraits:", rs.getInt("retraits_30j"), headerStyle, dataStyle);
            }
        }
        
        // Ajuster la largeur des colonnes
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }
    
    /**
     * Ajoute une ligne au dashboard
     */
    private static void addDashboardRow(Sheet sheet, int rowNum, String label, int value, CellStyle headerStyle, CellStyle dataStyle) {
        Row row = sheet.createRow(rowNum);
        
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(headerStyle);
        
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(dataStyle);
    }
    
    /**
     * Crée la feuille des détails de stock
     */
    private static void createStockDetailsSheet(Workbook workbook, List<Stock> stocks) {
        Sheet sheet = workbook.createSheet("Stocks Détaillés");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        
        // En-têtes
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Désignation", "Quantité", "État", "Seuil Critique", "Statut", "Date Création", "Description"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Données
        int rowNum = 1;
        for (Stock stock : stocks) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(stock.getDesignation());
            row.createCell(1).setCellValue(stock.getQuantite());
            row.createCell(2).setCellValue(stock.getEtat());
            row.createCell(3).setCellValue(stock.getValeurCritique());
            row.createCell(4).setCellValue(getStatusText(stock.getStatut()));
            row.createCell(5).setCellValue(stock.getDateCreation());
            row.createCell(6).setCellValue(stock.getDescription());
            
            // Appliquer le style
            for (int i = 0; i < 7; i++) {
                row.getCell(i).setCellStyle(dataStyle);
            }
        }
        
        // Ajuster les colonnes
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    /**
     * Crée la feuille des maintenances
     */
    private static void createMaintenanceSheet(Workbook workbook, List<Maintenance> maintenances) {
        Sheet sheet = workbook.createSheet("Maintenances");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        
        // En-têtes
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Désignation", "Date", "Type", "Description", "Effectuée", "Statut"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Données
        int rowNum = 1;
        for (Maintenance maintenance : maintenances) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(maintenance.getDesignation());
            row.createCell(1).setCellValue(maintenance.getDate());
            row.createCell(2).setCellValue(maintenance.getType());
            row.createCell(3).setCellValue(maintenance.getDescription());
            row.createCell(4).setCellValue(maintenance.isEffectuee() ? "Oui" : "Non");
            row.createCell(5).setCellValue(maintenance.isEffectuee() ? "Effectuée" : getMaintenanceStatusText(maintenance.getStatut()));
            
            // Appliquer le style
            for (int i = 0; i < 6; i++) {
                row.getCell(i).setCellStyle(dataStyle);
            }
        }
        
        // Ajuster les colonnes
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    /**
     * Crée la feuille des mouvements
     */
    private static void createMovementsSheet(Workbook workbook, Connection connection) throws SQLException {
        Sheet sheet = workbook.createSheet("Mouvements Récents");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        
        // En-têtes
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Date", "Équipement", "Type", "Quantité", "Avant", "Après", "Utilisateur", "Description"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Récupérer les données
        String sql = """
            SELECT sm.*, s.designation
            FROM stock_movements sm
            JOIN stocks s ON sm.stock_id = s.id
            ORDER BY sm.date_movement DESC
            LIMIT 100""";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            int rowNum = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rs.getTimestamp("date_movement").toString());
                row.createCell(1).setCellValue(rs.getString("designation"));
                row.createCell(2).setCellValue(rs.getString("type"));
                String quantity = ("APPROVISIONNEMENT".equals(rs.getString("type")) ? "+" : "-") + rs.getInt("quantite");
                row.createCell(3).setCellValue(quantity);
                row.createCell(4).setCellValue(rs.getInt("quantite_avant"));
                row.createCell(5).setCellValue(rs.getInt("quantite_apres"));
                row.createCell(6).setCellValue(rs.getString("utilisateur"));
                row.createCell(7).setCellValue(rs.getString("description"));
                
                // Appliquer le style
                for (int i = 0; i < 8; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }
        }
        
        // Ajuster les colonnes
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    /**
     * Crée la feuille des alertes
     */
    private static void createAlertsSheet(Workbook workbook, List<Stock> stocks, List<Maintenance> maintenances) {
        Sheet sheet = workbook.createSheet("Alertes");
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle alertStyle = createAlertStyle(workbook);
        
        // En-têtes
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Type");
        headerRow.createCell(1).setCellValue("Équipement/Maintenance");
        headerRow.createCell(2).setCellValue("Détails");
        headerRow.createCell(3).setCellValue("Priorité");
        
        for (int i = 0; i < 4; i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }
        
        int rowNum = 1;
        
        // Alertes stocks
        for (Stock stock : stocks) {
            if ("VIOLET".equals(stock.getStatut()) || "ROUGE".equals(stock.getStatut())) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue("STOCK");
                row.createCell(1).setCellValue(stock.getDesignation());
                row.createCell(2).setCellValue("Quantité: " + stock.getQuantite() + " (Seuil: " + stock.getValeurCritique() + ")");
                row.createCell(3).setCellValue("VIOLET".equals(stock.getStatut()) ? "CRITIQUE" : "URGENT");
                
                for (int i = 0; i < 4; i++) {
                    row.getCell(i).setCellStyle(alertStyle);
                }
            }
        }
        
        // Alertes maintenances
        for (Maintenance maintenance : maintenances) {
            if (!maintenance.isEffectuee() && ("VIOLET".equals(maintenance.getStatut()) || "ROUGE".equals(maintenance.getStatut()))) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue("MAINTENANCE");
                row.createCell(1).setCellValue(maintenance.getDesignation());
                row.createCell(2).setCellValue("Date prévue: " + maintenance.getDate());
                row.createCell(3).setCellValue("VIOLET".equals(maintenance.getStatut()) ? "DÉPASSÉE" : "URGENT");
                
                for (int i = 0; i < 4; i++) {
                    row.getCell(i).setCellStyle(alertStyle);
                }
            }
        }
        
        // Ajuster les colonnes
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    // Styles Excel
    
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = (Font) workbook.createFont();
        ((org.apache.poi.ss.usermodel.Font) font).setBold(true);
        ((org.apache.poi.ss.usermodel.Font) font).setFontHeightInPoints((short) 12);
        style.setFont((org.apache.poi.ss.usermodel.Font) font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }
    
    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }
    
    private static CellStyle createAlertStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    // Méthodes utilitaires
    
    private static String getStatusText(String statut) {
        switch (statut) {
            case "VERT": return "Normal";
            case "ORANGE": return "Attention";
            case "ROUGE": return "Faible";
            case "VIOLET": return "Critique";
            default: return "Inconnu";
        }
    }
    
    private static String getMaintenanceStatusText(String statut) {
        switch (statut) {
            case "VERT": return "Programmée";
            case "ORANGE": return "Proche";
            case "ROUGE": return "Urgente";
            case "VIOLET": return "En retard";
            case "BLEU": return "Effectuée";
            default: return "Inconnu";
        }
    }
    
    private static BaseColor getStatusColor(String statut) {
        switch (statut) {
            case "VERT": return BaseColor.GREEN;
            case "ORANGE": return BaseColor.ORANGE;
            case "ROUGE": return BaseColor.RED;
            case "VIOLET": return BaseColor.MAGENTA;
            case "BLEU": return BaseColor.BLUE;
            default: return BaseColor.WHITE;
        }
    }
}