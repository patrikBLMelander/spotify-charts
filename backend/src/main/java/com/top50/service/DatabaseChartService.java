package com.top50.service;

import com.top50.dto.ChartEntryDto;
import com.top50.dto.TrackDto;
import com.top50.dto.TrackHistoryDto;
import com.top50.entity.*;
import com.top50.exception.*;
import com.top50.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseChartService {
    private final ChartEntryRepository chartEntryRepository;
    private final PlaylistRepository playlistRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    private final WeekRepository weekRepository;
    private final WeekService weekService;
    
    public List<String> getAllWeeks(String username) {
        try {
            User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElse(null);
            
            if (user == null) {
                log.warn("User not found: {}. Returning empty list.", username);
                return Collections.emptyList();
            }
            
            List<Playlist> playlists = playlistRepository.findByUserAndDeletedAtIsNull(user);
            if (playlists.isEmpty()) {
                log.debug("No playlists found for user: {}. Returning empty list.", username);
                return Collections.emptyList();
            }
            
            // For now, use the first playlist (in future, could support multiple)
            Playlist playlist = playlists.get(0);
            List<Week> weeks = chartEntryRepository.findDistinctWeeksByPlaylist(playlist);
            
            return weeks.stream()
                .map(Week::getIsoFormat)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting weeks for user: {}", username, e);
            return Collections.emptyList();
        }
    }
    
    @Transactional(readOnly = true)
    public List<ChartEntryDto> getChartByWeek(String weekIso, String username) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
            .orElseThrow(() -> new UserNotFoundException(username));
        
        List<Playlist> playlists = playlistRepository.findByUserAndDeletedAtIsNull(user);
        if (playlists.isEmpty()) {
            return Collections.emptyList();
        }
        
        Playlist playlist = playlists.get(0);
        Week week = weekService.findByIsoFormat(weekIso)
            .orElseThrow(() -> new WeekNotFoundException(weekIso));
        
        List<ChartEntry> entries = chartEntryRepository.findByPlaylistAndWeekOrderByPosition(playlist, week);
        
        // Find the directly previous week (optimized: use week startDate for lookup)
        // Weeks are sorted DESC by startDate, so we find the week with startDate just before current
        Week previousWeek = findPreviousWeek(playlist, week);
        
        // Get all tracks from previous week for quick lookup
        Set<String> previousWeekTrackIds = new HashSet<>();
        Map<String, Integer> previousWeekPositions = new HashMap<>();
        if (previousWeek != null) {
            List<ChartEntry> previousEntries = chartEntryRepository.findByPlaylistAndWeekOrderByPosition(playlist, previousWeek);
            for (ChartEntry prevEntry : previousEntries) {
                previousWeekTrackIds.add(prevEntry.getTrack().getId());
                previousWeekPositions.put(prevEntry.getTrack().getId(), prevEntry.getPosition());
            }
        }
        
        return entries.stream().map(entry -> {
            ChartEntryDto dto = new ChartEntryDto();
            dto.setWeek(weekIso);
            dto.setPosition(entry.getPosition());
            
            TrackDto trackDto = new TrackDto();
            Track track = entry.getTrack();
            trackDto.setId(track.getId());
            trackDto.setTitle(track.getTitle());
            trackDto.setSpotifyUrl(track.getSpotifyUrl());
            trackDto.setImageUrl(track.getImageUrl());
            
            // Get artists
            List<String> artistNames = track.getArtists().stream()
                .sorted(Comparator.comparing(TrackArtist::getPosition))
                .map(ta -> ta.getArtist().getName())
                .collect(Collectors.toList());
            trackDto.setArtists(artistNames);
            
            dto.setTrack(trackDto);
            
            // Only set previous position if track was in the directly previous week
            // If track was not in previous week (even if it was in an earlier week), it's a new entry
            if (previousWeek != null && previousWeekTrackIds.contains(track.getId())) {
                dto.setPreviousPosition(previousWeekPositions.get(track.getId()));
            }
            // Otherwise, previousPosition remains null (new entry)
            
            return dto;
        }).collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public TrackHistoryDto getTrackHistory(String trackId, String username) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
            .orElseThrow(() -> new UserNotFoundException(username));
        
        Track track = trackRepository.findByIdAndDeletedAtIsNull(trackId)
            .orElseThrow(() -> new RuntimeException("Track not found: " + trackId));
        
        List<Playlist> playlists = playlistRepository.findByUserAndDeletedAtIsNull(user);
        if (playlists.isEmpty()) {
            throw new RuntimeException("No playlist found for user: " + username);
        }
        
        Playlist playlist = playlists.get(0);
        List<ChartEntry> entries = chartEntryRepository.findByTrackAndPlaylistOrderByWeek(track, playlist);
        
        TrackHistoryDto history = new TrackHistoryDto();
        history.setTrack(convertToTrackDto(track));
        
        List<TrackHistoryDto.PositionPoint> points = entries.stream()
            .map(entry -> {
                TrackHistoryDto.PositionPoint point = new TrackHistoryDto.PositionPoint();
                point.setWeek(entry.getWeek().getIsoFormat());
                point.setPosition(entry.getPosition());
                return point;
            })
            .collect(Collectors.toList());
        
        history.setHistory(points);
        return history;
    }
    
    @Transactional(readOnly = true)
    public List<ChartEntryDto> getDroppedTracks(String weekIso, String username) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
            .orElseThrow(() -> new UserNotFoundException(username));
        
        List<Playlist> playlists = playlistRepository.findByUserAndDeletedAtIsNull(user);
        if (playlists.isEmpty()) {
            return Collections.emptyList();
        }
        
        Playlist playlist = playlists.get(0);
        Week currentWeek = weekService.findByIsoFormat(weekIso)
            .orElseThrow(() -> new WeekNotFoundException(weekIso));
        
        // Find previous week using optimized method
        Week previousWeek = findPreviousWeek(playlist, currentWeek);
        
        if (previousWeek == null) {
            return Collections.emptyList();
        }
        
        // Get tracks from previous week
        final Week finalPreviousWeek = previousWeek; // Make final for lambda
        List<ChartEntry> previousEntries = chartEntryRepository.findByPlaylistAndWeekOrderByPosition(playlist, finalPreviousWeek);
        
        // Get all current week entries with their tracks and artists for comparison
        List<ChartEntry> currentEntries = chartEntryRepository.findByPlaylistAndWeekOrderByPosition(playlist, currentWeek);
        
        // Create a set of current track IDs for fast lookup
        Set<String> currentTrackIds = currentEntries.stream()
            .map(ce -> ce.getTrack().getId())
            .collect(Collectors.toSet());
        
        // Create a map of current tracks by normalized title to all artist sets for fuzzy matching
        // Multiple tracks can have the same title, so we need to check all of them
        Map<String, List<Set<String>>> currentTracksByTitleAndArtists = currentEntries.stream()
            .collect(Collectors.groupingBy(
                entry -> normalizeTitle(entry.getTrack().getTitle()),
                Collectors.mapping(
                    entry -> getArtistNames(entry.getTrack()),
                    Collectors.toList()
                )
            ));
        
        return previousEntries.stream()
            .filter(entry -> {
                String trackId = entry.getTrack().getId();
                
                // First check: exact track ID match (fast path)
                if (currentTrackIds.contains(trackId)) {
                    return false; // Track is still in current week
                }
                
                // Second check: fuzzy match - same title and at least one common artist
                String normalizedTitle = normalizeTitle(entry.getTrack().getTitle());
                Set<String> previousArtists = getArtistNames(entry.getTrack());
                
                List<Set<String>> currentArtistsList = currentTracksByTitleAndArtists.get(normalizedTitle);
                if (currentArtistsList != null) {
                    // Check all tracks with the same title to see if any share at least one artist
                    boolean hasCommonArtist = currentArtistsList.stream()
                        .anyMatch(currentArtists -> previousArtists.stream()
                            .anyMatch(currentArtists::contains));
                    
                    if (hasCommonArtist) {
                        // Same song (different version) is still in current week, don't show as dropped
                        log.debug("Track '{}' (ID: {}) has a different version in current week, not showing as dropped", 
                            entry.getTrack().getTitle(), trackId);
                        return false;
                    }
                }
                
                // Track is truly dropped
                return true;
            })
            .map(entry -> {
                ChartEntryDto dto = new ChartEntryDto();
                dto.setWeek(finalPreviousWeek.getIsoFormat());
                dto.setPosition(entry.getPosition());
                dto.setTrack(convertToTrackDto(entry.getTrack()));
                return dto;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Normalize track title for comparison (lowercase, trim, remove extra spaces)
     */
    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase().trim().replaceAll("\\s+", " ");
    }
    
    /**
     * Get normalized artist names from a track
     */
    private Set<String> getArtistNames(Track track) {
        return track.getArtists().stream()
            .sorted(Comparator.comparing(TrackArtist::getPosition))
            .map(ta -> ta.getArtist().getName().toLowerCase().trim())
            .collect(Collectors.toSet());
    }
    
    @Transactional(readOnly = true)
    public List<TrackDto> getAllTracks(String username) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
            .orElseThrow(() -> new UserNotFoundException(username));
        
        List<Playlist> playlists = playlistRepository.findByUserAndDeletedAtIsNull(user);
        if (playlists.isEmpty()) {
            return Collections.emptyList();
        }
        
        Playlist playlist = playlists.get(0);
        
        // Get all unique tracks from all weeks for this playlist
        Set<String> trackIds = new HashSet<>();
        List<Week> weeks = chartEntryRepository.findDistinctWeeksByPlaylist(playlist);
        for (Week week : weeks) {
            List<ChartEntry> entries = chartEntryRepository.findByPlaylistAndWeekOrderByPosition(playlist, week);
            entries.forEach(entry -> trackIds.add(entry.getTrack().getId()));
        }
        
        return trackIds.stream()
            .map(trackId -> {
                Track track = trackRepository.findByIdAndDeletedAtIsNull(trackId)
                    .orElse(null);
                return track != null ? convertToTrackDto(track) : null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    private TrackDto convertToTrackDto(Track track) {
        TrackDto dto = new TrackDto();
        dto.setId(track.getId());
        dto.setTitle(track.getTitle());
        dto.setSpotifyUrl(track.getSpotifyUrl());
        dto.setImageUrl(track.getImageUrl());
        
        List<String> artistNames = track.getArtists().stream()
            .sorted(Comparator.comparing(TrackArtist::getPosition))
            .map(ta -> ta.getArtist().getName())
            .collect(Collectors.toList());
        dto.setArtists(artistNames);
        
        return dto;
    }
    
    /**
     * Find the directly previous week for a given playlist and week.
     * Optimized to avoid loading all weeks when we only need the previous one.
     * 
     * @param playlist The playlist to search in
     * @param currentWeek The current week to find the previous week for
     * @return The previous week, or null if no previous week exists
     */
    private Week findPreviousWeek(Playlist playlist, Week currentWeek) {
        // Get all weeks sorted by startDate DESC (newest first)
        List<Week> allWeeks = chartEntryRepository.findDistinctWeeksByPlaylist(playlist);
        
        // Find current week index and get the next one (which is the previous chronologically)
        for (int i = 0; i < allWeeks.size(); i++) {
            if (allWeeks.get(i).getId().equals(currentWeek.getId()) && i < allWeeks.size() - 1) {
                return allWeeks.get(i + 1);
            }
        }
        return null;
    }
}
