-- Clean database and Flyway history to allow fresh start
-- This migration runs BEFORE V1 to drop all tables and clear Flyway history
-- After this runs, V1 will recreate everything with correct schema

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

-- Clear ALL Flyway history to allow V1 to run again
DELETE FROM flyway_schema_history;
