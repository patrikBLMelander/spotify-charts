# Förbättringar och Brister i Databassdesignen

## 🔴 Kritiska Brister

### 1. Veckor som Strängar - Sorteringsproblem
**Problem:**
```sql
week VARCHAR(10)  -- "2026-W05"
```
- Lexikografisk sortering fungerar, men är inte optimal
- Svårt att göra queries som "alla veckor i januari 2026"
- Ingen validering av format
- Edge cases: veckor som sträcker sig över år (t.ex. vecka 1 kan vara i slutet av december)

**Förbättring:**
```sql
-- Alternativ 1: Separata kolumner
week_year INTEGER NOT NULL,
week_number INTEGER NOT NULL CHECK (week_number >= 1 AND week_number <= 53),
UNIQUE(week_year, week_number)

-- Alternativ 2: DATE för veckans start
week_start_date DATE NOT NULL,  -- Måndagen för veckan
-- Med en funktion/trigger som beräknar ISO veckonummer vid behov

-- Alternativ 3: Separata weeks-tabell
CREATE TABLE weeks (
    id UUID PRIMARY KEY,
    week_year INTEGER NOT NULL,
    week_number INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    iso_format VARCHAR(10) UNIQUE NOT NULL,  -- "2026-W05"
    UNIQUE(week_year, week_number)
);
```

**Rekommendation:** Alternativ 3 ger bäst flexibilitet och prestanda.

---

### 2. Hårdkodad Position-gräns
**Problem:**
```sql
position INTEGER NOT NULL CHECK (position > 0 AND position <= 50)
```
- Om någon vill ha Top 100 eller Top 20 måste constraint ändras
- Olika listor kan ha olika storlekar

**Förbättring:**
```sql
-- Flytta max_position till playlist
ALTER TABLE playlists ADD COLUMN max_position INTEGER DEFAULT 50;

-- Uppdatera constraint
position INTEGER NOT NULL CHECK (position > 0),
-- Validera mot playlist.max_position i application layer eller trigger
```

---

### 3. ON DELETE CASCADE - Dataförlustrisker
**Problem:**
```sql
track_id VARCHAR(50) NOT NULL REFERENCES tracks(id) ON DELETE CASCADE
```
- Om en track raderas (av misstag eller Spotify tar bort låten) försvinner all historik
- Ingen möjlighet till "soft delete" eller arkivering

**Förbättring:**
```sql
-- Alternativ 1: Soft delete
ALTER TABLE tracks ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE chart_entries ADD COLUMN deleted_at TIMESTAMP;

-- Alternativ 2: ON DELETE SET NULL + arkivering
ALTER TABLE chart_entries 
  ALTER COLUMN track_id DROP NOT NULL,
  ADD COLUMN track_snapshot JSONB;  -- Spara track-info vid tidpunkten

-- Alternativ 3: ON DELETE RESTRICT (förhindra radering om används)
ALTER TABLE chart_entries 
  DROP CONSTRAINT chart_entries_track_id_fkey,
  ADD CONSTRAINT chart_entries_track_id_fkey 
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE RESTRICT;
```

**Rekommendation:** Kombinera soft delete + RESTRICT för bäst skydd.

---

## ⚠️ Prestanda och Skalbarhet

### 4. Ineffektiv "Previous Position" Query
**Problem:**
```sql
-- I exemplet görs en subquery för VARJE rad
COALESCE(
    (SELECT position FROM chart_entries WHERE ... LIMIT 1),
    NULL
) AS previous_position
```
- O(n²) komplexitet - blir långsamt med många låtar
- Körs för varje rad i resultatet

**Förbättring:**
```sql
-- Alternativ 1: Window function (PostgreSQL)
SELECT 
    ce.position,
    ce.week,
    LAG(ce.position) OVER (
        PARTITION BY ce.playlist_id, ce.track_id 
        ORDER BY ce.week
    ) AS previous_position
FROM chart_entries ce
WHERE ce.playlist_id = ? AND ce.week = ?;

-- Alternativ 2: Materialiserad vy
CREATE MATERIALIZED VIEW chart_entry_history AS
SELECT 
    ce.*,
    LAG(ce.position) OVER (
        PARTITION BY ce.playlist_id, ce.track_id 
        ORDER BY ce.week
    ) AS previous_position
FROM chart_entries ce;

CREATE INDEX ON chart_entry_history(playlist_id, week);
-- Uppdatera vy: REFRESH MATERIALIZED VIEW CONCURRENTLY chart_entry_history;
```

**Rekommendation:** Window function i application layer eller materialiserad vy.

---

### 5. Saknade Composite Indexes
**Problem:**
- Många queries filtrerar på flera kolumner samtidigt
- Saknade index kan göra queries långsamma

**Förbättring:**
```sql
-- För "hämta alla veckor för en lista" + "hämta låtar för vecka"
CREATE INDEX idx_chart_entries_playlist_week_position 
  ON chart_entries(playlist_id, week, position);

-- För "hämta låts historia" (används ofta för diagram)
CREATE INDEX idx_chart_entries_track_playlist_week 
  ON chart_entries(track_id, playlist_id, week);

-- För "hämta alla låtar i en lista över tid"
CREATE INDEX idx_chart_entries_playlist_track_week 
  ON chart_entries(playlist_id, track_id, week);
```

---

### 6. Artist Normalisering - Dupliceringsproblem
**Problem:**
```sql
name VARCHAR(255) UNIQUE NOT NULL
```
- "Bruno Mars" vs "Bruno Mars " (whitespace)
- "The Weeknd" vs "the weeknd" (case sensitivity)
- Artistnamn kan ändras över tid
- Olika stavningar av samma artist

**Förbättring:**
```sql
-- Alternativ 1: Normalisering + alias
ALTER TABLE artists ADD COLUMN normalized_name VARCHAR(255);
ALTER TABLE artists ADD COLUMN display_name VARCHAR(255);

CREATE TABLE artist_aliases (
    id UUID PRIMARY KEY,
    artist_id UUID REFERENCES artists(id),
    alias VARCHAR(255) NOT NULL,
    is_primary BOOLEAN DEFAULT false,
    UNIQUE(artist_id, alias)
);

-- Normalisera vid insert: LOWER(TRIM(name))

-- Alternativ 2: Fuzzy matching i application layer
-- Använd Levenshtein distance eller liknande för att hitta liknande artister
```

---

## 🟡 Designförbättringar

### 7. Saknad Versionshantering
**Problem:**
- Ingen historik om när tracks uppdaterades
- Ingen möjlighet att se "hur såg låtinfo ut denna vecka?"

**Förbättring:**
```sql
-- Alternativ 1: Versionshantering i tracks
CREATE TABLE track_versions (
    id UUID PRIMARY KEY,
    track_id VARCHAR(50) REFERENCES tracks(id),
    title VARCHAR(255),
    spotify_url VARCHAR(500),
    image_url VARCHAR(500),
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Alternativ 2: Snapshot i chart_entries
ALTER TABLE chart_entries ADD COLUMN track_snapshot JSONB;
-- Spara track-info vid tidpunkten chart entry skapades
```

---

### 8. Saknad Audit Trail
**Problem:**
- Ingen loggning av vem som skapade/uppdaterade data
- Svårt att spåra fel eller ändringar

**Förbättring:**
```sql
-- Lägg till på alla tabeller
created_by UUID REFERENCES users(id),
updated_by UUID REFERENCES users(id),
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

-- Trigger för att uppdatera updated_at automatiskt
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_chart_entries_updated_at 
  BEFORE UPDATE ON chart_entries
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

---

### 9. Ingen Caching-strategi
**Problem:**
- Vanliga queries körs varje gång
- T.ex. "hämta alla veckor" körs ofta men ändras sällan

**Förbättring:**
```sql
-- Materialiserade vyer för vanliga queries
CREATE MATERIALIZED VIEW playlist_weeks AS
SELECT 
    playlist_id,
    week,
    COUNT(*) AS track_count,
    MIN(position) AS lowest_position,
    MAX(position) AS highest_position
FROM chart_entries
GROUP BY playlist_id, week;

CREATE UNIQUE INDEX ON playlist_weeks(playlist_id, week);

-- Uppdatera vid behov (t.ex. via trigger eller scheduled job)
REFRESH MATERIALIZED VIEW CONCURRENTLY playlist_weeks;
```

---

### 10. Saknad Validering av Week-format
**Problem:**
- Inga constraints som validerar "2026-W05" format
- Felaktiga veckor kan sparas

**Förbättring:**
```sql
-- Check constraint för ISO veckonummer
ALTER TABLE chart_entries 
  ADD CONSTRAINT valid_week_format 
  CHECK (week ~ '^\d{4}-W\d{2}$');

-- Eller med weeks-tabell (se punkt 1)
-- Då kan vi använda foreign key istället
```

---

## 🟢 Ytterligare Förbättringar

### 11. Batch Operations
**Problem:**
- Inga optimeringar för bulk imports
- Varje chart entry sparas individuellt

**Förbättring:**
```sql
-- Batch insert med COPY (PostgreSQL)
COPY chart_entries(playlist_id, track_id, week, position)
FROM '/path/to/data.csv'
WITH (FORMAT csv, HEADER true);

-- Eller använd INSERT ... ON CONFLICT för idempotent imports
INSERT INTO chart_entries (playlist_id, track_id, week, position)
VALUES (?, ?, ?, ?)
ON CONFLICT (playlist_id, week, track_id) 
DO UPDATE SET position = EXCLUDED.position;
```

---

### 12. Statistiktabell för Prestanda
**Problem:**
- Beräkningar som "total appearances", "highest position" görs on-the-fly
- Kan bli långsamt med många veckor

**Förbättring:**
```sql
CREATE TABLE track_statistics (
    track_id VARCHAR(50) PRIMARY KEY REFERENCES tracks(id),
    playlist_id UUID REFERENCES playlists(id),
    total_appearances INTEGER DEFAULT 0,
    highest_position INTEGER,
    lowest_position INTEGER,
    weeks_in_charts INTEGER,
    first_seen_week VARCHAR(10),
    last_seen_week VARCHAR(10),
    average_position DECIMAL(5,2),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(track_id, playlist_id)
);

-- Uppdatera via trigger eller scheduled job
CREATE OR REPLACE FUNCTION update_track_statistics()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO track_statistics (
        track_id, playlist_id, total_appearances, ...
    )
    SELECT 
        track_id,
        playlist_id,
        COUNT(*),
        MIN(position),
        MAX(position),
        COUNT(DISTINCT week),
        MIN(week),
        MAX(week),
        AVG(position)
    FROM chart_entries
    WHERE track_id = NEW.track_id AND playlist_id = NEW.playlist_id
    GROUP BY track_id, playlist_id
    ON CONFLICT (track_id, playlist_id) DO UPDATE SET
        total_appearances = EXCLUDED.total_appearances,
        ...
        updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';
```

---

### 13. Partitionering för Stora Datamängder
**Problem:**
- Om systemet växer till tusentals veckor kan `chart_entries` bli enorm
- Queries kan bli långsamma

**Förbättring:**
```sql
-- Partitionera chart_entries per år
CREATE TABLE chart_entries_2026 PARTITION OF chart_entries
    FOR VALUES FROM ('2026-W01') TO ('2027-W01');

CREATE TABLE chart_entries_2027 PARTITION OF chart_entries
    FOR VALUES FROM ('2027-W01') TO ('2028-W01');

-- Automatisk partitionering via trigger eller scheduled job
```

---

### 14. Fulltext Search för Tracks
**Problem:**
- Ingen möjlighet att söka efter låtar effektivt
- LIKE queries är långsamma

**Förbättring:**
```sql
-- PostgreSQL fulltext search
ALTER TABLE tracks ADD COLUMN search_vector tsvector;

CREATE INDEX tracks_search_idx ON tracks USING GIN(search_vector);

CREATE OR REPLACE FUNCTION tracks_search_trigger() RETURNS trigger AS $$
begin
  new.search_vector :=
    setweight(to_tsvector('english', coalesce(new.title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(
        (SELECT string_agg(a.name, ' ') 
         FROM artists a 
         JOIN track_artists ta ON a.id = ta.artist_id 
         WHERE ta.track_id = new.id), ''
    ), 'B'), 'B');
  return new;
end
$$ LANGUAGE plpgsql;

CREATE TRIGGER tracks_search_update BEFORE INSERT OR UPDATE ON tracks
FOR EACH ROW EXECUTE FUNCTION tracks_search_trigger();
```

---

## 📊 Sammanfattning av Rekommendationer

### Högsta Prioritet:
1. ✅ **Weeks-tabell** - Bättre sortering och validering
2. ✅ **Soft delete** - Förhindra dataförlust
3. ✅ **Window functions** - Förbättra previous_position query
4. ✅ **Composite indexes** - Förbättra prestanda

### Medel Prioritet:
5. ✅ **Track statistics** - Prestanda för vanliga queries
6. ✅ **Audit trail** - Spårbarhet och debugging
7. ✅ **Materialiserade vyer** - Caching av vanliga queries

### Lägsta Prioritet (Framtida):
8. ✅ **Partitionering** - När datamängden blir stor
9. ✅ **Fulltext search** - Om sökfunktion behövs
10. ✅ **Artist aliases** - Om normalisering blir problem

---

## 🎯 Förbättrad Design - Exempel

```sql
-- Förbättrad weeks-tabell
CREATE TABLE weeks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    week_year INTEGER NOT NULL,
    week_number INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    iso_format VARCHAR(10) UNIQUE NOT NULL,
    UNIQUE(week_year, week_number),
    CHECK (week_number >= 1 AND week_number <= 53)
);

-- Förbättrad chart_entries med week_id
CREATE TABLE chart_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    track_id VARCHAR(50) NOT NULL REFERENCES tracks(id) ON DELETE RESTRICT,
    week_id UUID NOT NULL REFERENCES weeks(id),
    position INTEGER NOT NULL CHECK (position > 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id),
    
    UNIQUE(playlist_id, week_id, track_id),
    UNIQUE(playlist_id, week_id, position)
);

-- Index för prestanda
CREATE INDEX idx_chart_entries_playlist_week_position 
  ON chart_entries(playlist_id, week_id, position);

-- Materialiserad vy för previous_position
CREATE MATERIALIZED VIEW chart_entry_with_history AS
SELECT 
    ce.*,
    LAG(ce.position) OVER (
        PARTITION BY ce.playlist_id, ce.track_id 
        ORDER BY w.start_date
    ) AS previous_position
FROM chart_entries ce
JOIN weeks w ON ce.week_id = w.id;
```
