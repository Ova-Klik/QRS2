package com.techschool.attendance.service;

import com.techschool.attendance.exception.AppException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExportService {

    /**
     * Builds a downloadable response in CSV or XLSX format from uniform tabular data.
     *
     * @param headers column headers
     * @param rows    data rows (must match headers arity)
     * @param format  "csv" or "xlsx"
     * @param baseName file name without extension
     */
    public ResponseEntity<byte[]> export(List<String> headers, List<List<Object>> rows, String format, String baseName) {
        String ext = "xlsx".equalsIgnoreCase(format) ? "xlsx" : "csv";
        byte[] bytes = "xlsx".equalsIgnoreCase(format) ? toXlsx(headers, rows) : toCsv(headers, rows);
        if (bytes == null) throw AppException.badRequest("No data to export");

        String filename = sanitizeFilename(baseName) + "." + ext;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType("xlsx".equalsIgnoreCase(format)
                        ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        : MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    public byte[] toCsv(List<String> headers, List<List<Object>> rows) {
        if (rows.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(headers.stream().map(this::csvEscape).collect(Collectors.joining(","))).append("\r\n");
        for (List<Object> row : rows) {
            sb.append(row.stream().map(v -> csvEscape(v == null ? "" : v.toString()))
                    .collect(Collectors.joining(","))).append("\r\n");
        }
        // BOM so Excel opens UTF-8 CSVs correctly
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return out;
    }

    public byte[] toXlsx(List<String> headers, List<List<Object>> rows) {
        if (rows.isEmpty()) return null;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Attendance");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.LEFT);

            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(headerStyle);
            }

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));

            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> data = rows.get(r);
                for (int c = 0; c < data.size() && c < headers.size(); c++) {
                    Cell cell = row.createCell(c);
                    Object v = data.get(c);
                    if (v == null) {
                        cell.setCellValue("");
                    } else if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else if (v instanceof java.time.LocalDate ld) {
                        cell.setCellValue(ld.toString());
                        cell.setCellStyle(dateStyle);
                    } else {
                        cell.setCellValue(v.toString());
                    }
                }
            }

            for (int c = 0; c < headers.size(); c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw AppException.badRequest("Failed to generate Excel file: " + e.getMessage());
        }
    }

    private String csvEscape(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "attendance_export";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
