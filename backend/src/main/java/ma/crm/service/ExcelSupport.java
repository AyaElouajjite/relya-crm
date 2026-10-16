package ma.crm.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** Export Excel (.xlsx) des listes, avec les mêmes colonnes que l'export CSV. */
final class ExcelSupport {

    private ExcelSupport() {
    }

    static <T> byte[] write(String sheetName, String[] headers, List<T> rows, Function<T, Object[]> mapper) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(bold);
            }
            int r = 1;
            for (T item : rows) {
                Row row = sheet.createRow(r++);
                Object[] values = mapper.apply(item);
                for (int i = 0; i < values.length; i++) {
                    Object v = values[i];
                    if (v == null) {
                        continue;
                    }
                    Cell c = row.createCell(i);
                    if (v instanceof BigDecimal d) {
                        c.setCellValue(d.doubleValue());
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                    } else {
                        // Une cellule texte n'est jamais évaluée comme formule par Excel.
                        c.setCellValue(v.toString());
                    }
                }
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
