package com.top50.service;

import com.top50.dto.ChartEntryDto;
import com.top50.dto.TrackDto;
import com.top50.dto.TrackHistoryDto;
import com.top50.entity.*;
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
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        List<Playlist> playlists = playlistRepository.findByUserAndDeletedAtIsNull(user);
        if (playlists.isEmpty()) {
            return Collections.emptyList();
        }
        
        Playlist playlist = playlists.get(0);
        Week week = weekService.findByIsoFormat(weekIso)
            .orElseThrow(() -> new RuntimeException("Week not found: " + weekIso));
        
        List<ChartEntry> entries = chartEntryRepository.findByPlaylistAndWeekOrderByPosition(playlist, week);
        
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
            
            // Find previous position
            List<ChartEntry> previousEntries = chartEntryRepository.findPreviousEntry(
                playlist, track, week.getStartDate()
            );
            if (!previousEntries.isEmpty()) {
                dto.setPreviousPosition(previousEntries.get(0).getPosition());
            }
            
            return dto;
        }).collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public TrackHistoryDto getTrackHistory(String trackId, String username) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
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
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        List<Playlist> playlists = playlistRepository.findByUserAndDeletedAtIsNull(user);
        if (playlists.isEmpty()) {
            return Collections.emptyList();
        }
        
        Playlist playlist = playlists.get(0);
        Week currentWeek = weekService.findByIsoFormat(weekIso)
            .orElseThrow(() -> new RuntimeException("Week not found: " + weekIso));
        
        // Find previous week
        List<Week> allWeeks = chartEntryRepository.findDistinctWeeksByPlaylist(playlist);
        Week previousWeek = null;
        for (int i = 0; i < allWeeks.size(); i++) {
            if (allWeeks.get(i).getId().equals(currentWeek.getId()) && i < allWeeks.size() - 1) {
                previousWeek = allWeeks.get(i + 1);
                break;
            }
        }
        
        if (previousWeek == null) {
            return Collections.emptyList();
        }
        
        // Get tracks from previous week
        final Week finalPreviousWeek = previousWeek; // Make final for lambda
        List<ChartEntry> previousEntries = chartEntryRepository.findByPlaylistAndWeekOrderByPosition(playlist, finalPreviousWeek);
        
        // Filter out tracks that are still in current week
        Set<String> currentTrackIds = chartEntryRepository.findByPlaylistAndWeekOrderByPosition(playlist, currentWeek)
            .stream()
            .map(ce -> ce.getTrack().getId())
            .collect(Collectors.toSet());
        
        return previousEntries.stream()
            .filter(entry -> !currentTrackIds.contains(entry.getTrack().getId()))
            .map(entry -> {
                ChartEntryDto dto = new ChartEntryDto();
                dto.setWeek(finalPreviousWeek.getIsoFormat());
                dto.setPosition(entry.getPosition());
                dto.setTrack(convertToTrackDto(entry.getTrack()));
                return dto;
            })
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<TrackDto> getAllTracks(String username) {
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
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
}
