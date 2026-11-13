# Licytacje przedmiotów (PR 3)

`/auctions` zawiera licytacje przedmiotów z katalogu. W formularzu `/new`
sprzedawca wybiera zwykłą sprzedaż albo licytację. `/auctions/{id}` pokazuje
stan, historię i realizację, a `/auctions/edit/{id}` pozwala zmienić dane przed
pierwszą ofertą. Widoki „Moje wystawione” i „Moje licytowane” są dostępne
z panelu użytkownika. Złoto jako sprzedawane dobro nadal korzysta z PR 2.

## Uruchomienie

Domyślnie nowe aukcje i składanie ofert są wyłączone na backendzie:

```dotenv
ITEM_AUCTIONS_ENABLED=false
PLN_AUCTIONS_ENABLED=false
```

Po osobnej decyzji o uruchomieniu ustaw `ITEM_AUCTIONS_ENABLED=true`,
a dla PLN także `PLN_AUCTIONS_ENABLED=true`, i uruchom ponownie backend.
Docker Compose przekazuje obie zmienne. Flagi są niezależne od
`GOLD_LISTINGS_ENABLED`. Odczyt, zamykanie, moderacja i realizacja istniejących
aukcji działają również po wyłączeniu nowych ofert.

Flyway V29 rozszerza bazę po V28. Migracje nie reinterpretują dotychczasowych
cen. `sale_mode=AUCTION` odróżnia licytacje od zwykłych ogłoszeń `ITEM`.
Wspólny identyfikator pozwala zachować dyskusje, obserwowanie i rozmowy.
Techniczne pole `listings.price` nie jest ceną aukcji i nie jest udostępniane
dla licytacji. Kwoty licytacji pochodzą wyłącznie z `auctions` i `auction_bids`.

## Reguły

- Jedna waluta: PLN w groszach (1–100 000 000) albo złoto w pojedynczych
  jednostkach (1–9 000 000 000 000 000). Formularz złota przyjmuje `m` z PR 2.
- Czas od godziny do 7 dni, domyślnie 24 godziny. Nowe daty są `TIMESTAMPTZ`
  i `Instant`, API używa UTC, interfejs wyświetla czas lokalny.
- Pierwsza oferta co najmniej równa cenie wywoławczej. Następne podbicie:
  `max(1 PLN lub 1m, ceil(1% bieżącej kwoty))`. Serwer podaje minimum.
- Oferta w ostatnich 2 minutach przedłuża termin do 2 minut od jej przyjęcia.
  Przedłużenia mogą się powtarzać.
- Licytujący musi mieć zweryfikowany e-mail i konto starsze niż 24 godziny.
  Stare `users.created_at` pozostaje lokalnym czasem obecnego backendu;
  sprawdzenie wieku stosuje strefę JVM zgodną z istniejącym zapisem kont.
- Właściciel, także administrator, nie licytuje własnego przedmiotu. Lider
  nie przebija własnej oferty. Nie ma automatycznych ofert ani wymiany walut.
- Po pierwszej ofercie nie można edytować aukcji; sprzedawca nie może jej
  anulować. Zwykłe endpointy edycji/usuwania ogłoszeń nie obchodzą tych zasad.

Domyślne limity to 30 prób/minutę/konto oraz 2 sekundy między próbami w jednej
aukcji. `app.auctions.attempts-per-minute` i
`app.auctions.attempt-spacing-seconds` pozwalają je skonfigurować; UI pobiera
wartości z `/api/auctions/config`. Limity obejmują także odrzucone próby
przekazane do serwisu ofert. Błędny JSON i żądania odrzucone przed serwisem
przez walidację/auth nie są próbami w tym liczniku. 429 zawiera `Retry-After`.

## Spójność i prywatność

Każda mutacja blokuje najpierw ogłoszenie, potem aukcję. Przyjmowanie ofert
czyta `clock_timestamp()` po uzyskaniu obu blokad. Oferta jest ważna wyłącznie
gdy `acceptedAt < endsAt`, także jeśli czekała w kolejce. Historia jest
dopisywalna; triggery odrzucają UPDATE/DELETE ofert, unieważnień i audytu.
Waluta oferty jest zabezpieczona kluczem obcym do waluty aukcji.

Klucz UUID ponowienia jest unikalny w obrębie aukcji i konta. Ponowienie
zwraca ten sam zapis; inna kwota lub waluta z tym kluczem jest odrzucana.
Klient zachowuje klucz po niejednoznacznym błędzie sieci. Zmiana lidera,
terminu i trwałe powiadomienie są jedną transakcją. WebSocket publikuje
wyłącznie ID i wersję po commit; błąd dostarczenia nie cofa oferty.

Zalogowani mogą subskrybować tylko dokładny `/topic/auctions/{id}` na
istniejącym połączeniu STOMP. REST pozostaje źródłem stanu. Szczegóły
odświeżają się po reconnect i sygnale zmiany (zdarzenia są łączone w oknach
500 ms). Polling co 10 sekund jest używany na widocznej stronie przy braku
WebSocket lub oczekiwaniu na wynik po upływie terminu. Lista aukcji jest
odświeżana co 30 sekund, a listy przedmiotów i złota co 60 sekund; szybki
powrót do karty nie wyzwala dodatkowego odpytywania. Aukcje nie
korzystają z cache publicznych list PR 2; ceny nie trafiają do tego cache.
Kwoty i kontakt wymagają logowania. Historia używa stabilnych aliasów
wewnątrz jednej aukcji; pełne ID uczestników widzi moderacja. Zwycięzca jest
ujawniany stronom realizacji i administratorowi.

Scheduler co 5 sekund zamyka do 100 należnych aukcji, korzystając z
`FOR UPDATE ... SKIP LOCKED`. Działa idempotentnie na wielu instancjach.
Wygrana nie oznacza sprzedaży i nie zwiększa potwierdzonej reputacji.

## Realizacja i moderacja

Sprzedawca i zwycięzca rozpoczynają istniejącą rozmowę albo wykorzystują
wcześniejszą rozmowę tej pary o tym ogłoszeniu. Inne konta nie mogą rozpocząć
realizacji przez zwykłe API rozmów. W rozmowie można zaprosić pośrednika;
potwierdzenie z PR 1 musi dotyczyć zwycięzcy. Deklaracja sprzedawcy pozostaje
oddzielona od jawnego potwierdzenia administratora. Po 48 godzinach bez
rozpoczęcia kontaktu widok pokazuje przypomnienie.

Stan realizacji: `PENDING_CONTACT`, `IN_PROGRESS`, `DECLARED_COMPLETED`,
`ADMIN_CONFIRMED`, `DISPUTED`, `CANCELLED`. Strony mogą zgłosić spór;
administrator rozpatruje anulowanie/wznowienie z uzasadnieniem. Nie ma
automatycznej kary ani przekazywania wygranej kolejnej osobie.

W panelu administracyjnym „Moderacja licytacji” pokazuje zgłoszenia,
ograniczenia i audyt. Szczegóły aukcji pozwalają unieważnić ofertę lub
anulować aktywną aukcję. Unieważnienie oferty po zakończeniu kieruje
realizację do sporu, zachowując ustalonego zwycięzcę. Ograniczenie udziału
wymaga powodu, daty końcowej i sposobu odwołania; administrator może je cofnąć.

Serwis nie pobiera opłat, nie przechowuje środków ani nie weryfikuje ich
posiadania. Publiczne uruchomienie, zasady retencji, odwołań i zgodność
z regulaminami pozostają osobną decyzją właściciela projektu.

## API i weryfikacja

Publiczne odczyty: `GET /api/auctions`, `/{id}`, `/{id}/bids`, `/config`.
`GET /api/auctions/mine` oraz zapisy wymagają logowania:
`POST /api/auctions`, `PUT /{id}`, `POST /{id}/bids`, `/{id}/cancel`,
`/{id}/reports`, `/{id}/fulfillment/start`, `/{id}/fulfillment/actions`.
Moderacja korzysta z `/api/admin/auctions/...` i wymaga roli administratora.

```powershell
powershell -File scripts/test-auctions.ps1
npm.cmd --prefix frontend test -- --watch=false
npm.cmd --prefix frontend run build
node scripts/test-discussion-posting-clock.mjs
```

Pierwszy skrypt kompiluje i wykonuje testy jednostkowe w Java 21, następnie
uruchamia testy integracyjne na jednorazowym PostgreSQL 17 w osobnej sieci
Docker. Nie odczytuje `.env`, nie używa bazy aplikacji, usuwa swoje kontenery
i sieć. Bez `AUCTION_TEST_DATABASE_URL` Maven pomija klasę integracyjną.
Testy obejmują współbieżność, blokady i termin, idempotencję, limity,
niemodyfikowalność historii, atomowy rollback, ukrywanie danych, finalizację,
potwierdzenie reputacji oraz zamknięte uprawnienia STOMP.
