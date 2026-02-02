# Database Setup och Import Guide

## Översikt

Systemet använder nu MySQL-databas istället för JSON-filer. Alla ändringar är implementerade och redo att användas.

## Starta Systemet

### Enkel start (rekommenderat)

Kör helt enkelt:

```bash
docker-compose up -d
```

Detta kommer att:
1. Starta MySQL och vänta tills den är redo
2. Starta Backend som automatiskt:
   - Skapar databasstrukturen via Flyway migrations
   - Importerar alla JSON-filer från `data/Walter/` och `data/Signe/` (om databasen är tom)
   - Skapar användare, listor, tracks, artister och chart entries
3. Starta Frontend

Alla services startar på:
- MySQL: port 3307 (externt, 3306 internt i Docker)
- Backend: port 8080
- Frontend: port 3001

### Import-beteende

Importen körs automatiskt om:
- Databasen är tom (första gången)
- Eller om `IMPORT_DATA=true` är satt

För att tvinga import även om data finns:
```bash
IMPORT_DATA=true docker-compose up -d
```

För att förhindra import:
```bash
IMPORT_DATA=false docker-compose up -d
```

## Databasstruktur

Databasen innehåller följande tabeller:

- **users** - Användare (Walter, Signe, etc.)
- **playlists** - Listor (en per användare för nu)
- **weeks** - Veckor med ISO-format och datum
- **tracks** - Global låtdatabas
- **artists** - Global artistdatabas
- **track_artists** - Koppling mellan låtar och artister
- **chart_entries** - Placeringar per vecka (kärnan i systemet)
- **track_statistics** - Automatisk statistik (uppdateras via triggers)

## Importera Data

### Automatisk Import (vid start)

Sätt system property när du startar backend:

```bash
mvn spring-boot:run -Dimport.data=true
```

### Manuell Import (via API - framtida)

En API-endpoint kan läggas till för att importera nya veckor.

## Verifiera Import

Efter importen kan du verifiera att data är korrekt:

```bash
# Koppla till MySQL (från Docker)
docker exec -it top50-mysql mysql -u top50 -ptop50password top50_charts

# Eller från host (om du har mysql-klient installerad)
mysql -h 127.0.0.1 -P 3307 -u top50 -ptop50password top50_charts

# Kolla antal användare
SELECT * FROM users;

# Kolla antal veckor
SELECT COUNT(*) FROM weeks;

# Kolla antal chart entries
SELECT COUNT(*) FROM chart_entries;

# Kolla en specifik vecka
SELECT ce.position, t.title, w.iso_format
FROM chart_entries ce
JOIN tracks t ON ce.track_id = t.id
JOIN weeks w ON ce.week_id = w.id
WHERE w.iso_format = '2026-W05'
ORDER BY ce.position
LIMIT 10;
```

## Konfiguration

### Databasanslutning

Standardinställningar i `application.properties`:
- URL: `jdbc:mysql://localhost:3306/top50_charts` (för lokal utveckling)
- Username: `top50`
- Password: `top50password`

**OBS:** I Docker använder backend port 3306 (internt i Docker-nätverket), men externt exponeras MySQL på port 3307 för att undvika konflikter med lokal MySQL-installation.

För Docker Compose används miljövariabler:
- `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/top50_charts?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- `SPRING_DATASOURCE_USERNAME=top50`
- `SPRING_DATASOURCE_PASSWORD=top50password`

### Flyway Migrations

Migrations körs automatiskt vid start. Filer finns i:
`backend/src/main/resources/db/migration/`

## Felsökning

### MySQL startar inte
```bash
docker-compose logs mysql
```

### Import körs inte
Kontrollera att:
1. `-Dimport.data=true` är satt
2. `data/` mappen finns och innehåller JSON-filer
3. MySQL är igång och tillgänglig

### Databasanslutningsfel
Kontrollera:
1. MySQL container är igång: `docker-compose ps`
2. Port 3306 är tillgänglig
3. Användarnamn/lösenord stämmer

## Nästa Steg

Efter att data är importerad:
1. Systemet använder nu databas istället för JSON-filer
2. Alla API-endpoints fungerar som tidigare
3. Frontend behöver ingen ändring

## Backup

För att säkerhetskopiera databasen:

```bash
docker exec top50-mysql mysqldump -u top50 -ptop50password top50_charts > backup.sql
```

För att återställa:

```bash
docker exec -i top50-mysql mysql -u top50 -ptop50password top50_charts < backup.sql
```
