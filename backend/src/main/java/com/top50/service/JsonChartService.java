package com.top50.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.top50.dto.ChartEntryDto;
import com.top50.dto.JsonImportRequest;
import com.top50.dto.TrackDto;
import com.top50.dto.TrackHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for reading chart data directly from JSON files
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JsonChartService {
    private final ObjectMapper objectMapper;
    
    @Value("${data.directory:../data}")
    private String dataDirectory;
    
    private static final Pattern WEEK_PATTERN = Pattern.compile("\\d{4}-W\\d{2}\\.json");
    
    private Path getDataDirectory() {
        Path path = Paths.get(dataDirectory);
        if (Files.exists(path) && Files.isDirectory(path)) {
            return path.toAbsolutePath();
        }
        
        Path currentDir = Paths.get("").toAbsolutePath();
        if (currentDir.getFileName().toString().equals("backend")) {
            Path parentData = currentDir.getParent().resolve("data");
            if (Files.exists(parentData) && Files.isDirectory(parentData)) {
                return parentData;
            }
        }
        
        Path relativeData = currentDir.resolve("data");
        if (Files.exists(relativeData) && Files.isDirectory(relativeData)) {
            return relativeData;
        }
        
        if (currentDir.getParent() != null) {
            Path parentData = currentDir.getParent().resolve("data");
            if (Files.exists(parentData) && Files.isDirectory(parentData)) {
                return parentData;
            }
        }
        
        log.warn("Could not find data directory. Using configured path: {}", path);
        return path.toAbsolutePath();
    }
    
    /**
     * Get all available weeks for a user
     */
    public List<String> getAllWeeks(String user) {
        Path userDir = getDataDirectory().resolve(user);
        if (!Files.exists(userDir) || !Files.isDirectory(userDir)) {
            return Collections.emptyList();
        }
        
        List<String> weeks = new ArrayList<>();
        try (Stream<Path> paths = Files.list(userDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> {
                     String filename = path.getFileName().toString();
                     return filename.endsWith(".json") && WEEK_PATTERN.matcher(filename).matches();
                 })
                 .map(path -> path.getFileName().toString().replace(".json", ""))
                 .sorted()
                 .forEach(weeks::add);
        } catch (IOException e) {
            log.error("Error reading directory {}: {}", userDir, e.getMessage());
        }
        return weeks;
    }
    
    /**
     * Get chart data for a specific week and user
     */
    public List<ChartEntryDto> getChartByWeek(String week, String user) {
        Path filePath = getDataDirectory().resolve(user).resolve(week + ".json");
        if (!Files.exists(filePath)) {
            log.warn("File not found: {}", filePath);
            return Collections.emptyList();
        }
        
        try {
            String content = Files.readString(filePath);
            JsonImportRequest request = objectMapper.readValue(content, JsonImportRequest.class);
            
            // Get previous week for status calculation
            String previousWeek = getPreviousWeek(week, user);
            Map<String, Integer> previousPositions = new HashMap<>();
            if (previousWeek != null) {
                List<ChartEntryDto> previousEntries = getChartByWeek(previousWeek, user);
                for (ChartEntryDto entry : previousEntries) {
                    previousPositions.put(entry.getTrack().getId(), entry.getPosition());
                }
            }
            
            // Convert to ChartEntryDto
            return request.getEntries().stream()
                    .filter(entry -> entry.getTrackId() != null && !entry.getTrackId().trim().isEmpty())
                    .filter(entry -> entry.getTitle() != null && !entry.getTitle().trim().isEmpty() && !entry.getTitle().trim().equals("—"))
                    .filter(entry -> entry.getPlacement() != null)
                    .sorted(Comparator.comparing(JsonImportRequest.ChartEntry::getPlacement))
                    .map(entry -> {
                        TrackDto trackDto = new TrackDto(
                                entry.getTrackId(),
                                entry.getTitle().trim(),
                                entry.getArtists() != null ? entry.getArtists() : Collections.emptyList(),
                                entry.getSpotifyUrl(),
                                entry.getImageUrl() // Read imageUrl from JSON
                        );
                        
                        Integer previousPosition = previousPositions.get(entry.getTrackId());
                        
                        return new ChartEntryDto(
                                request.getWeek(),
                                entry.getPlacement(),
                                trackDto,
                                previousPosition
                        );
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error reading file {}: {}", filePath, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get all tracks across all weeks for a user
     */
    public List<TrackDto> getAllTracks(String user) {
        List<String> weeks = getAllWeeks(user);
        Set<String> seenTrackIds = new HashSet<>();
        List<TrackDto> allTracks = new ArrayList<>();
        
        for (String week : weeks) {
            List<ChartEntryDto> entries = getChartByWeek(week, user);
            for (ChartEntryDto entry : entries) {
                if (!seenTrackIds.contains(entry.getTrack().getId())) {
                    seenTrackIds.add(entry.getTrack().getId());
                    allTracks.add(entry.getTrack());
                }
            }
        }
        
        return allTracks;
    }
    
    /**
     * Get track history across all weeks for a user
     */
    public TrackHistoryDto getTrackHistory(String trackId, String user) {
        List<String> weeks = getAllWeeks(user);
        List<TrackHistoryDto.PositionPoint> history = new ArrayList<>();
        TrackDto trackDto = null;
        
        for (String week : weeks) {
            List<ChartEntryDto> entries = getChartByWeek(week, user);
            for (ChartEntryDto entry : entries) {
                if (entry.getTrack().getId().equals(trackId)) {
                    if (trackDto == null) {
                        trackDto = entry.getTrack();
                    }
                    history.add(new TrackHistoryDto.PositionPoint(week, entry.getPosition()));
                    break;
                }
            }
        }
        
        if (trackDto == null) {
            throw new IllegalArgumentException("Track not found: " + trackId);
        }
        
        return new TrackHistoryDto(trackDto, history);
    }
    
    /**
     * Get tracks that were in the previous week but not in the current week
     */
    public List<ChartEntryDto> getDroppedTracks(String week, String user) {
        String previousWeek = getPreviousWeek(week, user);
        if (previousWeek == null) {
            return Collections.emptyList();
        }
        
        // Get tracks from previous week
        List<ChartEntryDto> previousEntries = getChartByWeek(previousWeek, user);
        
        // Get tracks from current week
        List<ChartEntryDto> currentEntries = getChartByWeek(week, user);
        Set<String> currentTrackIds = currentEntries.stream()
                .map(entry -> entry.getTrack().getId())
                .collect(Collectors.toSet());
        
        // Find tracks that were in previous week but not in current week
        return previousEntries.stream()
                .filter(entry -> !currentTrackIds.contains(entry.getTrack().getId()))
                .sorted(Comparator.comparing(ChartEntryDto::getPosition))
                .collect(Collectors.toList());
    }
    
    /**
     * Get chart data as JSON string for download
     */
    public String getChartDataAsJson(String week, String user) {
        try {
            Path filePath = getDataDirectory().resolve(user).resolve(week + ".json");
            if (Files.exists(filePath)) {
                return Files.readString(filePath);
            }
            return "{}";
        } catch (IOException e) {
            log.error("Error reading file for download: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Get all chart data as JSON string for download
     */
    public String getAllChartDataAsJson(String user) {
        try {
            List<String> weeks = getAllWeeks(user);
            Map<String, Object> allData = new LinkedHashMap<>();
            allData.put("user", user);
            allData.put("weeks", new ArrayList<>());
            
            for (String week : weeks) {
                Path filePath = getDataDirectory().resolve(user).resolve(week + ".json");
                if (Files.exists(filePath)) {
                    String content = Files.readString(filePath);
                    JsonNode weekData = objectMapper.readTree(content);
                    ((List<Object>) allData.get("weeks")).add(weekData);
                }
            }
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(allData);
        } catch (IOException e) {
            log.error("Error generating all data JSON: {}", e.getMessage());
            return "{}";
        }
    }
    
    private String getPreviousWeek(String currentWeek, String user) {
        List<String> allWeeks = getAllWeeks(user);
        int currentIndex = allWeeks.indexOf(currentWeek);
        if (currentIndex > 0) {
            return allWeeks.get(currentIndex - 1);
        }
        return null;
    }
}
