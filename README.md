# MargoMarket

## Oferty złota Margonem

Zakładka `/gold` korzysta ze wspólnych ogłoszeń, rozmów, dyskusji,
obserwowanych i potwierdzeń wymian. Formularze: `/gold/new` oraz
`/gold/edit/{id}`. Oferta dotyczy całego pakietu; nie obsługuje częściowej sprzedaży.

Wpisywana ilość jest wyrażona w `m`, do sześciu miejsc po przecinku.
`1500m` jest prezentowane jako `1,5g`. Baza przechowuje całkowite jednostki
złota (`BIGINT`, maksymalnie 9 000 000 000 000 000), a nową cenę PLN w groszach
(1–100 000 000). Jednostki dotychczasowych cen przedmiotów nie zmieniają się.

Cenę pakietu wylicza backend z ilości i kursu. Formularz przyjmuje
`1 PLN = … m`, a na Syberii, Pandorze i Unii domyślnie `1g = … PLN`.
Sposób podania kursu można zmienić ręcznie. Przykłady: 260m przy 13m/PLN
kosztuje 20 PLN; 1,5g przy 23 PLN/g kosztuje 34,50 PLN.
Kwoty zaokrąglane są do grosza, z połową grosza w górę. Kurs jest zapisywany
jako para całkowitych wartości złota i groszy, bez typów zmiennoprzecinkowych.
Żądanie zawiera `rateMode` (`M_PER_PLN` lub `PLN_PER_G`), `rateGoldUnits`
i `ratePriceMinor`. Dla pierwszego trybu cena kursu musi wynosić 100 groszy,
a dla drugiego ilość kursu musi wynosić 1 000 000 000 jednostek złota.
`priceMinor` w odpowiedzi jest ceną całego pakietu wyliczoną przez serwer.
V28 dodaje kursy po V27; starym ofertom zachowuje dokładną cenę pakietu,
bez wymyślania historycznego kursu. Przy ich edycji trzeba wybrać nowy kurs.

Wystawianie nowych ofert złota jest domyślnie wyłączone. Dopiero po osobnej
decyzji o uruchomieniu należy ustawić `GOLD_LISTINGS_ENABLED=true` i ponownie
uruchomić backend. Flaga blokuje tworzenie również przez API. Edycja i zakończenie
już istniejących ofert pozostają możliwe. Serwis nie obsługuje płatności.

API złota: `GET/POST /api/listings/gold`, `PUT /api/listings/{id}/gold`,
`POST /api/listings/{id}/withdraw`. Lista obsługuje świat, opis, zakres ilości
i ceny oraz sortowanie `newest`, `priceAsc`, `priceDesc`, `amountAsc`, `amountDesc`.

Cache Angulara przechowuje wyłącznie publiczne strony wyników w pamięci,
przez 30 sekund, maksymalnie 20 wpisów. Rodzaj oferty, filtry i strona mają
oddzielne klucze. Ceny i kontakt są uzupełniane osobnym uwierzytelnionym żądaniem
`GET /api/listings/private-details?ids=...` i nie są zapisywane w cache.
Mutacje unieważniają cache, a powrót do widocznej karty odświeża dane.

Test pełnego łańcucha migracji i ograniczeń złota w jednorazowej bazie:

```powershell
powershell -File scripts/test-gold-migration.ps1
```

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
