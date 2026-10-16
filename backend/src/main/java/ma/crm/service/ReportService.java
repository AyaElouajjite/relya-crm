package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.service.DashboardService.Dashboard;
import ma.crm.service.DashboardService.SellerPerformance;
import ma.crm.service.DashboardService.Slice;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Export des rapports du tableau de bord en PDF et en Excel (module 8). */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final DashboardService dashboard;
    private final PdfService pdf;
    private final AuditService audit;

    public byte[] pdf(LocalDate from, LocalDate to, Long ownerId) {
        Dashboard d = dashboard.build(from, to, ownerId);
        audit.log(AuditService.EXPORT, "Report", null, "PDF " + d.from() + " → " + d.to());
        return pdf.report("Rapport commercial du " + d.from().format(PdfService.DATE) + " au " + d.to().format(PdfService.DATE),
                tables(d));
    }

    public byte[] excel(LocalDate from, LocalDate to, Long ownerId) {
        Dashboard d = dashboard.build(from, to, ownerId);
        audit.log(AuditService.EXPORT, "Report", null, "Excel " + d.from() + " → " + d.to());
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            Sheet kpi = wb.createSheet("Indicateurs");
            int r = 0;
            for (String[] row : kpiRows(d)) {
                Row x = kpi.createRow(r++);
                x.createCell(0).setCellValue(row[0]);
                x.createCell(1).setCellValue(row[1]);
            }
            kpi.autoSizeColumn(0);
            kpi.autoSizeColumn(1);

            Sheet monthly = wb.createSheet("CA mensuel");
            writeHeader(monthly.createRow(0), header, "Mois", "CA réalisé HT", "Objectif");
            r = 1;
            for (var p : d.monthly()) {
                Row x = monthly.createRow(r++);
                x.createCell(0).setCellValue(Month.of(p.month()).getDisplayName(TextStyle.FULL, Locale.FRENCH));
                moneyCell(x, 1, p.revenue(), money);
                moneyCell(x, 2, p.objective(), money);
            }
            autosize(monthly, 3);

            Sheet sellers = wb.createSheet("Commerciaux");
            writeHeader(sellers.createRow(0), header, "Commercial", "CA HT", "Objectif", "Atteinte %", "Prévisionnel",
                    "Gagnées", "Perdues", "Taux de gain %", "Leads convertis", "Conversion %", "Activités");
            r = 1;
            for (SellerPerformance s : d.sellers()) {
                Row x = sellers.createRow(r++);
                x.createCell(0).setCellValue(s.name());
                moneyCell(x, 1, s.revenue(), money);
                moneyCell(x, 2, s.objective(), money);
                numCell(x, 3, s.objectiveRate());
                moneyCell(x, 4, s.forecast(), money);
                x.createCell(5).setCellValue(s.won());
                x.createCell(6).setCellValue(s.lost());
                numCell(x, 7, s.winRate());
                x.createCell(8).setCellValue(s.leadsConverted());
                numCell(x, 9, s.leadConversionRate());
                x.createCell(10).setCellValue(s.activitiesDone());
            }
            autosize(sellers, 11);

            sliceSheet(wb, header, money, "Pipeline", "Étape", d.pipelineByStage());
            sliceSheet(wb, header, money, "Motifs de perte", "Motif", d.lossReasons());
            sliceSheet(wb, header, money, "Sources de leads", "Source", d.leadSources());
            sliceSheet(wb, header, money, "Tickets", "Statut", d.ticketsByStatus());

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String[]> tables(Dashboard d) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Indicateurs clés"});
        rows.add(new String[]{"Indicateur", "Valeur"});
        rows.addAll(kpiRows(d));
        if (!d.sellers().isEmpty()) {
            rows.add(new String[]{"Performance par commercial"});
            rows.add(new String[]{"Commercial", "CA HT", "Objectif", "Atteinte", "Gagnées / perdues", "Conversion leads"});
            for (SellerPerformance s : d.sellers()) {
                rows.add(new String[]{s.name(), PdfService.money(s.revenue()), PdfService.money(s.objective()),
                        pct(s.objectiveRate()), s.won() + " / " + s.lost(), pct(s.leadConversionRate())});
            }
        }
        rows.add(new String[]{"Chiffre d'affaires mensuel " + d.to().getYear()});
        rows.add(new String[]{"Mois", "CA réalisé HT", "Objectif"});
        for (var p : d.monthly()) {
            rows.add(new String[]{Month.of(p.month()).getDisplayName(TextStyle.FULL, Locale.FRENCH),
                    PdfService.money(p.revenue()), PdfService.money(p.objective())});
        }
        addSlices(rows, "Pipeline par étape", "Étape", d.pipelineByStage(), true);
        addSlices(rows, "Motifs de perte", "Motif", d.lossReasons(), true);
        addSlices(rows, "Sources des leads", "Source", d.leadSources(), false);
        addSlices(rows, "Tickets par statut", "Statut", d.ticketsByStatus(), false);
        return rows;
    }

    private static List<String[]> kpiRows(Dashboard d) {
        var k = d.kpis();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Chiffre d'affaires réalisé (HT)", PdfService.money(k.revenue())});
        rows.add(new String[]{"Objectif de la période", PdfService.money(k.objective())});
        rows.add(new String[]{"Taux d'atteinte", pct(k.objectiveRate())});
        rows.add(new String[]{"Chiffre d'affaires prévisionnel (pondéré)", PdfService.money(k.forecast())});
        rows.add(new String[]{"Pipeline ouvert (non pondéré)", PdfService.money(k.pipelineTotal())});
        rows.add(new String[]{"Opportunités ouvertes", String.valueOf(k.openOpportunities())});
        rows.add(new String[]{"Opportunités gagnées / perdues", k.won() + " / " + k.lost()});
        rows.add(new String[]{"Taux de gain", pct(k.winRate())});
        rows.add(new String[]{"Leads créés / convertis", k.leadsCreated() + " / " + k.leadsConverted()});
        rows.add(new String[]{"Taux de conversion lead → client", pct(k.leadConversionRate())});
        rows.add(new String[]{"Devis envoyés", String.valueOf(k.quotesSent())});
        rows.add(new String[]{"Taux d'acceptation des devis", pct(k.quoteAcceptanceRate())});
        rows.add(new String[]{"Tickets ouverts (dont en retard)", k.openTickets() + " (" + k.overdueTickets() + ")"});
        rows.add(new String[]{"Délai moyen de résolution", k.avgResolutionHours() == null ? "—" : k.avgResolutionHours() + " h"});
        rows.add(new String[]{"Activités réalisées", String.valueOf(k.activitiesDone())});
        return rows;
    }

    private static void addSlices(List<String[]> rows, String title, String label, List<Slice> slices, boolean withAmount) {
        if (slices.isEmpty()) {
            return;
        }
        rows.add(new String[]{title});
        rows.add(withAmount ? new String[]{label, "Nombre", "Montant"} : new String[]{label, "Nombre"});
        for (Slice s : slices) {
            rows.add(withAmount ? new String[]{s.key(), String.valueOf(s.count()), PdfService.money(s.amount())}
                    : new String[]{s.key(), String.valueOf(s.count())});
        }
    }

    private static void sliceSheet(Workbook wb, CellStyle header, CellStyle money, String name, String label, List<Slice> slices) {
        Sheet sheet = wb.createSheet(name);
        writeHeader(sheet.createRow(0), header, label, "Nombre", "Montant");
        int r = 1;
        for (Slice s : slices) {
            Row x = sheet.createRow(r++);
            x.createCell(0).setCellValue(s.key());
            x.createCell(1).setCellValue(s.count());
            moneyCell(x, 2, s.amount(), money);
        }
        autosize(sheet, 3);
    }

    private static void writeHeader(Row row, CellStyle style, String... titles) {
        for (int i = 0; i < titles.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(titles[i]);
            c.setCellStyle(style);
        }
    }

    private static void moneyCell(Row row, int col, BigDecimal value, CellStyle style) {
        if (value != null) {
            Cell c = row.createCell(col);
            c.setCellValue(value.doubleValue());
            c.setCellStyle(style);
        }
    }

    private static void numCell(Row row, int col, Double value) {
        if (value != null) {
            row.createCell(col).setCellValue(value);
        }
    }

    private static void autosize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static String pct(Double value) {
        return value == null ? "—" : value + " %";
    }
}
