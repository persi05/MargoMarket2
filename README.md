# MargoMarket

## Publiczne profile i potwierdzenia wymian

Profile są dostępne pod `/u/{id}/profil`. Pokazują aktywne ogłoszenia,
statusy sprzedaży i osobno wymiany jawnie potwierdzone przez przypisanego
administratora. Historyczne zamknięcia rozmów nie są importowane jako potwierdzenia.
Potwierdzenie nie gwarantuje bezpieczeństwa kolejnych transakcji.

Przypisany pośrednik potwierdza wykonaną wymianę przyciskiem w rozmowie.
Zwykłe zamknięcie rozmowy nie potwierdza wymiany i nie oznacza automatycznie
sprzedaży. Potwierdzenia można przeglądać i unieważniać z uzasadnieniem w panelu
administracyjnym. Rejestr ma unikalne powiązania z rozmową i ogłoszeniem.
Usunięcie potwierdzonego ogłoszenia przez administratora archiwizuje je.

Publiczne odpowiedzi nie udostępniają e-maila sprzedawcy. Historyczne pseudonimy
zawierające `@` zastępuje nazwa `Gracz {id}`. Ceny i kontakt nadal wymagają logowania.
Awatary pozostają inicjałami; nie dodano przesyłania zdjęć ani ocen użytkowników.

Migracja V26 wykonuje się przy starcie aplikacji przez Flyway. Przed publicznym
wdrożeniem należy zatwierdzić zasady retencji rejestru i audytu oraz informacje
o publicznych profilach w polityce prywatności.

Weryfikacja (z katalogu repozytorium):

```powershell
.\mvnw.cmd test
docker build -f scripts/Dockerfile.profile-tests -t margomarket-public-profile-tests .
powershell -File scripts/test-profile-migration.ps1
npm.cmd --prefix frontend test -- --watch=false
npm.cmd --prefix frontend run build
node scripts/test-discussion-posting-clock.mjs
```

Test migracji uruchamia jednorazowy PostgreSQL i nie łączy się z bazą aplikacji.
Obraz testowy backendu wymaga Dockera; lokalne Maven wymaga Java 21.

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

Uruchomienie skryptu do pobrania przedmiotów z margoworld:

```powershell
node scripts\update-margoworld-items.mjs
```
