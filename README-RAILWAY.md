# Deploy på Railway

Detta projekt är konfigurerat för att köras på Railway.

## Snabbstart

### 1. Installera Railway CLI (valfritt)
```bash
npm i -g @railway/cli
railway login
```

### 2. Deploy via Railway Dashboard

1. Gå till [railway.app](https://railway.app)
2. Klicka på "New Project"
3. Välj "Deploy from GitHub repo" (om du har pushat till GitHub) eller "Empty Project"
4. Om du valde "Empty Project":
   - Klicka på "New" → "GitHub Repo" och välj ditt repo
   - Eller klicka på "New" → "Empty Project" och koppla GitHub senare

5. Railway kommer automatiskt:
   - Detektera `railway.toml` eller `Dockerfile.railway`
   - Bygga och deploya applikationen

### 3. Konfigurera Environment Variables

I Railway Dashboard, gå till ditt projekt → Variables:

```
DATA_DIRECTORY=/app/data
```

### 4. Lägg till Persistent Volume för Data

1. I Railway Dashboard, gå till ditt projekt
2. Klicka på "New" → "Volume"
3. Namn: `data`
4. Mount path: `/app/data`
5. Koppla volymen till din service

### 5. Ladda upp JSON-filer

Du har två alternativ:

**Alternativ A: Via Railway CLI**
```bash
railway link  # Länka till ditt projekt
railway run sh  # Öppna shell i containern
# Skapa mappar och ladda upp filer
mkdir -p /app/data/Walter /app/data/Signe
# Kopiera dina JSON-filer hit
```

**Alternativ B: Via Railway Dashboard**
1. Gå till ditt projekt → Volumes
2. Klicka på volymen
3. Använd filhanteraren för att ladda upp JSON-filer

**Alternativ C: Via Git (rekommenderat)**
1. Pusha dina JSON-filer till GitHub
2. Railway kommer automatiskt deploya dem
3. JSON-filerna kommer finnas i `/app/data` efter deploy

## Deploy via CLI

```bash
# Länka till projekt
railway link

# Deploy
railway up

# Se logs
railway logs

# Öppna shell
railway shell
```

## Projektstruktur på Railway

```
/app/
├── app.jar              # Spring Boot backend
├── data/                # JSON-filer (persistent volume)
│   ├── Walter/
│   └── Signe/
└── /usr/share/nginx/html/  # React frontend (byggd)
```

## Ports

- **Port 80**: Nginx serverar frontend och proxar API-anrop
- **Port 8080**: Spring Boot backend (internt)

Railway kommer automatiskt tilldela en publik URL.

## Environment Variables

| Variable | Beskrivning | Default |
|----------|-------------|---------|
| `DATA_DIRECTORY` | Sökväg till data-mappen | `/app/data` |
| `PORT` | Port för applikationen (Railway sätter detta automatiskt) | `80` |

## Troubleshooting

### JSON-filer syns inte
- Kontrollera att volymen är monterad på `/app/data`
- Kontrollera att `DATA_DIRECTORY` environment variable är satt
- Kontrollera logs: `railway logs`

### Backend startar inte
- Kontrollera logs: `railway logs`
- Se till att Java 17 är tillgängligt (hanteras av Dockerfile)

### Frontend visar inte data
- Kontrollera att backend körs (port 8080)
- Kontrollera Nginx-konfigurationen
- Kontrollera browser console för fel

## Uppdatera efter ändringar

Railway deployar automatiskt när du pushar till GitHub. Du kan också manuellt deploya:

```bash
railway up
```

## Kostnad

Railway har en gratis tier med:
- $5 gratis kredit per månad
- 500 timmar runtime
- 5GB storage

För detta projekt bör det räcka gott och väl!
