package com.intech.ai.service;

import com.intech.ai.modal.Ticket;
import com.intech.ai.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketExportService {

    private final TicketRepository ticketRepository;

    public String exportTicketStatusExcel(String userId) throws Exception {

        List<Ticket> tickets =  ticketRepository.findAll()
                .stream()
                .filter(t -> userId.equals(t.getEmployeeId()))
                .toList();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Ticket Status");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Ticket ID");
        header.createCell(1).setCellValue("Category");
        header.createCell(2).setCellValue("Status");
        header.createCell(3).setCellValue("Created At");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        int rowIdx = 1;
        for (Ticket t : tickets) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(String.valueOf(t.getId()));
            row.createCell(1).setCellValue(t.getCategory());
            row.createCell(2).setCellValue(t.getStatus());
            row.createCell(3).setCellValue(t.getCreatedAt() == null ? "" : t.getCreatedAt().format(fmt));
        }

        for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

        Files.createDirectories(Path.of("uploads"));

        String fileName = "ticket-status-" + userId + "-" + UUID.randomUUID() + ".xlsx";
        Path filePath = Path.of("uploads", fileName);

        try (FileOutputStream out = new FileOutputStream(filePath.toFile())) {
            workbook.write(out);
        }
        workbook.close();

        return fileName;
    }
}
