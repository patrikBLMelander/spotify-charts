package com.top50.repository;

import com.top50.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArtistRepository extends JpaRepository<Artist, String> {
    Optional<Artist> findByNormalizedName(String normalizedName);
    Optional<Artist> findByName(String name);
}
