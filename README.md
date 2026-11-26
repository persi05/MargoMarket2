# MargoMarket

MargoMarket to giełda przedmiotów dla graczy Margonem. Gracze wystawiają przedmioty z katalogu gry i złoto, sprzedają je za cenę stałą albo na licytacji, a szczegóły wymiany ustalają na czacie. Wymianę może potwierdzić administrator w roli pośrednika. Dopiero potwierdzona wymiana pozwala kupującemu wystawić sprzedawcy opinię.

Backend napisano w Spring Boot 3 (Java 21), frontend w Angularze, a dane trzyma PostgreSQL. Backend udostępnia REST API pod `/api` oraz kanał WebSocket (STOMP) do czatu i licytacji na żywo.

## Funkcje

- ogłoszenia przedmiotów i złota z wyszukiwarką, filtrami i obserwowaniem,
- licytacje w złocie albo w złotówkach, z historią ofert i aktualizacją na żywo,
- czat kupującego ze sprzedawcą, prośba o pośrednika i potwierdzenie wymiany,
- opinie o sprzedających i publiczne profile,
- historia cen i podpowiedzi ceny przy wystawianiu,
- panel administratora: zgłoszenia, wymiany, moderacja licytacji, blokady kont, aktualizacja katalogu przedmiotów.

## Struktura repozytorium

| Katalog | Zawartość |
|---|---|
| `src/main/java/com/margomarket` | backend podzielony na moduły (Spring Modulith), opis w [docs/architecture.md](docs/architecture.md) |
| `src/main/resources/db/migration` | migracje Flyway (V1–V37) |
| `src/test` | testy jednostkowe i integracyjne backendu |
| `frontend/src` | aplikacja Angular z testami Vitest |
| `frontend/e2e` | testy end-to-end w Playwright |
| `docs` | dokumentacja: architektura, licytacje, moderacja kont, katalog, specyfikacja OpenAPI |

## Wymagania

- Docker Desktop albo Docker Engine z Docker Compose,
- Node.js (CI używa wersji 24) z npm, do frontendu i testów e2e,
- Java 21, tylko gdy backend ma działać albo być testowany poza Dockerem.

## Konfiguracja

Skopiuj przykładową konfigurację i w razie potrzeby zmień wartości w `.env`:

```powershell
Copy-Item .env.example .env
```

Schemat bazy tworzą migracje z `src/main/resources/db/migration`. Flyway wykonuje je automatycznie przy starcie backendu.

## Uruchomienie

Backend i PostgreSQL startują w Dockerze:

```powershell
docker compose up --build
```

API odpowiada pod `http://localhost:8080/api`, a PostgreSQL nasłuchuje na `localhost:5432`. Frontend uruchamia się osobno:

```powershell
cd frontend
npm install
npm start
```

Aplikacja jest wtedy dostępna pod `http://localhost:4200`. Serwer deweloperski przekazuje zapytania `/api` do `http://localhost:8080`.

Plik `src/main/resources/data/margoworld-market-items.json` to katalog startowy. Wczytuje się raz, przy pierwszym uruchomieniu na pustej bazie. Później katalog aktualizuje administrator z poziomu panelu, co opisano w [docs/catalog.md](docs/catalog.md).

## Testy

### Backend

Testy jednostkowe (wymagają Javy 21):

```powershell
.\mvnw.cmd test
```

Pełna weryfikacja z testami integracyjnymi (Java 21 i uruchomiony Docker):

```powershell
.\mvnw.cmd verify
```

Maven Failsafe uruchamia w fazie `verify` klasy `*IT`. Każda z nich stawia własny PostgreSQL przez Testcontainers, wykonuje migracje Flyway i usuwa kontener po zakończeniu, więc dane aplikacji pozostają nietknięte. Wyniki trafiają do `target/failsafe-reports`. Ta grupa obejmuje m.in. testy migracji (`src/test/java/com/margomarket/migration`), licytacji (`AuctionIntegrationIT`) i granic modułów.

### Frontend

```powershell
cd frontend
npm test -- --watch=false
```

### End-to-end

Testy Playwright przechodzą przez całą aplikację w przeglądarce. Sprawdzają scenariusze, w których kilku użytkowników działa jednocześnie: sprzedaż przedmiotu, czat w czasie rzeczywistym, licytację z ofertą widoczną na żywo oraz wymianę z pośrednikiem zakończoną opinią. Korzystają z osobnego stosu w Dockerze (API na porcie 18080, baza w pamięci), dlatego nie ruszają danych deweloperskich.

```powershell
cd frontend
npx playwright install chromium   # tylko za pierwszym razem
npm run e2e:stack                 # buduje i uruchamia API oraz bazę testową
npm run e2e                       # sam uruchamia frontend na porcie 4300
npm run e2e:stack:down            # zatrzymuje stos
```

Konta testowe (sprzedawca, kupujący, administrator) zakłada `e2e/global-setup.ts`. Przed każdym przebiegiem usuwa też dane pozostawione przez poprzedni.

## CI

Workflow `.github/workflows/ci.yml` startuje przy każdym pushu i pull requeście, a z zakładki Actions można go też uruchomić ręcznie. Backend przechodzi `./mvnw verify` na Javie 21, razem z testami integracyjnymi na tymczasowych bazach PostgreSQL. Frontend sprawdzany jest na Node.js 24: `npm ci`, testy Vitest i build Angulara. Raporty testów i pliki wynikowe zostają jako artefakty workflowu. CI niczego nie wdraża na serwer.

## Dokumentacja

- [docs/architecture.md](docs/architecture.md): moduły backendu, komunikacja między nimi, monitoring,
- [docs/item-auctions.md](docs/item-auctions.md): reguły licytacji, spójność danych, realizacja wygranej,
- [docs/user-moderation.md](docs/user-moderation.md): blokady i ograniczenia kont,
- [docs/catalog.md](docs/catalog.md): aktualizacja katalogu przedmiotów,
- [docs/openapi.yaml](docs/openapi.yaml): specyfikacja REST API, generowana przez test `OpenApiSpecIT`.

## Prawa do treści gry

MargoMarket jest niezależnym projektem i nie jest powiązany z Garmory Sp. z o.o. Nazwy i grafiki przedmiotów oraz inne elementy gry Margonem należą do Garmory Sp. z o.o. Aplikacja korzysta z nich wyłącznie w celach informacyjnych, w zakresie potrzebnym do opisania wystawianych przedmiotów. Grafiki nie są kopiowane do repozytorium, ładują się bezpośrednio z serwerów Garmory.
