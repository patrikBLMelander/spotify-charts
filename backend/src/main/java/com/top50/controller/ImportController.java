package com.top50.controller;

import com.top50.dto.JsonImportRequest;
import com.top50.service.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {
    private final DataImportService dataImportService;
    
    private static final String WEEK_PATTERN = "\\d{4}-W\\d{2}";

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
    
    @DeleteMapping("/week")
    public ResponseEntity<?> deleteWeekData(
            @RequestParam String week,
            @RequestParam(required = false, defaultValue = "Walter") String user) {
        // Input validation
        if (week == null || week.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ImportResponse("Week parameter is required", null));
        }
        
        if (!week.matches(WEEK_PATTERN)) {
            return ResponseEntity.badRequest()
                    .body(new ImportResponse("Invalid week format. Expected YYYY-Www (e.g., 2026-W05)", null));
        }
        
        if (user == null || user.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ImportResponse("User parameter is required", null));
        }
        
        try {
            log.info("Deleting chart entries for user: {}, week: {}", user, week);
            dataImportService.deleteChartEntriesForWeek(week, user);
            return ResponseEntity.ok().body(new ImportResponse("Chart entries deleted successfully", week));
        } catch (Exception e) {
            // Exceptions are now handled by GlobalExceptionHandler
            // This catch is for any unexpected exceptions
            log.error("Unexpected error deleting chart entries for user: {}, week: {}", user, week, e);
            throw e; // Re-throw to let GlobalExceptionHandler handle it
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
