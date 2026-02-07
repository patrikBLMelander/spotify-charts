package com.top50.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.top50.entity.*;
import com.top50.exception.*;
import com.top50.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class DataImportService implements CommandLineRunner {
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final TrackRepository trackRepository;
    private final ArtistRepository artistRepository;
    private final TrackArtistRepository trackArtistRepository;
    private final WeekService weekService;
    private final ChartEntryRepository chartEntryRepository;
    
    @Value("${data.directory:../data}")
    private String dataDirectory;
    
    private static final Pattern WEEK_PATTERN = Pattern.compile("(\\d{4}-W\\d{2})\\.json");
    
    @Override
    public void run(String... args) {
        // Check if import should run (system property or environment variable)
        String importFlag = System.getProperty("import.data", System.getenv("IMPORT_DATA"));
        
        // Check if database is empty (no chart entries = no data imported)
        boolean isDatabaseEmpty = chartEntryRepository.count() == 0;
        
        // "auto" means: import if no chart entries exist, otherwise skip
        // "true" means: always import
        // "false" or unset means: never import (unless database is empty)
        boolean shouldImport = false;
        if ("auto".equalsIgnoreCase(importFlag) || importFlag == null) {
            shouldImport = isDatabaseEmpty;
        } else {
            shouldImport = Boolean.parseBoolean(importFlag);
        }
        
        if (!shouldImport) {
            if (isDatabaseEmpty) {
                log.warn("Database is empty but import is disabled. Set IMPORT_DATA=true or IMPORT_DATA=auto to import data.");
            } else {
                log.info("Data import skipped. Database already contains data.");
            }
            return;
        }
        
        if (isDatabaseEmpty) {
            log.info("Database is empty. Starting automatic data import from: {}", dataDirectory);
        } else {
            log.info("Starting data import from: {} (forced via IMPORT_DATA=true)", dataDirectory);
        }
        
        try {
            Path dataPath = getDataDirectory();
            if (!Files.exists(dataPath)) {
                log.warn("Data directory not found: {}", dataPath);
                return;
            }
            
            // Import Walter and Signe
            importUserData("Walter", dataPath.resolve("Walter"));
            importUserData("Signe", dataPath.resolve("Signe"));
            
            log.info("Data import completed successfully!");
        } catch (Exception e) {
            log.error("Error during data import", e);
        }
    }
    
    public void importJsonData(com.top50.dto.JsonImportRequest request, String username) {
        log.info("Importing JSON data for user: {}, week: {}", username, request.getWeek());
        
        // Get or create user, playlist, and week in a separate transaction
        User user = getOrCreateUser(username);
        Playlist playlist = getOrCreatePlaylist(user);
        Week week = weekService.getOrCreateWeek(request.getWeek());
        
        // First, remove all existing entries for this week (including soft-deleted) to avoid duplicates
        // This allows re-importing the same week with updated data after deletion
        removeAllEntriesForWeek(playlist, week);
        
        // Import entries - each entry in its own transaction to isolate failures
        int successCount = 0;
        for (com.top50.dto.JsonImportRequest.ChartEntry entry : request.getEntries()) {
            try {
                importChartEntryFromDto(entry, playlist, week);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to import chart entry for track {} in week {}: {}", 
                    entry.getTrackId(), request.getWeek(), e.getMessage());
                // Continue with next entry
            }
        }
        
        log.info("Successfully imported {} entries for week {} (attempted {})", 
            successCount, request.getWeek(), request.getEntries().size());
    }
    
    @Transactional
    private void removeAllEntriesForWeek(Playlist playlist, Week week) {
        // Get all entries for this week (including soft-deleted ones)
        // We need to hard-delete them all because unique constraint includes soft-deleted entries
        List<ChartEntry> allEntries = chartEntryRepository.findByPlaylistAndWeek(playlist, week);
        
        if (!allEntries.isEmpty()) {
            log.info("Removing {} existing entries (including soft-deleted) for week {} before re-import", 
                allEntries.size(), week.getIsoFormat());
            
            // Hard delete all entries (including soft-deleted) to avoid unique constraint conflicts
            // This allows clean re-import of the same week, even after it was deleted
            chartEntryRepository.deleteAll(allEntries);
            chartEntryRepository.flush();
        }
    }
    
    @Transactional
    private User getOrCreateUser(String username) {
        return userRepository.findByUsername(username.toLowerCase())
            .orElseGet(() -> {
                User newUser = new User();
                newUser.setId(UUID.randomUUID().toString());
                newUser.setUsername(username.toLowerCase());
                newUser.setDisplayName(username);
                newUser.setIsPublic(true);
                newUser.setCreatedAt(LocalDateTime.now());
                newUser.setUpdatedAt(LocalDateTime.now());
                return userRepository.save(newUser);
            });
    }
    
    @Transactional
    private Playlist getOrCreatePlaylist(User user) {
        return playlistRepository.findByUserAndNameAndDeletedAtIsNull(user, "Top 50 Charts")
            .orElseGet(() -> {
                Playlist newPlaylist = new Playlist();
                newPlaylist.setId(UUID.randomUUID().toString());
                newPlaylist.setUser(user);
                newPlaylist.setName("Top 50 Charts");
                newPlaylist.setIsPublic(true);
                newPlaylist.setIsActive(true);
                newPlaylist.setMaxPosition(50);
                newPlaylist.setCreatedAt(LocalDateTime.now());
                newPlaylist.setUpdatedAt(LocalDateTime.now());
                return playlistRepository.save(newPlaylist);
            });
    }
    
    @Transactional
    public void importChartEntryFromDto(com.top50.dto.JsonImportRequest.ChartEntry entryDto, Playlist playlist, Week week) {
        try {
            String trackId = entryDto.getTrackId();
            int position = entryDto.getPlacement();
            
            // Get or create track
            Track track = trackRepository.findById(trackId)
                .orElseGet(() -> {
                    Track newTrack = new Track();
                    newTrack.setId(trackId);
                    newTrack.setTitle(entryDto.getTitle());
                    newTrack.setSpotifyUrl(entryDto.getSpotifyUrl());
                    if (entryDto.getImageUrl() != null) {
                        newTrack.setImageUrl(entryDto.getImageUrl());
                    }
                    newTrack.setCreatedAt(LocalDateTime.now());
                    newTrack.setUpdatedAt(LocalDateTime.now());
                    return trackRepository.save(newTrack);
                });
            
            // Update track if needed
            boolean updated = false;
            if (entryDto.getImageUrl() != null && !Objects.equals(track.getImageUrl(), entryDto.getImageUrl())) {
                track.setImageUrl(entryDto.getImageUrl());
                updated = true;
            }
            if (updated) {
                track.setUpdatedAt(LocalDateTime.now());
                trackRepository.save(track);
            }
            
            // Get or create chart entry first (before linking artists to avoid cascade conflicts)
            // Check for existing entry (including soft-deleted ones due to unique constraint)
            Optional<ChartEntry> existingEntry = chartEntryRepository
                .findByPlaylistAndTrackAndWeekAndDeletedAtIsNull(playlist, track, week);
            
            ChartEntry chartEntry;
            if (existingEntry.isPresent()) {
                // Entry exists and is not deleted - update it
                chartEntry = existingEntry.get();
            } else {
                // Check if there's a soft-deleted entry (unique constraint prevents new entry)
                List<ChartEntry> allEntries = chartEntryRepository.findByPlaylistAndWeek(playlist, week)
                    .stream()
                    .filter(ce -> ce.getTrack().getId().equals(track.getId()))
                    .toList();
                
                if (!allEntries.isEmpty()) {
                    // Found soft-deleted entry - restore it
                    chartEntry = allEntries.get(0);
                    chartEntry.setDeletedAt(null); // Restore by clearing deletedAt
                } else {
                    // No entry exists - create new one
                    chartEntry = new ChartEntry();
                    chartEntry.setId(UUID.randomUUID().toString());
                    chartEntry.setPlaylist(playlist);
                    chartEntry.setTrack(track);
                    chartEntry.setWeek(week);
                    chartEntry.setCreatedAt(LocalDateTime.now());
                }
            }
            
            chartEntry.setPosition(position);
            chartEntryRepository.save(chartEntry);
            
            // Import artists after chart entry is saved (to avoid cascade conflicts)
            if (entryDto.getArtists() != null) {
                int artistPosition = 0;
                for (String artistName : entryDto.getArtists()) {
                    try {
                        Artist artist = getOrCreateArtist(artistName);
                        if (artist != null) {
                            linkTrackArtist(track, artist, artistPosition++);
                        }
                    } catch (Exception e) {
                        // Log but don't fail the entire entry import if artist linking fails
                        log.warn("Failed to link artist '{}' to track {}: {}", artistName, trackId, e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error importing chart entry for track {}: {}", entryDto.getTrackId(), e.getMessage());
            throw new RuntimeException("Error importing chart entry: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public void importUserData(String username, Path userDataPath) {
        if (!Files.exists(userDataPath)) {
            log.warn("User data directory not found: {}", userDataPath);
            return;
        }
        
        log.info("Importing data for user: {}", username);
        
        // Get or create user
        User user = userRepository.findByUsername(username)
            .orElseGet(() -> {
                User newUser = new User();
                newUser.setId(UUID.randomUUID().toString());
                newUser.setUsername(username.toLowerCase());
                newUser.setDisplayName(username);
                newUser.setIsPublic(true);
                newUser.setCreatedAt(LocalDateTime.now());
                newUser.setUpdatedAt(LocalDateTime.now());
                return userRepository.save(newUser);
            });
        
        // Get or create playlist
        Playlist playlist = playlistRepository.findByUserAndNameAndDeletedAtIsNull(user, "Top 50 Charts")
            .orElseGet(() -> {
                Playlist newPlaylist = new Playlist();
                newPlaylist.setId(UUID.randomUUID().toString());
                newPlaylist.setUser(user);
                newPlaylist.setName("Top 50 Charts");
                newPlaylist.setIsPublic(true);
                newPlaylist.setIsActive(true);
                newPlaylist.setMaxPosition(50);
                newPlaylist.setCreatedAt(LocalDateTime.now());
                newPlaylist.setUpdatedAt(LocalDateTime.now());
                return playlistRepository.save(newPlaylist);
            });
        
        // Import all week files
        try (Stream<Path> paths = Files.list(userDataPath)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> WEEK_PATTERN.matcher(path.getFileName().toString()).matches())
                .sorted()
                .forEach(path -> importWeekFile(path, playlist));
        } catch (IOException e) {
            log.error("Error reading user data directory: {}", userDataPath, e);
        }
    }
    
    public void importWeekFile(Path filePath, Playlist playlist) {
        try {
            String fileName = filePath.getFileName().toString();
            java.util.regex.Matcher matcher = WEEK_PATTERN.matcher(fileName);
            if (!matcher.find()) {
                log.warn("Invalid week file name: {}", fileName);
                return;
            }
            
            String weekIso = matcher.group(1);
            log.info("Importing week: {} for playlist: {}", weekIso, playlist.getName());
            
            Week week = weekService.getOrCreateWeek(weekIso);
            
            JsonNode root = objectMapper.readTree(filePath.toFile());
            JsonNode entries = root.get("entries");
            
            if (entries == null || !entries.isArray()) {
                log.warn("No entries found in file: {}", fileName);
                return;
            }
            
            int successCount = 0;
            for (JsonNode entry : entries) {
                try {
                    importChartEntry(entry, playlist, week);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to import entry: {}", e.getMessage());
                }
            }
            
            log.info("Imported {} entries for week {} (attempted {})", successCount, weekIso, entries.size());
        } catch (IOException e) {
            log.error("Error importing week file: {}", filePath, e);
        }
    }
    
    @Transactional
    public void importChartEntry(JsonNode entry, Playlist playlist, Week week) {
        String trackId = null;
        try {
            trackId = entry.get("track_id").asText();
            final String finalTrackId = trackId; // Final copy for lambda
            int position = entry.get("placement").asInt();
            
            // Get or create track
            Track track = trackRepository.findById(finalTrackId)
                .orElseGet(() -> {
                    Track newTrack = new Track();
                    newTrack.setId(finalTrackId);
                    newTrack.setTitle(entry.get("title").asText());
                    newTrack.setSpotifyUrl(entry.get("spotify_url").asText());
                    if (entry.has("image_url")) {
                        newTrack.setImageUrl(entry.get("image_url").asText());
                    }
                    newTrack.setCreatedAt(LocalDateTime.now());
                    newTrack.setUpdatedAt(LocalDateTime.now());
                    return trackRepository.save(newTrack);
                });
            
            // Update track if needed
            boolean updated = false;
            if (entry.has("image_url") && !Objects.equals(track.getImageUrl(), entry.get("image_url").asText())) {
                track.setImageUrl(entry.get("image_url").asText());
                updated = true;
            }
            if (updated) {
                track.setUpdatedAt(LocalDateTime.now());
                trackRepository.save(track);
            }
            
            // Get or create chart entry FIRST (most important)
            ChartEntry chartEntry = chartEntryRepository
                .findByPlaylistAndTrackAndWeekAndDeletedAtIsNull(playlist, track, week)
                .orElseGet(() -> {
                    ChartEntry newEntry = new ChartEntry();
                    newEntry.setId(UUID.randomUUID().toString());
                    newEntry.setPlaylist(playlist);
                    newEntry.setTrack(track);
                    newEntry.setWeek(week);
                    newEntry.setCreatedAt(LocalDateTime.now());
                    return newEntry;
                });
            
            chartEntry.setPosition(position);
            chartEntryRepository.saveAndFlush(chartEntry); // Force flush to ensure it's saved
            
            // Import artists AFTER chart entry is saved (don't fail if artist import fails)
            // Do this in a separate try-catch to not affect chart entry creation
            try {
                if (entry.has("artists") && entry.get("artists").isArray()) {
                    int artistPosition = 0;
                    for (JsonNode artistNode : entry.get("artists")) {
                        try {
                            String artistName = artistNode.asText();
                            Artist artist = getOrCreateArtist(artistName);
                            if (artist != null) {
                                linkTrackArtist(track, artist, artistPosition++);
                            }
                        } catch (Exception e) {
                            log.debug("Failed to import artist '{}' for track {}: {}", artistNode.asText(), trackId, e.getMessage());
                            // Continue with next artist
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to import artists for track {}, but chart entry was saved: {}", trackId, e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Error importing chart entry for track {}: {}", trackId != null ? trackId : "unknown", e.getMessage());
            throw e; // Re-throw to let caller handle
        }
    }
    
    @Transactional
    public Artist getOrCreateArtist(String artistName) {
        if (artistName == null || artistName.trim().isEmpty()) {
            return null;
        }
        
        String normalizedName = artistName.toLowerCase().trim();
        
        // Try to find existing artist first
        Optional<Artist> existing = artistRepository.findByNormalizedName(normalizedName);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Try to create new artist, handle duplicate key exception
        try {
            Artist artist = new Artist();
            artist.setId(UUID.randomUUID().toString());
            artist.setName(artistName);
            artist.setNormalizedName(normalizedName);
            artist.setDisplayName(artistName);
            artist.setCreatedAt(LocalDateTime.now());
            return artistRepository.save(artist);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // If duplicate key (race condition), try to find again
            log.debug("Artist '{}' was created by another thread, finding again", artistName);
            return artistRepository.findByNormalizedName(normalizedName)
                .orElse(null); // Return null instead of throwing
        } catch (Exception e) {
            log.warn("Unexpected error creating artist '{}': {}", artistName, e.getMessage());
            // Try to find one more time
            return artistRepository.findByNormalizedName(normalizedName)
                .orElse(null); // Return null instead of throwing
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = {DataIntegrityViolationException.class})
    public void linkTrackArtist(Track track, Artist artist, int position) {
        try {
            if (artist == null) {
                return;
            }
            
            // Check if link already exists in database
            Optional<TrackArtist> existing = trackArtistRepository.findByTrackAndArtistAndPosition(track, artist, position);
            
            if (existing.isEmpty()) {
                // Don't check track.getArtists() collection as it may be lazy-loaded and cause session conflicts
                // Just create and save the TrackArtist directly
                TrackArtist trackArtist = new TrackArtist();
                trackArtist.setTrack(track);
                trackArtist.setArtist(artist);
                trackArtist.setPosition(position);
                trackArtistRepository.saveAndFlush(trackArtist);
                // Don't add to track.getArtists() collection - let Hibernate manage the bidirectional relationship
            }
        } catch (DataIntegrityViolationException e) {
            // If duplicate key (race condition), ignore it - this won't rollback the transaction due to noRollbackFor
            log.debug("TrackArtist link already exists for track {} and artist {} at position {}", 
                track != null ? track.getId() : "null", 
                artist != null ? artist.getName() : "null", 
                position);
        } catch (org.hibernate.NonUniqueObjectException e) {
            // If object already in session, ignore it
            log.debug("TrackArtist already in session for track {} and artist {} at position {}", 
                track != null ? track.getId() : "null", 
                artist != null ? artist.getName() : "null", 
                position);
        } catch (Exception e) {
            log.warn("Failed to link artist {} to track {}: {}", artist != null ? artist.getName() : "null", track != null ? track.getId() : "null", e.getMessage());
            // Don't throw - continue with import
        }
    }
    
    @Transactional
    public void deleteChartEntriesForWeek(String weekIso, String username) {
        log.info("Deleting chart entries for user: {}, week: {}", username, weekIso);
        
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
            .orElseThrow(() -> new UserNotFoundException(username));
        
        List<Playlist> playlists = playlistRepository.findByUserAndDeletedAtIsNull(user);
        if (playlists.isEmpty()) {
            throw new PlaylistNotFoundException(username);
        }
        
        Playlist playlist = playlists.get(0);
        Week week = weekService.findByIsoFormat(weekIso)
            .orElseThrow(() -> new WeekNotFoundException(weekIso));
        
        // Use soft delete for consistency with deletedAt pattern
        // Get only non-deleted entries
        List<ChartEntry> entries = chartEntryRepository.findByPlaylistAndWeekNotDeleted(playlist, week);
        
        if (entries.isEmpty()) {
            log.info("No chart entries found for user: {}, week: {}", username, weekIso);
            return;
        }
        
        log.info("Soft deleting {} chart entries for user: {}, week: {}", entries.size(), username, weekIso);
        
        try {
            LocalDateTime now = LocalDateTime.now();
            entries.forEach(entry -> entry.setDeletedAt(now));
            chartEntryRepository.saveAll(entries);
            chartEntryRepository.flush();
            
            // Verify deletion
            long remainingCount = chartEntryRepository.findByPlaylistAndWeekNotDeleted(playlist, week).size();
            
            if (remainingCount > 0) {
                log.warn("Not all entries were deleted. Expected 0, found {} remaining", remainingCount);
                throw new DataDeletionException("Failed to delete all chart entries. " + remainingCount + " entries remain.");
            }
            
            log.info("Successfully soft deleted {} chart entries for user: {}, week: {}", 
                    entries.size(), username, weekIso);
        } catch (Exception e) {
            log.error("Failed to delete chart entries for user: {}, week: {}", username, weekIso, e);
            if (e instanceof DataDeletionException) {
                throw e;
            }
            throw new DataDeletionException("Failed to delete chart entries: " + e.getMessage(), e);
        }
    }
    
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
        
        return path.toAbsolutePath();
    }
}
