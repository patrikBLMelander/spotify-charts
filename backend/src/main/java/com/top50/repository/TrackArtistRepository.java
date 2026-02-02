package com.top50.repository;

import com.top50.entity.Track;
import com.top50.entity.TrackArtist;
import com.top50.entity.TrackArtistId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrackArtistRepository extends JpaRepository<TrackArtist, TrackArtistId> {
    Optional<TrackArtist> findByTrackAndArtistAndPosition(Track track, com.top50.entity.Artist artist, Integer position);
}
