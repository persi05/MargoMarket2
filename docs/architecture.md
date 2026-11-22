# Architektura: modularny monolit

Backend to jedna aplikacja Spring Boot podzielona na moduły według obszarów
biznesowych. Granice modułów sprawdza Spring Modulith (`spring-modulith` 1.2).
Każdy pakiet bezpośrednio pod `com.margomarket` jest modułem. Moduł trzyma
u siebie encje, repozytoria, serwisy, DTO i kontrolery swojego obszaru.

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
(`@ApplicationModule(allowedDependencies = …)`). Podpakiety modułu są jego
wnętrzem. Jedyny podpakiet udostępniony innym modułom to `listing.auction`,
nazwany interfejs `listing::auction`. Rozmowy rozliczają przez niego
zakończone aukcje.

## Jak moduły się komunikują

- **Wywołanie publicznego serwisu.** Przykład: `listing` sprawdza ograniczenia
  przez `UserModerationService`.
- **Zdarzenia.** Używamy ich tam, gdzie bezpośrednie wywołanie tworzyłoby cykl
  albo odwracało kierunek zależności:
  - `moderation.UserModerationChanged`: po zmianie blokady `conversation`
    odświeża otwarte strony użytkownika i zamyka sesje WebSocket
    zablokowanego konta (po commicie).
  - `conversation.ExchangeConfirmed`: po potwierdzeniu wymiany `review` wysyła
    kupującemu zaproszenie do opinii. Słuchacz działa w tej samej transakcji.
  - `listing.ListingSoldEvent`: powiadomienie o sprzedaży.
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
  przy użyciu wewnętrznego typu innego modułu albo przy zależności spoza
  `allowedDependencies`.
- `Documenter` zapisuje diagramy PlantUML i opisy modułów do
  `target/spring-modulith-docs` (`components.puml`, `module-*.puml`,
  `all-docs.adoc`).

Mapa modułów jest też dostępna w działającej aplikacji pod
`/actuator/modulith` (port 8081).

## Dodawanie kodu

1. Nowa klasa trafia do modułu swojego obszaru, a nie do warstwy technicznej.
2. Potrzebujesz czegoś z innego modułu? Użyj jego publicznego serwisu z pakietu
   bazowego. Jeśli tej zależności nie ma w `allowedDependencies`, dopisz ją
   świadomie albo opublikuj zdarzenie.
3. Gdy moduł A musiałby wołać B, a B już zależy od A, opublikuj zdarzenie w A
   i obsłuż je w B.

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

Podgląd lokalnie: `docker exec margomarket-api wget -qO- localhost:8081/actuator/health`.

Metryki biznesowe (odświeżane co najwyżej co 30 s jednym zapytaniem):

- `margomarket_auctions_active`: trwające aukcje,
- `margomarket_auctions_overdue`: aktywne aukcje ponad 2 minuty po czasie zakończenia,
- `margomarket_listings_active{kind="item"|"gold"}`: aktywne ogłoszenia z ceną stałą,
- `margomarket_conversations_open`: otwarte rozmowy,
- `margomarket_reports_open{type="conversation"|"auction"}`: zgłoszenia czekające na moderatora,
- `margomarket_fulfillments_disputed`: sporne rozliczenia aukcji.

Składnik zdrowia `auctionClosing` jest `DOWN`, gdy aktywna aukcja jest
otwarta dłużej niż 2 minuty po czasie zakończenia. Oznacza to, że
harmonogram zamykania stanął. Nie wchodzi do grupy readiness, więc instancja
dalej obsługuje ruch, ale `/actuator/health` zwraca 503.
Kontrola zdrowia poczty jest wyłączona, bo awaria SMTP nie powinna oznaczać
całej usługi jako niedziałającej.

Docker Compose sprawdza kontener przez `localhost:8081/actuator/health/readiness`.
