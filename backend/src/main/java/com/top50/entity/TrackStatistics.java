package com.top50.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "track_statistics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TrackStatisticsId.class)
public class TrackStatistics {
    @Id
    @Column(name = "track_id", length = 50)
    private String trackId;

    @Id
    @Column(name = "playlist_id", length = 36, columnDefinition = "CHAR(36)")
    private String playlistId;

    @Column(name = "total_appearances", nullable = false)
    private Integer totalAppearances = 0;

    @Column(name = "highest_position")
    private Integer highestPosition;

    @Column(name = "lowest_position")
    private Integer lowestPosition;

    @Column(name = "weeks_in_charts", nullable = false)
    private Integer weeksInCharts = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "first_seen_week_id")
    private Week firstSeenWeek;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_seen_week_id")
    private Week lastSeenWeek;

    @Column(name = "average_position", precision = 5, scale = 2)
    private BigDecimal averagePosition;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class TrackStatisticsId implements java.io.Serializable {
    private String trackId;
    private String playlistId;
}
