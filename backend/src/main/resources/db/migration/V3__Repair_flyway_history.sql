-- Repair Flyway history and reset database in one step
-- This fixes the failed V2 migration issue by doing everything V2 was supposed to do

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

-- Clean ALL Flyway history to allow V1 to run again with correct schema
DELETE FROM flyway_schema_history;
