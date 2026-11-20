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

## Aktualizacja katalogu przedmiotów

Administrator uruchamia aktualizację w panelu administracyjnym: **Katalog przedmiotów → Aktualizuj katalog**. Backend pobiera katalog z MargoWorld w tle i pokazuje status oraz liczbę nowych i zaktualizowanych przedmiotów. Nie ma harmonogramu. Nie jest potrzebny restart ani przebudowa aplikacji; kolejne wyszukiwania korzystają z nowych danych.

Pobieranie i walidacja poprzedzają zapis. Błąd źródła lub importu zachowuje dotychczasowe dane. Import aktualizuje przedmioty po identyfikatorze zewnętrznym, zachowuje ich lokalne ID i powiązania z ogłoszeniami. Brak przedmiotu w źródle nie usuwa go z bazy. Baza blokuje równoczesne aktualizacje; przerwany import można ponowić po 5 minutach bez postępu.

Plik `src/main/resources/data/margoworld-market-items.json` jest wyłącznie katalogiem startowym, wczytywanym jednorazowo. Późniejszy restart nie nadpisuje katalogu z bazy. Skrypt `node scripts/update-margoworld-items.mjs` służy opcjonalnie do odświeżania tego pliku w repozytorium dla nowych instalacji. Na hostingu PostgreSQL musi mieć trwałe dane; lokalny Docker Compose używa wolumenu.

## Testy backendu

Testy jednostkowe (Java 21):

```powershell
.\mvnw.cmd test
```

Pełna weryfikacja, w tym testy migracji na PostgreSQL (Java 21 i uruchomiony Docker):

```powershell
.\mvnw.cmd verify
```

Testy migracji są w `src/test/java/com/margomarket/migration`. Maven Failsafe uruchamia klasy `*IT` w fazie `verify`. Każda klasa uruchamia własny PostgreSQL przez Testcontainers, wykonuje migracje Flyway i sprawdza ograniczenia bazy. Po testach kontenery są usuwane. Dane aplikacji nie są używane. Wyniki znajdują się w `target/failsafe-reports`.

`AuctionIntegrationIT` także uruchamia własnego PostgreSQL przez Testcontainers i wykonuje się w ramach `verify`. Nie wymaga zmiennych środowiskowych ani ręcznego tworzenia bazy. Testy zegara dyskusji znajdują się obok kodu frontendu i uruchamiają się przez `npm test -- --watch=false`.

## CI na GitHubie

Workflow `.github/workflows/ci.yml` uruchamia się przy pushu i pull requeście. Po dodaniu workflowu do domyślnej gałęzi można go też uruchomić ręcznie z zakładki Actions. Backend jest sprawdzany na Javie 21 poleceniem `./mvnw verify`, łącznie z testami migracji i licytacji na tymczasowych bazach PostgreSQL. Frontend jest sprawdzany na Node.js 24 przez `npm ci`, testy Vitest i build Angulara. Raporty testów oraz pliki wynikowe są zachowywane jako artefakty workflowu. CI nie wdraża aplikacji na serwer.
