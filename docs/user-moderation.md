# Moderacja kont użytkowników

Blokady i upomnienia nadaje administrator na publicznym profilu użytkownika, w sekcji **Moderacja konta**. Ograniczenia da się nakładać osobno na publikowanie i edycję przedmiotów, złota i licytacji, na składanie ofert, na wiadomości i na komentarze. Blokada całego konta działa natychmiast: unieważnia wcześniej wydane tokeny i zamyka otwarte połączenia WebSocket.

Okres blokady podaje się jako czas trwania, jako datę zakończenia albo jako bezterminowy. Ograniczenia czasowe wygasają same, bez restartu i bez zadania cyklicznego.

Każda decyzja, upomnienie i cofnięcie blokady trafia do historii, a użytkownik dostaje powiadomienie. Przy pełnej blokadzie widzi osobny ekran z powodem i terminem.

## API

- `/api/admin/users/{id}/moderation`: zarządzanie moderacją (rola administratora),
- `/api/auth/moderation`: status własnego konta.

Błąd `ACCOUNT_BLOCKED` oznacza blokadę całego konta, a `ACTION_RESTRICTED` ograniczenie pojedynczej czynności. Ograniczenia licytowania nadane przed wprowadzeniem tego mechanizmu przeniosła migracja V32.
