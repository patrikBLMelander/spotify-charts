# Railway Environment Variables

Kopiera dessa variabler till ditt Railway-projekt under Settings → Variables.

## Backend Environment Variables

Sätt dessa variabler i ditt Railway-projekt för att backend ska kunna ansluta till MySQL:

```
SPRING_DATASOURCE_URL=jdbc:${{MYSQL_URL}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=${{MYSQLUSER}}
SPRING_DATASOURCE_PASSWORD=${{MYSQL_ROOT_PASSWORD}}
```

Eller om du vill använda de specifika värdena direkt:

```
SPRING_DATASOURCE_URL=jdbc:mysql://root:JVqmzqvHwpSUAzhXBhOmzdNKNOIaKnNe@${{RAILWAY_PRIVATE_DOMAIN}}:3306/railway?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=JVqmzqvHwpSUAzhXBhOmzdNKNOIaKnNe
```

## Optional Variables

Om du vill importera JSON-data automatiskt vid första start:

```
IMPORT_DATA=true
DATA_DIRECTORY=/app/data
```

## Notes

- `${{MYSQL_URL}}` är en Railway-variabel som innehåller hela connection stringen
- `${{RAILWAY_PRIVATE_DOMAIN}}` är Railway's privata domän för databasen
- `${{MYSQLUSER}}` är användarnamnet (vanligtvis "root")
- `${{MYSQL_ROOT_PASSWORD}}` är lösenordet som Railway genererar

## How to Set in Railway

1. Gå till ditt Railway-projekt
2. Klicka på "Variables" i menyn
3. Klicka på "New Variable"
4. Lägg till varje variabel ovan
5. Spara och deploya om
