# Databassdesign för Top 50 Charts

## Översikt
Designen fokuserar på att:
- Normalisera Track-data (en låt = ett objekt, oavsett hur många listor den är med i)
- Stödja flera användare med konton
- Stödja flera listor per användare (framtida funktion)
- Behålla funktionaliteten att spåra låtars förflyttningar över tid
- Möjliggöra att se andras publika listor

## Entiteter

### 1. User (Användare)
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,  -- För framtida auth
    display_name VARCHAR(100),
    is_public BOOLEAN DEFAULT false,  -- Om användarens listor är publika som standard
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Användning:**
- Varje person (Walter, Signe, etc.) blir en User
- `is_public` avgör om deras listor är synliga för andra
- `display_name` kan vara "Walter" eller "Walter's Charts"

---

### 2. Playlist/Chart (Lista)
```sql
CREATE TABLE playlists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,  -- t.ex. "Walter's Top 50", "Signe's Top 50"
    description TEXT,
    is_public BOOLEAN DEFAULT false,  -- Överstyr user.is_public om specifik lista ska vara privat/publik
    is_active BOOLEAN DEFAULT true,  -- Om listan fortfarande används
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(user_id, name)  -- En användare kan inte ha två listor med samma namn
);
```

**Användning:**
- Varje användare kan ha en eller flera listor
- I första versionen: en lista per användare
- I framtiden: flera listor (t.ex. "Walter's Top 50", "Walter's Discover Weekly")

---

### 3. Track (Låt - Global)
```sql
CREATE TABLE tracks (
    id VARCHAR(50) PRIMARY KEY,  -- Spotify track ID (t.ex. "0KKkJNfGyhkQ5aFogxQAPU")
    title VARCHAR(255) NOT NULL,
    spotify_url VARCHAR(500) NOT NULL,
    image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Användning:**
- En låt sparas EN gång, oavsett hur många listor den är med i
- Spotify track ID används som primärnyckel (redan unik)
- Uppdateras automatiskt om låtinfo ändras

---

### 4. Artist (Artist - Global)
```sql
CREATE TABLE artists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Användning:**
- Normaliserad artistdata
- Förhindrar duplicering av artistnamn

---

### 5. TrackArtist (Många-till-många relation)
```sql
CREATE TABLE track_artists (
    track_id VARCHAR(50) NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    artist_id UUID NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,  -- Ordning (0 = huvudartist, 1 = första featured, etc.)
    
    PRIMARY KEY (track_id, artist_id),
    UNIQUE(track_id, position)  -- En låt kan inte ha två artister på samma position
);
```

**Användning:**
- Hanterar låtar med flera artister (t.ex. "The Weeknd, JENNIE, Lily-Rose Depp")
- `position` behåller ordningen

---

### 6. ChartEntry (Placering i en lista för en vecka)
```sql
CREATE TABLE chart_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    track_id VARCHAR(50) NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    week VARCHAR(10) NOT NULL,  -- Format: "2026-W05"
    position INTEGER NOT NULL CHECK (position > 0 AND position <= 50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(playlist_id, week, track_id),  -- En låt kan bara vara med en gång per vecka per lista
    UNIQUE(playlist_id, week, position)   -- En position kan bara ha en låt per vecka per lista
);
```

**Användning:**
- Detta är kärnan i systemet
- Varje rad = en låts placering i en specifik vecka på en specifik lista
- `UNIQUE` constraints förhindrar duplicering och säkerställer dataintegritet

---

## Index för prestanda

```sql
-- Snabb uppslagning av veckor för en lista
CREATE INDEX idx_chart_entries_playlist_week ON chart_entries(playlist_id, week);

-- Snabb uppslagning av en låts historia
CREATE INDEX idx_chart_entries_track_week ON chart_entries(track_id, week);

-- Snabb uppslagning av användarens listor
CREATE INDEX idx_playlists_user ON playlists(user_id);

-- Snabb uppslagning av publika listor
CREATE INDEX idx_playlists_public ON playlists(is_public) WHERE is_public = true;
```

---

## Exempel: Hur data lagras

### Scenario: Walter har "That's What I Like" på plats 1 vecka 2026-W05

1. **Track** (sparas EN gång):
   ```json
   {
     "id": "0KKkJNfGyhkQ5aFogxQAPU",
     "title": "That's What I Like",
     "spotify_url": "https://open.spotify.com/track/0KKkJNfGyhkQ5aFogxQAPU",
     "image_url": "..."
   }
   ```

2. **Artist** (sparas EN gång):
   ```json
   {
     "id": "uuid-123",
     "name": "Bruno Mars"
   }
   ```

3. **TrackArtist** (kopplar låt till artist):
   ```json
   {
     "track_id": "0KKkJNfGyhkQ5aFogxQAPU",
     "artist_id": "uuid-123",
     "position": 0
   }
   ```

4. **ChartEntry** (sparas för varje vecka låten är med):
   ```json
   {
     "playlist_id": "walter-playlist-uuid",
     "track_id": "0KKkJNfGyhkQ5aFogxQAPU",
     "week": "2026-W05",
     "position": 1
   }
   ```

Om samma låt är med på Signes lista samma vecka:
- **Track** sparas INTE igen (använder samma)
- **Artist** sparas INTE igen (använder samma)
- **ChartEntry** sparas NYTT objekt:
   ```json
   {
     "playlist_id": "signe-playlist-uuid",
     "track_id": "0KKkJNfGyhkQ5aFogxQAPU",  // Samma track_id!
     "week": "2026-W05",
     "position": 3
   }
   ```

---

## Queries för vanliga operationer

### 1. Hämta alla veckor för en lista
```sql
SELECT DISTINCT week 
FROM chart_entries 
WHERE playlist_id = ? 
ORDER BY week DESC;
```

### 2. Hämta en specifik veckas lista
```sql
SELECT 
    ce.position,
    ce.week,
    t.id AS track_id,
    t.title,
    t.spotify_url,
    t.image_url,
    COALESCE(
        (SELECT position 
         FROM chart_entries 
         WHERE playlist_id = ce.playlist_id 
           AND track_id = ce.track_id 
           AND week < ce.week 
         ORDER BY week DESC 
         LIMIT 1),
        NULL
    ) AS previous_position
FROM chart_entries ce
JOIN tracks t ON ce.track_id = t.id
WHERE ce.playlist_id = ? AND ce.week = ?
ORDER BY ce.position;
```

### 3. Hämta en låts historia över tid
```sql
SELECT 
    ce.week,
    ce.position,
    p.name AS playlist_name,
    u.username
FROM chart_entries ce
JOIN playlists p ON ce.playlist_id = p.id
JOIN users u ON p.user_id = u.id
WHERE ce.track_id = ? AND p.user_id = ?
ORDER BY ce.week;
```

### 4. Hämta alla publika listor
```sql
SELECT 
    p.id,
    p.name,
    u.username,
    u.display_name,
    COUNT(DISTINCT ce.week) AS week_count
FROM playlists p
JOIN users u ON p.user_id = u.id
LEFT JOIN chart_entries ce ON p.id = ce.playlist_id
WHERE p.is_public = true OR u.is_public = true
GROUP BY p.id, p.name, u.username, u.display_name
ORDER BY week_count DESC;
```

### 5. Hämta "dropped tracks" (låtar som åkt ur listan)
```sql
-- Låtar som var med förra veckan men inte denna veckan
SELECT 
    ce_prev.track_id,
    ce_prev.position AS previous_position,
    t.title,
    t.spotify_url,
    t.image_url
FROM chart_entries ce_prev
JOIN tracks t ON ce_prev.track_id = t.id
WHERE ce_prev.playlist_id = ?
  AND ce_prev.week = ?  -- Förra veckan
  AND NOT EXISTS (
      SELECT 1 
      FROM chart_entries ce_current
      WHERE ce_current.playlist_id = ce_prev.playlist_id
        AND ce_current.track_id = ce_prev.track_id
        AND ce_current.week = ?  -- Denna veckan
  );
```

---

## Migration från JSON-filer

### Steg 1: Skapa användare
```sql
INSERT INTO users (username, display_name, is_public) 
VALUES ('walter', 'Walter', true);

INSERT INTO users (username, display_name, is_public) 
VALUES ('signe', 'Signe', true);
```

### Steg 2: Skapa listor
```sql
INSERT INTO playlists (user_id, name, is_public)
SELECT id, 'Top 50 Charts', true
FROM users WHERE username = 'walter';

INSERT INTO playlists (user_id, name, is_public)
SELECT id, 'Top 50 Charts', true
FROM users WHERE username = 'signe';
```

### Steg 3: Importera tracks och chart entries
```python
# Pseudokod för import
for user in ['Walter', 'Signe']:
    for week_file in glob(f'data/{user}/*.json'):
        week = extract_week_from_filename(week_file)
        playlist_id = get_playlist_id_for_user(user)
        
        for entry in json_data['entries']:
            # 1. Spara eller uppdatera track
            track_id = entry['track_id']
            upsert_track(track_id, entry)
            
            # 2. Spara eller uppdatera artister
            for artist_name in entry['artists']:
                artist_id = upsert_artist(artist_name)
                upsert_track_artist(track_id, artist_id, position)
            
            # 3. Spara chart entry
            insert_chart_entry(
                playlist_id=playlist_id,
                track_id=track_id,
                week=week,
                position=entry['placement']
            )
```

---

## Fördelar med denna design

1. **Normalisering**: En låt sparas en gång, oavsett antal listor/veckor
2. **Skalbarhet**: Stödjer flera användare och flera listor per användare
3. **Dataintegritet**: UNIQUE constraints förhindrar duplicering och felaktig data
4. **Prestanda**: Index gör queries snabba även med många veckor/låtar
5. **Flexibilitet**: Lätt att lägga till nya funktioner (t.ex. favoriter, kommentarer)
6. **Framtidssäker**: Stödjer redan flera listor per användare

---

## Ytterligare förbättringar (framtida)

### Favoriter
```sql
CREATE TABLE user_track_favorites (
    user_id UUID REFERENCES users(id),
    track_id VARCHAR(50) REFERENCES tracks(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, track_id)
);
```

### Kommentarer på listor
```sql
CREATE TABLE playlist_comments (
    id UUID PRIMARY KEY,
    playlist_id UUID REFERENCES playlists(id),
    user_id UUID REFERENCES users(id),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Statistik
```sql
CREATE TABLE track_statistics (
    track_id VARCHAR(50) REFERENCES tracks(id),
    total_appearances INTEGER DEFAULT 0,
    highest_position INTEGER,
    weeks_in_charts INTEGER,
    last_seen_week VARCHAR(10),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
