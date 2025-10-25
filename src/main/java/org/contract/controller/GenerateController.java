package org.contract.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.contract.service.ExcelWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api")
public class GenerateController {

    @Autowired
    private ExcelWordService excelWordService;

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestParam("file") MultipartFile file,
                                           @RequestParam("templateCode") String templateCode) throws JsonProcessingException {
        try {
            // Nhận list các file Word đã tạo
            List<File> generatedFiles = excelWordService.processExcelByBookmark(file.getInputStream(), templateCode);

            if (generatedFiles.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Excel file has no data rows".getBytes());
            }

            // Nếu có nhiều file -> nén thành zip
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (File f : generatedFiles) {
                    zos.putNextEntry(new ZipEntry(f.getName()));
                    Files.copy(f.toPath(), zos);
                    zos.closeEntry();
                }
            }

            byte[] zipBytes = baos.toByteArray();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=generated_docs.zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zipBytes);

        } catch (Exception e) {
            // Chuyển stack trace thành String
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Map<String, String> error = Map.of(
                    "message", e.getMessage(),
                    "stackTrace", sw.toString()
            );
            String json = new ObjectMapper().writeValueAsString(error);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
