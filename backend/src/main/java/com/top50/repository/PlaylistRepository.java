package com.top50.repository;

import com.top50.entity.Playlist;
import com.top50.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, String> {
    List<Playlist> findByUserAndDeletedAtIsNull(User user);
    Optional<Playlist> findByUserAndNameAndDeletedAtIsNull(User user, String name);
    List<Playlist> findByIsPublicTrueAndIsActiveTrueAndDeletedAtIsNull();
}
