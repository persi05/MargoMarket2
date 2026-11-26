# Aktualizacja katalogu przedmiotów

Katalog aktualizuje administrator w panelu: **Katalog przedmiotów → Aktualizuj katalog**. Backend pobiera dane z MargoWorld w tle i na bieżąco pokazuje status oraz liczbę nowych i zaktualizowanych przedmiotów. Aktualizacja nie ma harmonogramu, uruchamia się tylko ręcznie. Nie wymaga restartu ani przebudowy aplikacji, a kolejne wyszukiwania od razu korzystają z nowych danych.

Dane są najpierw pobierane i walidowane, a dopiero potem zapisywane. Jeśli źródło zwróci błąd albo import się nie powiedzie, dotychczasowy katalog zostaje bez zmian. Przedmioty są dopasowywane po identyfikatorze zewnętrznym, więc zachowują lokalne ID i powiązania z ogłoszeniami. Przedmiot, którego zabraknie w źródle, nie znika z bazy.

Baza nie dopuszcza dwóch aktualizacji naraz. Import, który przerwał się w trakcie, można ponowić, gdy przez 5 minut nie było w nim postępu.

## Katalog startowy

Plik `src/main/resources/data/margoworld-market-items.json` wczytuje się jednorazowo, przy starcie na pustej bazie. Późniejsze restarty nie nadpisują katalogu zapisanego w bazie. Na serwerze PostgreSQL musi więc trzymać dane trwale; lokalny Docker Compose używa do tego wolumenu.
