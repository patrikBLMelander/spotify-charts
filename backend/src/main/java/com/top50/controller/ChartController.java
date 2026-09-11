package com.top50.controller;

import com.top50.dto.ChartEntryDto;
import com.top50.dto.TrackDto;
import com.top50.dto.TrackHistoryDto;
import com.top50.service.JsonChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChartController {
    private final JsonChartService jsonChartService;

    @GetMapping("/tracks")
    public ResponseEntity<List<TrackDto>> getAllTracks(
            @RequestParam(required = false, defaultValue = "Walter") String user) {
        return ResponseEntity.ok(jsonChartService.getAllTracks(user));
    }

    @GetMapping("/tracks/{trackId}/history")
    public ResponseEntity<TrackHistoryDto> getTrackHistory(
            @PathVariable String trackId,
            @RequestParam(required = false, defaultValue = "Walter") String user) {
        try {
            return ResponseEntity.ok(jsonChartService.getTrackHistory(trackId, user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/chart/{user}")
    public ResponseEntity<List<ChartEntryDto>> getChartByUserAndWeek(
            @PathVariable String user,
            @RequestParam String week) {
        return ResponseEntity.ok(jsonChartService.getChartByWeek(week, user));
    }
    
    @GetMapping("/weeks/{user}")
    public ResponseEntity<List<String>> getAvailableWeeks(@PathVariable String user) {
        return ResponseEntity.ok(jsonChartService.getAllWeeks(user));
    }

    @GetMapping("/chart/{user}/dropped")
    public ResponseEntity<List<ChartEntryDto>> getDroppedTracks(
            @PathVariable String user,
            @RequestParam String week) {
        return ResponseEntity.ok(jsonChartService.getDroppedTracks(week, user));
    }

    // Download endpoint removed - can be re-implemented if needed

}
