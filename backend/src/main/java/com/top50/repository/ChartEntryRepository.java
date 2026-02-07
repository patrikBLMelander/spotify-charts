package com.top50.repository;

import com.top50.entity.ChartEntry;
import com.top50.entity.Playlist;
import com.top50.entity.Track;
import com.top50.entity.Week;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartEntryRepository extends JpaRepository<ChartEntry, String> {
    List<ChartEntry> findByPlaylistAndWeekAndDeletedAtIsNullOrderByPosition(Playlist playlist, Week week);
    
    @Query("SELECT DISTINCT ce.week FROM ChartEntry ce WHERE ce.playlist = :playlist AND ce.deletedAt IS NULL ORDER BY ce.week.startDate DESC")
    List<Week> findDistinctWeeksByPlaylist(@Param("playlist") Playlist playlist);
    
    @Query("SELECT ce FROM ChartEntry ce WHERE ce.track = :track AND ce.playlist = :playlist AND ce.deletedAt IS NULL ORDER BY ce.week.startDate")
    List<ChartEntry> findByTrackAndPlaylistOrderByWeek(@Param("track") Track track, @Param("playlist") Playlist playlist);
    
    Optional<ChartEntry> findByPlaylistAndTrackAndWeekAndDeletedAtIsNull(Playlist playlist, Track track, Week week);
    
    @Query("SELECT ce FROM ChartEntry ce WHERE ce.playlist = :playlist AND ce.week = :week AND ce.deletedAt IS NULL ORDER BY ce.position")
    List<ChartEntry> findByPlaylistAndWeekOrderByPosition(@Param("playlist") Playlist playlist, @Param("week") Week week);
    
    @Query("SELECT ce FROM ChartEntry ce WHERE ce.playlist = :playlist AND ce.week.startDate < :currentWeekStart AND ce.track = :track AND ce.deletedAt IS NULL ORDER BY ce.week.startDate DESC")
    List<ChartEntry> findPreviousEntry(@Param("playlist") Playlist playlist, @Param("track") Track track, @Param("currentWeekStart") java.time.LocalDate currentWeekStart);
    
    @Query("SELECT ce FROM ChartEntry ce WHERE ce.playlist = :playlist AND ce.week = :week")
    List<ChartEntry> findByPlaylistAndWeek(@Param("playlist") Playlist playlist, @Param("week") Week week);
    
    @Query("SELECT ce FROM ChartEntry ce WHERE ce.playlist = :playlist AND ce.week = :week AND ce.deletedAt IS NULL")
    List<ChartEntry> findByPlaylistAndWeekNotDeleted(@Param("playlist") Playlist playlist, @Param("week") Week week);
}
