package com.top50.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tracks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Track {
    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "spotify_url", nullable = false, length = 500)
    private String spotifyUrl;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @OneToMany(mappedBy = "track", orphanRemoval = true)
    @ToString.Exclude
    private List<TrackArtist> artists = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
