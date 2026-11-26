# Licytacje przedmiotów

Pod `/auctions` znajduje się lista licytacji przedmiotów z katalogu. Sprzedawca wybiera w formularzu `/new`, czy wystawia przedmiot w zwykłej sprzedaży, czy na licytacji. Strona `/auctions/{id}` pokazuje stan aukcji, historię ofert i realizację wygranej. Pod `/auctions/edit/{id}` można zmienić dane aukcji, dopóki nikt nie złożył oferty. Widoki „Moje wystawione” i „Moje licytowane” są dostępne z panelu użytkownika. Sprzedaż samego złota obsługują osobne ogłoszenia złota, nie licytacje.

## Baza danych

Licytacje dodała migracja V29. Nie zmienia ona znaczenia cen zapisanych wcześniej. Aukcja jest ogłoszeniem z `sale_mode=AUCTION` (zwykła sprzedaż ma `ITEM`), więc dzieli identyfikator z ogłoszeniem i korzysta z tych samych dyskusji, obserwowania i rozmów. Pole `listings.price` jest dla aukcji tylko technicznym wypełnieniem i API go nie zwraca. Kwoty licytacji pochodzą wyłącznie z tabel `auctions` i `auction_bids`.

## Reguły

- Aukcja ma jedną walutę: złotówki liczone w groszach (od 1 do 100 000 000) albo złoto w pojedynczych jednostkach (od 1 do 9 000 000 000 000 000). Kwoty w złocie wpisuje się jak w ogłoszeniach złota, np. `500m` albo `2g`.
- Aukcja trwa od godziny do 7 dni, domyślnie 24 godziny. Terminy zapisywane są jako `TIMESTAMPTZ` (w Javie `Instant`). API podaje je w UTC, a interfejs pokazuje w czasie lokalnym.
- Pierwsza oferta nie może być niższa od ceny wywoławczej. Każda następna musi podbić cenę o `max(1 zł lub 1m, ceil(1% bieżącej kwoty))`. Minimalną kwotę wylicza serwer.
- Oferta złożona w ostatnich 2 minutach przesuwa koniec aukcji na 2 minuty od jej przyjęcia. Takie przedłużenia mogą się powtarzać.
- Licytować może konto ze zweryfikowanym e-mailem, założone co najmniej 24 godziny wcześniej. Kolumna `users.created_at` przechowuje czas lokalny backendu, dlatego wiek konta liczony jest w strefie JVM.
- Właściciel aukcji, także administrator, nie licytuje własnego przedmiotu. Prowadzący nie może przebić własnej oferty. Nie ma ofert automatycznych ani przeliczania walut.
- Po pierwszej ofercie aukcji nie da się edytować, a sprzedawca nie może jej anulować. Zwykłe endpointy edycji i usuwania ogłoszeń tych zasad nie omijają.

Domyślnie konto może złożyć 30 prób na minutę, z odstępem co najmniej 2 sekund między próbami w jednej aukcji. Wartości ustawia się przez `app.auctions.attempts-per-minute` i `app.auctions.attempt-spacing-seconds`, a interfejs pobiera je z `/api/auctions/config`. Do limitu liczą się także próby odrzucone przez serwis ofert. Nie liczą się natomiast żądania z błędnym JSON-em ani te, które odpadły wcześniej na walidacji lub uwierzytelnianiu. Po przekroczeniu limitu serwer odpowiada kodem 429 z nagłówkiem `Retry-After`.

## Spójność i prywatność

Każda zmiana blokuje najpierw wiersz ogłoszenia, potem wiersz aukcji. Przy przyjmowaniu oferty czas odczytywany jest przez `clock_timestamp()` dopiero po uzyskaniu obu blokad. Oferta jest ważna tylko wtedy, gdy `acceptedAt < endsAt`, nawet jeśli czekała w kolejce na blokadę. Historia ofert, unieważnień i audytu da się tylko dopisywać, bo triggery odrzucają na tych tabelach UPDATE i DELETE. Klucz obcy pilnuje, żeby waluta oferty zgadzała się z walutą aukcji.

Każda oferta niesie klucz UUID, unikalny w obrębie aukcji i konta. Ponowne wysłanie z tym samym kluczem zwraca już zapisaną ofertę, a próba użycia go z inną kwotą lub walutą kończy się odrzuceniem. Po niejednoznacznym błędzie sieci klient wysyła ofertę ponownie z tym samym kluczem. Zmiana prowadzącego, nowy termin i powiadomienie dla przebitego licytującego zapisują się w jednej transakcji. Przez WebSocket po commicie wychodzi tylko ID aukcji i numer wersji. Jeśli dostarczenie się nie uda, oferta i tak zostaje.

Zalogowany użytkownik może subskrybować wyłącznie konkretny temat `/topic/auctions/{id}` na istniejącym połączeniu STOMP. Źródłem stanu pozostaje REST: po sygnale zmiany albo po ponownym połączeniu strona pobiera szczegóły, a sygnały z 500 ms łączy w jedno odświeżenie. Gdy WebSocket nie działa albo strona czeka na wynik po upływie terminu, widoczna strona odpytuje serwer co 10 sekund. Lista aukcji odświeża się co 30 sekund, listy przedmiotów i złota co 60 sekund. Szybki powrót do karty przeglądarki nie wywołuje dodatkowego zapytania. Aukcje nie trafiają do cache publicznych list ogłoszeń.

Kwoty i dane kontaktowe widzą tylko zalogowani. W historii ofert licytujący występują pod stałymi w obrębie aukcji aliasami, a pełne identyfikatory widzi moderacja. Zwycięzcę poznają strony realizacji i administrator.

Co 5 sekund harmonogram zamyka do 100 aukcji, którym minął termin, z użyciem `FOR UPDATE ... SKIP LOCKED`. Dzięki temu może działać jednocześnie na kilku instancjach i nie zamknie aukcji dwa razy. Wygranie aukcji nie jest jeszcze sprzedażą i nie podnosi potwierdzonej reputacji sprzedawcy.

## Realizacja i moderacja

Sprzedawca i zwycięzca zaczynają nową rozmowę albo kontynuują wcześniejszą rozmowę o tym ogłoszeniu. Inne konta nie mogą rozpocząć realizacji przez zwykłe API rozmów. Do rozmowy można zaprosić pośrednika, a jego potwierdzenie wymiany musi dotyczyć zwycięzcy. Deklaracja sprzedawcy, że wymiana się odbyła, jest zapisywana osobno od potwierdzenia administratora. Jeśli przez 48 godzin nikt nie nawiąże kontaktu, strona aukcji wyświetla przypomnienie.

Realizacja przechodzi przez stany `PENDING_CONTACT`, `IN_PROGRESS`, `DECLARED_COMPLETED`, `ADMIN_CONFIRMED`, `DISPUTED` i `CANCELLED`. Każda ze stron może zgłosić spór. Administrator rozpatruje go, anulując albo wznawiając realizację, i podaje uzasadnienie. System nie nakłada kar automatycznie i nie przekazuje wygranej kolejnej osobie.

Zakładka „Moderacja licytacji” w panelu administracyjnym zbiera zgłoszenia, ograniczenia i wpisy audytu. Ze szczegółów aukcji administrator może unieważnić ofertę albo anulować trwającą aukcję. Unieważnienie oferty po zakończeniu aukcji kieruje realizację do sporu, ale zwycięzca pozostaje ten sam. Ograniczenie udziału w licytacjach wymaga podania powodu, daty końcowej i sposobu odwołania; administrator może je cofnąć.

Serwis nie pobiera opłat, nie przechowuje środków i nie sprawdza, czy licytujący je ma. Decyzje o publicznym uruchomieniu, przechowywaniu danych, odwołaniach i zgodności z regulaminem gry należą do właściciela projektu.

## API

Publiczne odczyty: `GET /api/auctions`, `/{id}`, `/{id}/bids` i `/config`. Logowania wymaga `GET /api/auctions/mine` oraz wszystkie zapisy: `POST /api/auctions`, `PUT /{id}`, `POST /{id}/bids`, `/{id}/cancel`, `/{id}/reports`, `/{id}/fulfillment/start` i `/{id}/fulfillment/actions`. Moderacja korzysta z `/api/admin/auctions/...` i wymaga roli administratora.

## Testy

`AuctionIntegrationIT` uruchamia się w ramach `.\mvnw.cmd verify` na jednorazowym PostgreSQL z Testcontainers. Nie potrzebuje zmiennych środowiskowych ani bazy aplikacji. Sprawdza współbieżne oferty, blokady i termin, idempotencję, limity prób, niemodyfikowalność historii, wycofanie całej transakcji, ukrywanie danych, zamykanie aukcji, potwierdzenie reputacji i uprawnienia subskrypcji STOMP.

Zegar dyskusji testuje `discussion-posting-clock.spec.ts` w testach frontendu (`npm test -- --watch=false` w katalogu `frontend`). Przebieg licytacji w przeglądarce, z ofertą widoczną u sprzedawcy na żywo, sprawdza test e2e `frontend/e2e/auction.e2e.ts`.
