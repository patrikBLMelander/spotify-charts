package com.top50.repository;

import com.top50.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrackRepository extends JpaRepository<Track, String> {
    Optional<Track> findByIdAndDeletedAtIsNull(String id);
}
