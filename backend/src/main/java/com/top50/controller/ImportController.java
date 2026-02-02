package com.top50.controller;

import com.top50.dto.JsonImportRequest;
import com.top50.service.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/import")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class ImportController {
    private final DataImportService dataImportService;

    @PostMapping("/json")
    public ResponseEntity<?> importJsonData(
            @RequestBody JsonImportRequest request,
            @RequestParam(required = false, defaultValue = "Walter") String user) {
        try {
            log.info("Importing JSON data for user: {}, week: {}", user, request.getWeek());
            dataImportService.importJsonData(request, user);
            return ResponseEntity.ok().body(new ImportResponse("Data imported successfully", request.getWeek()));
        } catch (Exception e) {
            log.error("Error importing data for user: {}, week: {}", user, request.getWeek(), e);
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = "Unknown error occurred during import";
            }
            return ResponseEntity.status(500)
                    .body(new ImportResponse("Error importing data: " + errorMessage, null));
        }
    }
    
    private static class ImportResponse {
        private String message;
        private String week;
        
        public ImportResponse(String message, String week) {
            this.message = message;
            this.week = week;
        }
        
        public String getMessage() { return message; }
        public String getWeek() { return week; }
    }
}
