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

Uruchomienie skryptu do pobrania przedmiotów z margoworld:

```powershell
node scripts\update-margoworld-items.mjs
```

## Moderacja treści

Administrator zarządza listą w panelu administracji, w zakładce „Zakazane słowa”.
Może dodawać i edytować słowa lub frazy, wyłączać wpisy i je usuwać.
Początkowa lista polskich i angielskich przekleństw oraz wybranych obelg znajduje się w
`src/main/resources/db/migration/V14__create_blocked_words.sql` i jest wczytywana przez Flyway tylko raz.
Późniejsze zmiany zapisują się w bazie danych i nie znikają po restarcie.

Filtr ignoruje wielkość liter oraz akcenty, w tym polskie znaki. Tryb `EXACT` dopasowuje
całe słowo lub frazę, a `PREFIX` cały wyraz zaczynający się od wpisanego rdzenia.
Prefiksy mogą obejmować również neutralne wyrazy — administrator może doprecyzować lub wyłączyć regułę.
Filtr nie rozpoznaje wszystkich sposobów obchodzenia cenzury (np. rozdzielania liter symbolami).

Dyskusje zwracają wyłącznie przefiltrowaną treść: pasujące znaki zastępowane są `*`.
Treść jest filtrowana przez backend dopiero po naciśnięciu Wyślij lub Enter,
bez podglądu i ostrzeżeń podczas pisania. Zakazane słowa nie blokują pierwszego wysłania.
Backend zapisuje wiadomość już z gwiazdkami. Starsze komentarze zapisane przed tą zmianą
mogą nadal zawierać oryginały, dlatego przy pobieraniu również stosowane są aktualne reguły.
Już otwarta dyskusja wymaga ponownego wczytania starszych wiadomości.

Zwykły użytkownik może wysłać jedną wiadomość co 10 minut
w danej dyskusji. Limit jest niezależny między ogłoszeniami i zapisany w bazie przez
`V15__create_discussion_cooldowns.sql`. Usunięcie komentarza i odświeżenie strony nie
zerują limitu. Rezerwacja limitu i zapis komentarza
odbywają się w jednej transakcji; równoczesne wysłania również podlegają ograniczeniu.
Odrzucone wysłanie zwraca HTTP 429, nagłówek `Retry-After` i `retryAfterSeconds` w JSON.

Wysłanie wiadomości zawierającej zakazane słowo zapisuje ją z gwiazdkami i nakłada
na autora 12 godzin wyciszenia we wszystkich dyskusjach. Administrator nie podlega
cooldownowi ani wyciszeniu, ale jego wiadomości również są gwiazdkowane.
Po wysłaniu formularz pokazuje komunikat o nałożonej blokadzie. W każdej dyskusji
widać jej koniec z datą, godziną i oznaczeniem lokalnej strefy czasowej oraz odliczanie.
Wyciszenie przechowywane jest w tabeli `discussion_mutes` z migracji V16 i nie znika
po usunięciu komentarza ani odświeżeniu strony. Status zawiera `muteRemainingSeconds`,
`serverTime` i `mutedUntil` jako jednoznaczne daty UTC. Odliczanie korzysta z
monotonicznego `performance.now()`, niezależnego od przestawienia zegara urządzenia,
i jest synchronizowane z serwerem po powrocie do karty oraz co 12 sekund.
Serwer egzekwuje blokady według czasu bazy danych. Blokada autora w bazie serializuje równoczesne
wysłania w różnych dyskusjach; wyciszenie i zapis komentarza są częścią jednej transakcji.
Limit dotyczy wyłącznie dyskusji. Przyszły prywatny czat może używać wspólnego filtra
tekstu, ale nie powinien wywoływać `DiscussionCooldownService`.
Nowe i edytowane reguły muszą mieć minimum 3 znaki, nie licząc spacji, w obu trybach.
Wspólny `TextModerationService.mask(text)` lub `masker()` (dla wielu wiadomości naraz)
można wywołać w kolejnych modułach przed zwróceniem treści klientowi. Czat nie jest częścią tej zmiany.
