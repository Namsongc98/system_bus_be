package com.ticket_system.manage_revenue_ticket.service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class FileService {


    public ByteArrayOutputStream exportTicketSummaryToExcel(List<Map<String, Object>> data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Ticket Summary");
            // Tạo header
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Bus ID","Biển số xe","Nhân viên thu vé","Lái xe", "Ticket Count", "Total Revenue"}; // Ví dụ
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            // Ghi dữ liệu
            int rowNum = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(String.valueOf(rowData.get("bus_id")));
                row.createCell(1).setCellValue(String.valueOf(rowData.get("bienSoXe")));
                row.createCell(2).setCellValue(String.valueOf(rowData.get("nhanVienThuVe")));
                row.createCell(3).setCellValue(String.valueOf(rowData.get("laiXe")));
                row.createCell(4).setCellValue(String.valueOf(rowData.get("tongSoVe")));
                row.createCell(5).setCellValue(String.valueOf(rowData.get("tongTienVe")));
            }
            // Ghi workbook vào output stream
            workbook.write(outputStream);
            return outputStream;
        }
    }
}
