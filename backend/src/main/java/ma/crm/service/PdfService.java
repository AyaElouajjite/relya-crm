package ma.crm.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Company;
import ma.crm.domain.Invoice;
import ma.crm.domain.Quote;
import ma.crm.domain.QuoteLine;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Génération des documents PDF : devis, factures et rapports. */
@Service
@RequiredArgsConstructor
public class PdfService {

    private static final Color BRAND = new Color(37, 73, 234);
    private static final Color LIGHT = new Color(241, 245, 249);
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SettingsService settings;

    public static String money(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.00", symbols).format(value == null ? BigDecimal.ZERO : value) + " MAD";
    }

    public byte[] quote(Quote q) {
        return document(doc -> {
            header(doc, "DEVIS", q.getNumber(), q.getDate(),
                    q.getValidUntil() == null ? null : "Valable jusqu'au " + q.getValidUntil().format(DATE));
            recipient(doc, q.getCompany(), q.getContact() == null ? null : q.getContact().getFullName());
            lines(doc, q.getLines());
            totals(doc, q.getTotalHT(), q.getTotalVAT(), q.getTotalTTC());
            if (StringUtils.hasText(q.getNotes())) {
                doc.add(new Paragraph("Remarques", H2));
                doc.add(new Paragraph(q.getNotes(), NORMAL));
            }
            doc.add(spacer(10));
            doc.add(new Paragraph("Conditions", H2));
            doc.add(new Paragraph(settings.get(SettingsService.QUOTE_CONDITIONS), NORMAL));
            doc.add(spacer(20));
            doc.add(new Paragraph("Bon pour accord (date, signature et cachet du client) :", NORMAL));
        });
    }

    public byte[] invoice(Invoice i) {
        Quote q = i.getQuote();
        return document(doc -> {
            header(doc, "FACTURE", i.getNumber(), i.getIssueDate(),
                    i.getDueDate() == null ? null : "Échéance : " + i.getDueDate().format(DATE));
            recipient(doc, i.getCompany(), q.getContact() == null ? null : q.getContact().getFullName());
            doc.add(new Paragraph("Référence devis : " + q.getNumber(), SMALL));
            doc.add(spacer(6));
            lines(doc, q.getLines());
            totals(doc, i.getTotalHT(), i.getTotalVAT(), i.getTotalTTC());
            doc.add(new Paragraph("Statut : " + switch (i.getStatus()) {
                case EMISE -> "à régler";
                case PAYEE -> "payée le " + (i.getPaidDate() == null ? "" : i.getPaidDate().format(DATE));
                case ANNULEE -> "annulée";
            }, BOLD));
        });
    }

    /** Rapport tabulaire générique (module 8). */
    public byte[] report(String title, List<String[]> sections) {
        return document(doc -> {
            doc.add(new Paragraph(settings.get(SettingsService.COMPANY_NAME), SMALL));
            doc.add(new Paragraph(title, TITLE));
            doc.add(new Paragraph("Généré le " + LocalDate.now().format(DATE), SMALL));
            doc.add(spacer(10));
            PdfPTable table = null;
            for (String[] row : sections) {
                if (row.length == 1) {
                    if (table != null) {
                        doc.add(table);
                    }
                    doc.add(spacer(8));
                    doc.add(new Paragraph(row[0], H2));
                    table = null;
                    continue;
                }
                if (table == null) {
                    table = new PdfPTable(row.length);
                    table.setWidthPercentage(100);
                    table.setSpacingBefore(4);
                    for (String h : row) {
                        table.addCell(headerCell(h));
                    }
                    continue;
                }
                for (int c = 0; c < row.length; c++) {
                    table.addCell(cell(row[c], c == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT));
                }
            }
            if (table != null) {
                doc.add(table);
            }
        });
    }

    // ---- Briques communes ----

    private interface Body {
        void write(Document doc) throws DocumentException;
    }

    private byte[] document(Body body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();
        try {
            body.write(doc);
            doc.add(spacer(20));
            doc.add(new Paragraph(footer(), SMALL));
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération PDF impossible", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private void header(Document doc, String type, String number, LocalDate date, String extra) {
        PdfPTable t = new PdfPTable(new float[]{3, 2});
        t.setWidthPercentage(100);
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Paragraph(settings.get(SettingsService.COMPANY_NAME), H2));
        left.addElement(new Paragraph(settings.get(SettingsService.COMPANY_ADDRESS), NORMAL));
        String contact = String.join("  ", nonBlank(settings.get(SettingsService.COMPANY_PHONE)),
                nonBlank(settings.get(SettingsService.COMPANY_EMAIL)));
        if (StringUtils.hasText(contact.trim())) {
            left.addElement(new Paragraph(contact.trim(), NORMAL));
        }
        String ice = settings.get(SettingsService.COMPANY_ICE);
        if (StringUtils.hasText(ice)) {
            left.addElement(new Paragraph("ICE : " + ice, NORMAL));
        }
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph(type, TITLE);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
        Paragraph meta = new Paragraph("N° " + number + "\nDate : " + date.format(DATE) + (extra == null ? "" : "\n" + extra), NORMAL);
        meta.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(meta);
        t.addCell(left);
        t.addCell(right);
        doc.add(t);
        doc.add(spacer(14));
    }

    private void recipient(Document doc, Company c, String contactName) {
        PdfPTable t = new PdfPTable(new float[]{1, 1});
        t.setWidthPercentage(100);
        PdfPCell empty = new PdfPCell();
        empty.setBorder(Rectangle.NO_BORDER);
        PdfPCell box = new PdfPCell();
        box.setBackgroundColor(LIGHT);
        box.setBorder(Rectangle.NO_BORDER);
        box.setPadding(8);
        box.addElement(new Paragraph("Client", SMALL));
        box.addElement(new Paragraph(c.getName(), H2));
        if (contactName != null) {
            box.addElement(new Paragraph("À l'attention de " + contactName, NORMAL));
        }
        if (StringUtils.hasText(c.getAddress()) || StringUtils.hasText(c.getCity())) {
            box.addElement(new Paragraph(String.join(" ", nonBlank(c.getAddress()), nonBlank(c.getCity())).trim(), NORMAL));
        }
        if (StringUtils.hasText(c.getIce())) {
            box.addElement(new Paragraph("ICE : " + c.getIce(), NORMAL));
        }
        t.addCell(empty);
        t.addCell(box);
        doc.add(t);
        doc.add(spacer(14));
    }

    private void lines(Document doc, List<QuoteLine> lines) {
        PdfPTable t = new PdfPTable(new float[]{6, 1.4f, 2, 1.2f, 1.2f, 2.2f});
        t.setWidthPercentage(100);
        for (String h : new String[]{"Désignation", "Qté", "PU HT", "Remise", "TVA", "Total HT"}) {
            t.addCell(headerCell(h));
        }
        for (QuoteLine l : lines) {
            t.addCell(cell(l.getDescription(), Element.ALIGN_LEFT));
            t.addCell(cell(l.getQuantity().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT));
            t.addCell(cell(money(l.getUnitPrice()), Element.ALIGN_RIGHT));
            t.addCell(cell(l.getDiscount().signum() == 0 ? "—" : l.getDiscount().stripTrailingZeros().toPlainString() + " %", Element.ALIGN_RIGHT));
            t.addCell(cell(l.getVatRate().stripTrailingZeros().toPlainString() + " %", Element.ALIGN_RIGHT));
            t.addCell(cell(money(l.getTotalHT()), Element.ALIGN_RIGHT));
        }
        doc.add(t);
    }

    private void totals(Document doc, BigDecimal ht, BigDecimal vat, BigDecimal ttc) {
        PdfPTable t = new PdfPTable(new float[]{3, 1.4f});
        t.setWidthPercentage(45);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setSpacingBefore(8);
        t.addCell(cell("Total HT", Element.ALIGN_LEFT));
        t.addCell(cell(money(ht), Element.ALIGN_RIGHT));
        t.addCell(cell("TVA", Element.ALIGN_LEFT));
        t.addCell(cell(money(vat), Element.ALIGN_RIGHT));
        PdfPCell label = new PdfPCell(new Phrase("Total TTC", HEADER));
        label.setBackgroundColor(BRAND);
        label.setPadding(5);
        PdfPCell value = new PdfPCell(new Phrase(money(ttc), HEADER));
        value.setBackgroundColor(BRAND);
        value.setPadding(5);
        value.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(label);
        t.addCell(value);
        doc.add(t);
        doc.add(spacer(12));
    }

    private static PdfPCell headerCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, HEADER));
        c.setBackgroundColor(BRAND);
        c.setPadding(5);
        c.setBorderColor(Color.WHITE);
        return c;
    }

    private static PdfPCell cell(String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, NORMAL));
        c.setPadding(5);
        c.setHorizontalAlignment(align);
        c.setBorderColor(new Color(226, 232, 240));
        return c;
    }

    private static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        p.setLeading(1);
        return p;
    }

    private String footer() {
        return settings.get(SettingsService.COMPANY_NAME) + " — document généré par le CRM";
    }

    private static String nonBlank(String s) {
        return s == null ? "" : s;
    }
}
