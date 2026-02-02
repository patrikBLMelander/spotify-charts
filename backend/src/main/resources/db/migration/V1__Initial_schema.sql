-- Top 50 Charts Database Schema
-- Based on improved design with weeks table, soft deletes, and optimizations

-- ============================================
-- 1. Weeks Table (Improved design)
-- ============================================
CREATE TABLE weeks (
    id CHAR(36) PRIMARY KEY,
    week_year INT NOT NULL,
    week_number INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    iso_format VARCHAR(10) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_weeks_year_number (week_year, week_number),
    CHECK (week_number >= 1 AND week_number <= 53)
);

CREATE INDEX idx_weeks_iso_format ON weeks(iso_format);
CREATE INDEX idx_weeks_start_date ON weeks(start_date);

-- ============================================
-- 2. Users Table
-- ============================================
CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255),
    display_name VARCHAR(100),
    is_public BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_public ON users(is_public);

-- ============================================
-- 3. Playlists Table
-- ============================================
CREATE TABLE playlists (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_public BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    max_position INT DEFAULT 50,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_playlists_user_name (user_id, name)
);

CREATE INDEX idx_playlists_user ON playlists(user_id);
CREATE INDEX idx_playlists_public ON playlists(is_public, is_active);

-- ============================================
-- 4. Tracks Table (Global)
-- ============================================
CREATE TABLE tracks (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    spotify_url VARCHAR(500) NOT NULL,
    image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_tracks_deleted ON tracks(deleted_at);

-- ============================================
-- 5. Artists Table (Global)
-- ============================================
CREATE TABLE artists (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_artists_normalized (normalized_name)
);

CREATE INDEX idx_artists_name ON artists(name);

-- ============================================
-- 6. Track Artists (Many-to-Many)
-- ============================================
CREATE TABLE track_artists (
    track_id VARCHAR(50) NOT NULL,
    artist_id CHAR(36) NOT NULL,
    position INT NOT NULL,
    
    PRIMARY KEY (track_id, artist_id),
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE RESTRICT,
    FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_track_artists_position (track_id, position)
);

CREATE INDEX idx_track_artists_track ON track_artists(track_id);
CREATE INDEX idx_track_artists_artist ON track_artists(artist_id);

-- ============================================
-- 7. Chart Entries (Core table)
-- ============================================
CREATE TABLE chart_entries (
    id CHAR(36) PRIMARY KEY,
    playlist_id CHAR(36) NOT NULL,
    track_id VARCHAR(50) NOT NULL,
    week_id CHAR(36) NOT NULL,
    position INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by CHAR(36),
    deleted_at TIMESTAMP NULL,
    
    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE RESTRICT,
    FOREIGN KEY (week_id) REFERENCES weeks(id) ON DELETE RESTRICT,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    
    UNIQUE KEY uk_chart_entries_playlist_week_track (playlist_id, week_id, track_id),
    UNIQUE KEY uk_chart_entries_playlist_week_position (playlist_id, week_id, position),
    CHECK (position > 0)
);

-- Composite indexes for performance
CREATE INDEX idx_chart_entries_playlist_week_position ON chart_entries(playlist_id, week_id, position);
CREATE INDEX idx_chart_entries_track_playlist_week ON chart_entries(track_id, playlist_id, week_id);
CREATE INDEX idx_chart_entries_playlist_track_week ON chart_entries(playlist_id, track_id, week_id);
CREATE INDEX idx_chart_entries_deleted ON chart_entries(deleted_at);

-- ============================================
-- 8. Track Statistics (Performance optimization)
-- ============================================
CREATE TABLE track_statistics (
    track_id VARCHAR(50) NOT NULL,
    playlist_id CHAR(36) NOT NULL,
    total_appearances INT DEFAULT 0,
    highest_position INT,
    lowest_position INT,
    weeks_in_charts INT DEFAULT 0,
    first_seen_week_id CHAR(36),
    last_seen_week_id CHAR(36),
    average_position DECIMAL(5,2),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    PRIMARY KEY (track_id, playlist_id),
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
    FOREIGN KEY (first_seen_week_id) REFERENCES weeks(id) ON DELETE SET NULL,
    FOREIGN KEY (last_seen_week_id) REFERENCES weeks(id) ON DELETE SET NULL
);

CREATE INDEX idx_track_statistics_playlist ON track_statistics(playlist_id);

-- ============================================
-- 9. Triggers for automatic updates
-- ============================================

-- Note: Triggers with complex logic require DELIMITER which Flyway doesn't support well
-- Statistics will be updated via application logic instead
-- Triggers can be added later via a separate migration if needed

-- Trigger to update track statistics when chart entry is deleted (soft delete)
-- Note: Simplified trigger without DELIMITER for Flyway compatibility
-- This trigger will be created in a separate migration file if needed
-- For now, statistics will be updated via application logic
