# Data Directory

Lägg JSON-filer här för att importera chart-data.

## Struktur

```
data/
├── Walter/
│   ├── 2026-W04.json
│   ├── 2026-W05.json
│   └── ...
└── Signe/
    ├── 2026-W04.json
    ├── 2026-W05.json
    └── ...
```

## Filnamn

Filer ska heta `YYYY-Www.json` där:
- `YYYY` = år (t.ex. 2026)
- `Www` = veckonummer med ledande nolla (t.ex. W04, W05)

Exempel: `2026-W04.json`, `2026-W05.json`

## JSON-format

Varje fil ska ha följande format:

```json
{
  "week": "2026-W04",
  "entries": [
    {
      "placement": 1,
      "track_id": "0KKkJNfGyhkQ5aFogxQAPU",
      "title": "That's What I Like",
      "artists": ["Bruno Mars"],
      "spotify_url": "https://open.spotify.com/track/0KKkJNfGyhkQ5aFogxQAPU"
    },
    ...
  ]
}
```

## Importera filer

1. Lägg JSON-filer i rätt mapp (`Walter/` eller `Signe/`)
2. Öppna frontend och välj användare
3. Klicka på "📥 Importera filer"
4. Välj vecka i dropdown-menyn

Filer importeras automatiskt varje timme via scheduler, eller manuellt via frontend-knappen.
