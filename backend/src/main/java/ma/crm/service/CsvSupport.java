package ma.crm.service;

import ma.crm.exception.BusinessException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lecture / écriture CSV compatibles Excel (séparateur « ; », UTF-8 avec BOM).
 * Les tags sont sérialisés dans une seule colonne, séparés par « | ».
 */
final class CsvSupport {

    private static final char BOM = '﻿';

    private CsvSupport() {
    }

    static <T> byte[] write(String[] headers, List<T> rows, Function<T, Object[]> mapper) {
        try (var out = new ByteArrayOutputStream();
             var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(BOM);
            CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(headers).build();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (T row : rows) {
                    printer.printRecord(Arrays.stream(mapper.apply(row)).map(CsvSupport::sanitize).toArray());
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Neutralise l'injection de formules (CSV injection) : une cellule commençant par =, +, -, @
     * ou une tabulation serait interprétée comme une formule par Excel. Les numéros de téléphone
     * (« +212 6 61 ... ») restent intacts.
     */
    static Object sanitize(Object value) {
        if (value instanceof String s && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0
                && !PHONE_OR_NUMBER.matcher(s).matches()) {
            return "'" + s;
        }
        return value;
    }

    private static final java.util.regex.Pattern PHONE_OR_NUMBER = java.util.regex.Pattern.compile("^[+-][\\d\\s().]+$");

    static List<CSVRecord> read(InputStream input) {
        try {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == BOM) {
                content = content.substring(1);
            }
            String firstLine = content.lines().findFirst().orElse("");
            char delimiter = firstLine.chars().filter(c -> c == ';').count()
                    >= firstLine.chars().filter(c -> c == ',').count() ? ';' : ',';
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setDelimiter(delimiter).setHeader().setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true).setTrim(true).setIgnoreEmptyLines(true).build();
            try (CSVParser parser = CSVParser.parse(content, format)) {
                return parser.getRecords();
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new BusinessException("Fichier CSV illisible : " + e.getMessage());
        }
    }

    static String get(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }
        String value = record.get(column);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static String joinTags(Set<String> tags) {
        return String.join("|", new TreeSet<>(tags));
    }

    static Set<String> splitTags(String value) {
        if (!StringUtils.hasText(value)) {
            return new HashSet<>();
        }
        return Arrays.stream(value.split("\\|")).map(String::trim).filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
