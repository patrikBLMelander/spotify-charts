-- Change CHAR(36) to VARCHAR(36) for ID columns to match Hibernate expectations
-- Hibernate interprets @Column(length = 36) as VARCHAR(36), not CHAR(36)

ALTER TABLE artists MODIFY COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE weeks MODIFY COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE users MODIFY COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE playlists MODIFY COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE playlists MODIFY COLUMN user_id VARCHAR(36) NOT NULL;
ALTER TABLE chart_entries MODIFY COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE chart_entries MODIFY COLUMN playlist_id VARCHAR(36) NOT NULL;
ALTER TABLE chart_entries MODIFY COLUMN week_id VARCHAR(36) NOT NULL;
ALTER TABLE chart_entries MODIFY COLUMN created_by VARCHAR(36);
ALTER TABLE track_artists MODIFY COLUMN artist_id VARCHAR(36) NOT NULL;
ALTER TABLE track_statistics MODIFY COLUMN playlist_id VARCHAR(36) NOT NULL;
ALTER TABLE track_statistics MODIFY COLUMN first_seen_week_id VARCHAR(36);
ALTER TABLE track_statistics MODIFY COLUMN last_seen_week_id VARCHAR(36);
