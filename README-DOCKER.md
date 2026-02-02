# Docker Setup

Detta projekt kan köras med Docker för enkel setup och deployment.

## Krav

- Docker
- Docker Compose

## Snabbstart

1. **Bygg och starta alla services:**
   ```bash
   docker-compose up --build
   ```

2. **Öppna applikationen:**
   - Frontend: http://localhost:3001
   - Backend API: http://localhost:8080

## Kommandon

### Starta services
```bash
docker-compose up
```

### Starta i bakgrunden
```bash
docker-compose up -d
```

### Stoppa services
```bash
docker-compose down
```

### Se logs
```bash
docker-compose logs -f
```

### Bygga om efter ändringar
```bash
docker-compose up --build
```

## Data-filer

JSON-filerna i `data/` mappen mountas som read-only volumes, så du kan redigera dem lokalt och de uppdateras automatiskt i containern.

## Utveckling

För utveckling rekommenderas att köra frontend och backend separat (utan Docker) för snabbare hot-reload:

**Backend:**
```bash
cd backend
mvn spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm run dev
```

Docker är bäst för:
- Production deployment
- Demo/testmiljöer
- Delning med andra som inte har Java/Node installerat
