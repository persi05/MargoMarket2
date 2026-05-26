# MargoMarket

MargoMarket to aplikacja marketplace z backendem Spring Boot, frontendem Angular i bazą PostgreSQL. Backend udostępnia REST API pod `/api`, a frontend w trybie developerskim korzysta z proxy do `http://localhost:8080`.


## Wymagania

Do uruchomienia projektu potrzebne są:

- Docker Desktop lub Docker Engine z Docker Compose
- Java 21, jeśli backend ma być uruchamiany lokalnie poza Dockerem
- Node.js i npm, jeśli ma być uruchamiany frontend

## Konfiguracja

1. Skopiuj plik przykładowej konfiguracji:

   ```powershell
   Copy-Item .env.example .env
   ```

2. W razie potrzeby zmień wartości w `.env`.

Migracje bazy danych znajdują się w `src/main/resources/db/migration` i uruchamiają się automatycznie przez Flyway przy starcie backendu.

## Szybkie uruchomienie

Ten wariant uruchamia backend i PostgreSQL w Dockerze.

```powershell
docker compose up --build
```

Po starcie:

- API: `http://localhost:8080/api`
- PostgreSQL: `localhost:5432`

Frontend uruchom osobno:

```powershell
cd frontend
npm install
npm start
```

Frontend będzie dostępny pod adresem:

```text
http://localhost:4200
```


Na podstawie mojego poprzedniego projektu:
https://github.com/persi05/MargoMarket
