package com.top50.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "track_artists", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"track_id", "artist_id"}),
    @UniqueConstraint(columnNames = {"track_id", "position"})
})
@IdClass(TrackArtistId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackArtist {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    @ToString.Exclude
    private Track track;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    @ToString.Exclude
    private Artist artist;

    @Column(nullable = false)
    private Integer position;
}
