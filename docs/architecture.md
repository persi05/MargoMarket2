# Architektura: modularny monolit

Backend to jedna aplikacja Spring Boot podzielona na moduły według obszarów
biznesowych. Granice modułów sprawdza Spring Modulith (`spring-modulith` 1.2).
Każdy pakiet bezpośrednio pod `com.margomarket` jest modułem. Moduł trzyma
u siebie encje, repozytoria, serwisy, DTO i kontrolery swojego obszaru.

Moduł ma dwie części:

- **pakiet główny** (np. `com.margomarket.user`) to jego publiczne API, czyli
  to, czego mogą używać inne moduły;
- **podpakiety** (zwykle `internal`, w `listing` także `gold`, `comment`,
  `auction`) to jego wnętrze: repozytoria, kontrolery, mappery, encje
  i serwisy, z których korzysta tylko ten moduł.

```
user/
  UserDirectory.java      publiczne API: wyszukiwanie użytkowników dla innych modułów
  UserService.java        publiczne API
  User.java               encja publiczna, bo inne moduły mają do niej relacje JPA
  internal/
    UserRepository.java   tylko dla modułu user
    RoleRepository.java, Role.java, ...
```

## Moduły

| Moduł | Pakiet | Zawartość | Może zależeć od |
|---|---|---|---|
| Wspólne | `shared` | `ApiError`, `PageResponse`, wyjątki ogólne, `GlobalExceptionHandler`, OpenAPI | — |
| Moderacja treści | `textmoderation` | zablokowane słowa | shared |
| Użytkownicy | `user` | konta, rejestracja, nazwy publiczne | shared, textmoderation |
| Moderacja użytkowników | `moderation` | blokady konta i ograniczenia działań | shared, user |
| Limity zapytań | `ratelimit` | limity zapytań i logowania | shared, user |
| Bezpieczeństwo | `security` | JWT, logowanie, `SecurityConfig` | shared, user, moderation, ratelimit |
| Katalog | `catalog` | przedmioty, światy, waluty, słowniki, odświeżanie katalogu | shared |
| Ceny | `pricing` | kursy światów, historia i podpowiedzi cen | shared, catalog |
| Powiadomienia | `notification` | powiadomienia użytkowników | shared, user |
| Ogłoszenia | `listing` | ogłoszenia; podpakiety `gold`, `comment`, `auction` | shared, user, catalog, moderation, textmoderation, notification |
| Rozmowy | `conversation` | rozmowy, pośrednicy, potwierdzenia wymian, realizacja aukcji, WebSocket | shared, user, security, moderation, textmoderation, listing, `listing::auction` |
| Opinie | `review` | opinie o sprzedających | shared, user, moderation, textmoderation, notification, conversation |
| Profile | `profile` | publiczne profile | shared, user, listing |
| Administracja | `admin` | panel administratora | shared, user, listing |
| Monitoring | `monitoring` | health check i metryki Actuatora | — (czyta bazę bezpośrednio) |

Dozwolone zależności są zapisane w `package-info.java` każdego modułu
(`@ApplicationModule(allowedDependencies = …)`).

## Publiczne API modułów

| Moduł | Publiczne API |
|---|---|
| `user` | `UserDirectory` (referencja, zweryfikowane konto, nazwy publiczne), `UserService`, `User`, `PublicNames`, `UserResponse`, żądania dotyczące konta |
| `catalog` | `Catalog` (przedmiot, świat, waluta, lista światów), encje `Item`, `ItemType`, `Rarity`, `Server`, `Currency`, a także `WorldType`, `ItemRules`, `LookupResponse` |
| `listing` | `ListingService`, `ListingQueries` (wyszukiwanie dla admina, ogłoszenia na profilu, blokada wiersza), `UserListingStats`, `Listing`, `ListingStatus`, `ListingSoldEvent`, DTO ogłoszeń |
| `listing::auction` | `AuctionSettlement` (blokada aukcji, wpis w historii, powiadomienie stron na żywo), `AuctionListingPolicy` |
| `moderation` | `UserModerationService`, `ModerationScope`, `UserRestrictedException`, `UserModerationChanged` |
| `conversation` | `ExchangeConfirmed` |
| `notification` | `NotificationService` |
| `ratelimit` | `RateLimiter`, `RateLimitFilter`, `LoginAttemptGuard` |
| `security` | `JwtService` |
| `textmoderation` | `TextModerationService` |
| `pricing`, `profile`, `review`, `admin`, `monitoring` | brak, inne moduły z nich nie korzystają |

`listing::auction` to nazwany interfejs: z pakietu `listing.auction` inne
moduły widzą tylko klasy oznaczone `@NamedInterface("auction")`. Rozmowy
rozliczają przez nie zakończone aukcje.

Encje `User`, `Listing`, `ListingStatus` i encje katalogu są publiczne tylko
dlatego, że inne moduły mają do nich relacje JPA. Gdy te relacje zastąpią same
identyfikatory, encje też trafią do `internal`.

## Jak moduły się komunikują

- **Wywołanie publicznego API.** Przykład: `listing` sprawdza ograniczenia
  przez `UserModerationService`, a przedmioty i światy pobiera przez `Catalog`.
  Żaden moduł nie używa cudzych repozytoriów ani mapperów.
- **Zdarzenia z outboxem.** Używamy ich tam, gdzie bezpośrednie wywołanie
  tworzyłoby cykl albo odwracało kierunek zależności. Także tam, gdzie skutek
  uboczny (powiadomienie, komunikat na żywo) nie powinien cofać głównej
  operacji:
  - `moderation.UserModerationChanged`: `conversation` odświeża otwarte strony
    użytkownika i zamyka sesje WebSocket zablokowanego konta;
  - `conversation.ExchangeConfirmed`: `review` zaprasza kupującego do opinii;
  - `listing.ListingSoldEvent`: powiadomienia o sprzedaży dla sprzedawcy
    i obserwujących.

  Słuchacze mają `@ApplicationModuleListener`. Zdarzenie zapisuje się w tabeli
  `event_publication` (migracja V36) w tej samej transakcji, w której zostało
  opublikowane, osobno dla każdego słuchacza. Po commicie słuchacz działa
  asynchronicznie we własnej transakcji. Po udanej obsłudze wpis dostaje
  `completion_date`. Wycofana transakcja nie publikuje niczego.

  Gdy słuchacz rzuci wyjątek, główna operacja zostaje zapisana, a zdarzenie
  czeka w tabeli. `EventPublicationMaintenance` co 5 minut ponawia zdarzenia
  nieobsłużone od ponad 5 minut. Po restarcie aplikacji ponawiane są wszystkie
  niedokończone. Obsłużone wpisy są usuwane po 7 dniach. Przez ponowienia
  słuchacz może dostać to samo zdarzenie więcej niż raz. Dlatego powiadomienia
  (zaproszenie do opinii, sprzedaż) mają klucz zdarzenia (`event_key`)
  i drugi raz się nie zapisują.
- **Obsługa błędów.** Każdy moduł tłumaczy własne wyjątki we własnym
  `@RestControllerAdvice` z `@Order(GlobalExceptionHandler.MODULE_HANDLERS)`.
  `GlobalExceptionHandler` obsługuje tylko błędy wspólne. Format odpowiedzi
  (`ACCOUNT_BLOCKED`, `ACTION_RESTRICTED`, `RATE_LIMITED`, …) się nie zmienił.

Baza danych pozostaje wspólna (jeden schemat Flyway). Moduły korzystają też
z zapytań SQL na tabelach innych obszarów (np. statystyki, monitoring).
Granice pilnują zależności w kodzie, a nie dostępu do tabel.

## Kontrola granic

`ModuleStructureTest` (zwykły test jednostkowy, uruchamiany też w CI):

- `ApplicationModules.verify()` przerywa build przy cyklu między modułami,
  przy użyciu wewnętrznego typu innego modułu (czegokolwiek z jego
  podpakietów) albo przy zależności spoza `allowedDependencies`.
- Repozytoria, kontrolery i klasy `@RestControllerAdvice` nie mogą leżeć
  w pakiecie głównym modułu, czyli w jego publicznym API.
- Publiczne mogą być tylko encje z listy `SHARED_ENTITIES`, czyli te, do
  których inne moduły mają relacje JPA.

`EventPublicationIT` sprawdza outbox: zapis zdarzenia razem z transakcją,
brak publikacji po wycofaniu i ponowienie po błędzie słuchacza.
- `Documenter` zapisuje diagramy PlantUML i opisy modułów do
  `target/spring-modulith-docs` (`components.puml`, `module-*.puml`,
  `all-docs.adoc`).

Mapa modułów jest też dostępna w działającej aplikacji pod
`/actuator/modulith` (port 8081).

## Dodawanie kodu

1. Nowa klasa trafia do modułu swojego obszaru, domyślnie do jego pakietu
   `internal`. Do pakietu głównego trafia tylko to, czego potrzebują inne
   moduły.
2. Potrzebujesz czegoś z innego modułu? Użyj jego publicznego API. Jeśli
   czegoś brakuje, dodaj metodę do publicznej klasy tamtego modułu (np.
   `Catalog`, `UserDirectory`, `ListingQueries`), zamiast udostępniać
   repozytorium. Jeśli tej zależności nie ma w `allowedDependencies`, dopisz
   ją świadomie albo opublikuj zdarzenie.
3. Gdy moduł A musiałby wołać B, a B już zależy od A, opublikuj zdarzenie w A
   i obsłuż je w B metodą z `@ApplicationModuleListener`. Słuchacz musi
   znieść ponowne dostarczenie tego samego zdarzenia.

## Monitoring

Actuator działa na osobnym porcie **8081** (`MANAGEMENT_PORT`). Docker Compose
wystawia na zewnątrz tylko 8080, więc `/actuator/**` jest dostępne wyłącznie
z wnętrza kontenera albo z tego samego serwera, np. dla Prometheusa w tej
samej sieci Dockera. Na porcie 8080 `/actuator/**` jest odrzucane.
Na porcie 8081 nie trzeba tokenu.

| Endpoint (port 8081) | Zawartość |
|---|---|
| `/actuator/health` | stan aplikacji ze składnikami (`db`, `auctionClosing`, …) |
| `/actuator/health/liveness`, `/readiness` | sondy dla Dockera i load balancera; readiness obejmuje bazę |
| `/actuator/metrics`, `/actuator/prometheus` | metryki JVM, HTTP, puli połączeń i biznesowe |
| `/actuator/modulith`, `/actuator/info` | mapa modułów, informacje o aplikacji |

Docker Compose wystawia port 8081 tylko na `127.0.0.1`, więc na tym samym
komputerze wystarczy otworzyć `http://localhost:8081/actuator/health`
(albo `/actuator/prometheus`, `/actuator/modulith`). Z sieci port nie jest
dostępny. Na serwerze dostęp z własnego komputera daje tunel SSH:
`ssh -L 8081:localhost:8081 użytkownik@serwer`. Nie wystawiaj portu
bez `127.0.0.1:`, bo na 8081 nie ma logowania.

Metryki biznesowe (odświeżane co najwyżej co 30 s jednym zapytaniem):

- `margomarket_auctions_active`: trwające aukcje,
- `margomarket_auctions_overdue`: aktywne aukcje ponad 2 minuty po czasie zakończenia,
- `margomarket_listings_active{kind="item"|"gold"}`: aktywne ogłoszenia z ceną stałą,
- `margomarket_conversations_open`: otwarte rozmowy,
- `margomarket_reports_open{type="conversation"|"auction"}`: zgłoszenia czekające na moderatora,
- `margomarket_fulfillments_disputed`: sporne rozliczenia aukcji,
- `margomarket_events_incomplete`: zdarzenia między modułami nieobsłużone od
  ponad minuty (słuchacz rzucił wyjątek i zdarzenie czeka na ponowienie).

Składnik zdrowia `auctionClosing` jest `DOWN`, gdy aktywna aukcja jest
otwarta dłużej niż 2 minuty po czasie zakończenia. Oznacza to, że
harmonogram zamykania stanął. Nie wchodzi do grupy readiness, więc instancja
dalej obsługuje ruch, ale `/actuator/health` zwraca 503.
Kontrola zdrowia poczty jest wyłączona, bo awaria SMTP nie powinna oznaczać
całej usługi jako niedziałającej.

Docker Compose sprawdza kontener przez `localhost:8081/actuator/health/readiness`.
