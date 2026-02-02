-- Repair Flyway history by removing failed migration records
-- This allows V2 to run and clean the database

-- Remove failed V2 migration from history
DELETE FROM flyway_schema_history WHERE version = '2';

-- Now V2 can run on next deployment to drop tables and clear history
-- Then V1 will recreate tables with correct VARCHAR(36) schema
