-- Drop all tables and reset Flyway history
-- This allows V1 to recreate tables with correct VARCHAR(36) types
-- This migration fixes the failed V2 migration issue

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

-- Clean Flyway history to remove failed migration records
-- Note: This must be done after dropping tables to avoid foreign key issues
-- Remove only version 2 (failed migration), keep version 1 so V3 can run
DELETE FROM flyway_schema_history WHERE version = '2';
