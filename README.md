# Spotify Top 50 Charts

Ett fullstack-system för att spåra och visualisera hur låtar rör sig i Spotify-topplistan över tid.

## 🏗️ Arkitektur

- **Backend**: Java Spring Boot med REST API
- **Frontend**: React med Vite
- **Data**: JSON-filer (ingen databas behövs)
- **Visualisering**: Recharts för linjediagram
- **Deployment**: Railway-ready (se [README-RAILWAY.md](README-RAILWAY.md))

## 📋 Förutsättningar

- Java 17 eller senare
- Maven 3.6+
- Node.js 18+ och npm
- (Valfritt) PostgreSQL om du vill använda det istället för SQLite

## 🚀 Snabbstart

### Backend

1. Navigera till backend-mappen:
```bash
cd backend
```

2. Bygg projektet:
```bash
mvn clean install
```

3. Starta backend-servern:
```bash
mvn spring-boot:run
```

Backend-servern körs på `http://localhost:8080`

### Frontend

1. Navigera till frontend-mappen:
```bash
cd frontend
```

2. Installera beroenden:
```bash
npm install
```

3. Starta utvecklingsservern:
```bash
npm run dev
```

Frontend-appen öppnas på `http://localhost:3000`

## 📊 Datamodell

### Track
- `id` (String, PK) - Spotify track_id
- `title` (String) - Låtens titel
- `artists` (String[]) - Artister
- `spotifyUrl` (String) - Spotify URL

### WeeklyChartEntry
- `id` (Long, PK)
- `week` (String) - ISO-format: YYYY-Www
- `position` (int) - Placering i listan
- `track` (ManyToOne -> Track)

**Unik constraint**: `(week, track_id)` förhindrar dubletter vid re-import

## 🔌 API Endpoints

### POST /api/import
Importerar en Spotify-playlist för en specifik vecka.

**Request body:**
```json
{
  "playlistUrl": "https://open.spotify.com/playlist/...",
  "week": "2026-W05"
}
```

**Exempel:**
```bash
curl -X POST http://localhost:8080/api/import \
  -H "Content-Type: application/json" \
  -d '{
    "playlistUrl": "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
    "week": "2026-W05"
  }'
```

### GET /api/tracks
Hämtar alla spårade låtar.

**Response:**
```json
[
  {
    "id": "track_id",
    "title": "Låtens titel",
    "artists": ["Artist 1", "Artist 2"],
    "spotifyUrl": "https://open.spotify.com/track/..."
  }
]
```

### GET /api/tracks/{trackId}/history
Hämtar placeringshistorik för en specifik låt.

**Response:**
```json
{
  "track": {
    "id": "track_id",
    "title": "Låtens titel",
    "artists": ["Artist 1"],
    "spotifyUrl": "https://open.spotify.com/track/..."
  },
  "history": [
    {
      "week": "2026-W01",
      "position": 5
    },
    {
      "week": "2026-W02",
      "position": 3
    }
  ]
}
```

### GET /api/chart?week=2026-W05
Hämtar hela topplistan för en specifik vecka.

## 📥 Importera en ny vecka

### Via Frontend

1. Öppna frontend-appen i webbläsaren
2. Fyll i Spotify-playlistens URL
3. (Valfritt) Ange vecka i formatet YYYY-Www, eller lämna tomt för aktuell vecka
4. Klicka på "Importera"

### Via API

Använd POST `/api/import` enligt exempel ovan.

**Viktigt**: Importen är idempotent - du kan köra samma import flera gånger utan att skapa dubletter.

## 🗄️ Databas-konfiguration

### SQLite (Standard)

SQLite används som standard och kräver ingen konfiguration. Databasfilen `spotify_charts.db` skapas automatiskt i backend-mappen.

### PostgreSQL

För att använda PostgreSQL istället:

1. Redigera `backend/src/main/resources/application.properties`
2. Kommentera bort SQLite-raderna
3. Avkommentera PostgreSQL-raderna:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/spotify_charts
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

4. Skapa databasen:
```sql
CREATE DATABASE spotify_charts;
```

## 🎨 Frontend-funktioner

- **Importformulär**: Importera nya veckor enkelt
- **Låtval**: Välj vilka låtar som ska visas i diagrammet
- **Linjediagram**: 
  - X-axel: Vecka (YYYY-Www)
  - Y-axel: Placering (inverterad, så 1 = topp)
  - Färgkodning per låt
  - Möjlighet att visa flera låtar samtidigt
  - Interaktiv tooltip och legend

## 🎵 Spotify API Integration

Systemet använder **Spotify Web API** med **Client Credentials flow** för att hämta playlist-data. Integrationen är implementerad och redo att användas!

### Konfiguration

1. **Skapa ett Spotify Developer-konto**:
   - Gå till https://developer.spotify.com/dashboard
   - Logga in med ditt Spotify-konto

2. **Skapa en app**:
   - Klicka på "Create app"
   - Fyll i namn och beskrivning
   - Välj "Web API" som API-typ
   - Acceptera användarvillkoren

3. **Hämta credentials**:
   - Efter att appen är skapad, kopiera **Client ID** och **Client Secret**

4. **Konfigurera i backend**:
   
   **Alternativ 1: Via environment variables (rekommenderat)**
   ```bash
   export SPOTIFY_CLIENT_ID="ditt_client_id"
   export SPOTIFY_CLIENT_SECRET="ditt_client_secret"
   ```
   
   **Alternativ 2: Via application.properties**
   Redigera `backend/src/main/resources/application.properties`:
   ```properties
   spotify.client-id=ditt_client_id
   spotify.client-secret=ditt_client_secret
   ```

### Hur det fungerar

Systemet använder **OAuth 2.0 Client Credentials flow** ([dokumentation](https://developer.spotify.com/documentation/web-api/tutorials/client-credentials-flow)):

1. Backend autentiserar med Spotify API med Client ID och Secret
2. Får ett access token som är giltigt i 1 timme
3. Använder tokenet för att hämta playlist-data från Spotify Web API
4. Tokenet förnyas automatiskt när det går ut

### API Endpoints som används

- `POST https://accounts.spotify.com/api/token` - För att få access token
- `GET https://api.spotify.com/v1/playlists/{playlist_id}/tracks` - För att hämta låtar från en playlist

Se [Spotify Web API dokumentation](https://developer.spotify.com/documentation/web-api) för mer information.

## 🛠️ Utveckling

### Backend-struktur

```
backend/
├── src/main/java/com/top50/
│   ├── entity/          # JPA-entiteter
│   ├── repository/      # Data access layer
│   ├── service/         # Business logic
│   ├── controller/      # REST endpoints
│   └── dto/             # Data transfer objects
└── src/main/resources/
    ├── application.properties
    └── db/migration/    # Flyway migrations
```

### Frontend-struktur

```
frontend/
├── src/
│   ├── components/      # React-komponenter
│   ├── App.jsx          # Huvudkomponent
│   └── main.jsx         # Entry point
└── package.json
```

## 🧪 Testning

### Backend
```bash
cd backend
mvn test
```

### Frontend
```bash
cd frontend
npm test  # Om test-script finns
```

## 🚂 Deployment på Railway

Projektet är konfigurerat för enkel deployment på Railway. Se [README-RAILWAY.md](README-RAILWAY.md) för detaljerade instruktioner.

**Snabbstart:**
1. Pusha till GitHub
2. Skapa nytt projekt på [railway.app](https://railway.app)
3. Koppla GitHub-repo
4. Railway deployar automatiskt!

## 📝 Licens

Detta projekt är skapat för utbildningssyfte.

## 🔮 Framtida förbättringar

- [x] Visa låtar som åkt ur listan
- [x] Album thumbnails med länkar
- [x] Railway deployment
- [ ] Exportera data som CSV/JSON
- [ ] Sökfunktion för låtar
- [ ] Automatisk veckovis import (scheduler)
