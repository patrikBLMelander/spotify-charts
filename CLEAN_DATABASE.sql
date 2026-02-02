-- Run this script manually in Railway's MySQL console to clean everything
-- Then V1 will create tables with correct schema on next deployment

SET FOREIGN_KEY_CHECKS = 0;

-- Drop all tables
DROP TABLE IF EXISTS track_statistics;
DROP TABLE IF EXISTS chart_entries;
DROP TABLE IF EXISTS track_artists;
DROP TABLE IF EXISTS artists;
DROP TABLE IF EXISTS tracks;
DROP TABLE IF EXISTS playlists;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS weeks;

SET FOREIGN_KEY_CHECKS = 1;

-- Clear ALL Flyway history
DELETE FROM flyway_schema_history;
