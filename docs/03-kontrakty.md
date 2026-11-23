# Kontrakty i błędy API

Ten katalog obejmuje tworzenie i anulowanie zwrotu oraz odczyt szczegółów,
listy i pełnej historii pod /api/v1/returns oraz symulacje wysyłki, inspekcji, refundacji i zamknięcia pod
/internal/sim/returns/{numer}/. Obejmuje też administracyjne
`POST /admin/projections/rebuild` i błędy obsługiwane przez Spring MVC.
Błędy mają format RFC 9457 i typ treści `application/problem+json`.
API publikuje ten katalog jako HTML pod `GET /docs/errors` na tym samym serwerze.
Pole `type` zawiera ścieżkę do katalogu i fragment odpowiadający sekcji danego kodu,
np. `/docs/errors#invalid-request`. Pole `instance` wskazuje ścieżkę żądania.

## Invalid request

Kod: `INVALID_REQUEST`.

HTTP 400. Brak wymaganego nagłówka lub ciała, błędny JSON albo niepoprawne pola.
`errors` zawiera pole i komunikat. `$` oznacza całe ciało lub ogólny błąd żądania.
Dla listy errors wskazuje parametr, np. page, size lub status; błędny numer
zwrotu wskazuje businessNumber. Popraw dane przed ponowieniem.
Nieudana operacja tworzenia nie zajmuje klucza.

## Return not found

Kod: `RETURN_NOT_FOUND`.

HTTP 404. Nie znaleziono zwrotu o wskazanym numerze biznesowym.
Sprawdź numer i ponów po jego poprawieniu lub gdy zwrot będzie dostępny.

## Idempotency key reused

Kod: `IDEMPOTENCY_KEY_REUSED`.

HTTP 422. Ten sam klucz i endpoint były już użyte z innymi bajtami żądania.
Dla nowej operacji użyj nowego klucza; ponowienie starej operacji wymaga oryginalnej treści.
Zmiana odstępów lub kolejności pól JSON również zmienia treść porównywaną skrótem.

## Return window expired

Kod: `RETURN_WINDOW_EXPIRED`.

HTTP 422. Upłynął skonfigurowany termin zgłoszenia zwrotu. Ponowienie nie zmieni wyniku.

## Mixed currencies

Kod: `MIXED_CURRENCIES`.

HTTP 422. Waluta pozycji różni się od waluty zgłoszenia. Popraw dane przed ponowieniem.

## No items

Kod: `NO_ITEMS`.

HTTP 422. Zgłoszenie nie zawiera pozycji do zwrotu. Dodaj co najmniej jedną pozycję przed ponowieniem.

## Invalid quantity

Kod: `INVALID_QUANTITY`.

HTTP 422. Ilość zwracanej pozycji jest mniejsza niż jeden. Popraw ilość przed ponowieniem.

## Unknown reason

Kod: `UNKNOWN_REASON`.

HTTP 422, jeśli ten kod zostanie zgłoszony przez domenę. Oznacza nierozpoznany
powód zwrotu. Użyj obsługiwanego powodu przed ponowieniem. Obecnie niepoprawny
powód w żądaniu tworzenia jest odrzucany jako `INVALID_REQUEST` (400).

## Unsupported media type

Kod: `UNSUPPORTED_MEDIA_TYPE`.

HTTP 415. Endpoint nie obsługuje typu wysłanego ciała. Ponów po zakodowaniu
ciała w obsługiwanym formacie i ustawieniu odpowiadającego mu `Content-Type`,
np. `application/json` dla operacji przyjmujących JSON.

## Not acceptable

Kod: `NOT_ACCEPTABLE`.

HTTP 406. Serwer nie może zwrócić reprezentacji dopuszczonej przez `Accept`.
Ponów z nagłówkiem dopuszczającym format odpowiedzi endpointu, np.
`Accept: application/json, application/problem+json` dla operacji zwracających JSON.

## Http request error

Kod: `HTTP_REQUEST_ERROR`.

Kod zapasowy dla odpowiedzi HTTP 4xx i 5xx obsługiwanych przez Spring MVC,
z wyłączeniem 400, 406, 415 i 500, które mają własne kody. Status zachowuje
znaczenie błędu źródłowego. Przykłady i warunki ponowienia

- HTTP 404 oznacza brak trasy lub zasobu. Sprawdź URL przed ponowieniem.
- HTTP 405 oznacza niedozwoloną metodę. Użyj metody wskazanej w `Allow`.
- HTTP 503 oznacza przejściową niedostępność. Poczekaj do czasu wskazanego w
  `Retry-After`, jeśli nagłówek jest obecny, i sprawdź, czy operację można bezpiecznie ponowić.

Dla innych statusów kieruj się statusem i nagłówkami odpowiedzi. Po 4xx popraw
przyczynę błędu, a po 5xx sprawdź wynik operacji przed ponowieniem zapisu.

## Internal error

Kod: `INTERNAL_ERROR`.

HTTP 500. Nieoczekiwany błąd serwera podczas obsługi żądania, także w trakcie
administracyjnej przebudowy projekcji.
Szczegóły techniczne pozostają w logach serwera i nie są zwracane klientowi.

- Odczyt można ponowić po ustąpieniu problemu.
- Tworzenie zwrotu można ponowić w okresie retencji, używając tego samego
  `Idempotency-Key` i dokładnie tych samych bajtów ciała.
- Przed ponowieniem anulowania lub symulacji sprawdź stan zwrotu i historię.
  Ponów polecenie po ustąpieniu problemu, jeśli wcześniejsze wywołanie nie
  odniosło skutku i aktualny stan nadal pozwala wykonać operację.
- `POST /admin/projections/rebuild` nie przyjmuje klucza idempotencji.
  Gdy problem ustąpi, operator może ponowić operację po sprawdzeniu, że poprzednie
  wywołanie się zakończyło. Każde wywołanie przebudowuje projekcję z magazynu zdarzeń.

## Cancellation not allowed

Kod: `CANCELLATION_NOT_ALLOWED`.

HTTP 409. Anulowanie jest możliwe tylko w REQUESTED, LABEL_ISSUED lub
PICKUP_SCHEDULED. Ponowne anulowanie również daje 409. Odczytaj aktualny stan.

## Concurrency conflict

Kod: `CONCURRENCY_CONFLICT`.

HTTP 409. Inne żądanie zmieniło zwrot od czasu jego odczytu. Odczytaj bieżący
stan i oceń, czy nadal można wykonać polecenie, zanim je ponowisz.

## Invalid state transition

Kod: `INVALID_STATE_TRANSITION`.

HTTP 409. Polecenie prowadzi do niedozwolonego przejścia stanu, np. próba
przyjęcia paczki przed rozpoczęciem transportu. Samo ponowienie
bez zmiany stanu nie rozwiąże problemu.

## Simulation command rejected

Kod: `SIMULATION_COMMAND_REJECTED`.

HTTP 422. Kwota lub waluta wykonanej refundacji nie zgadza się z decyzją albo
przyczyna zamknięcia nie odpowiada stanowi. Popraw polecenie przed ponowieniem.

## Supervisor approval required

Kod: `SUPERVISOR_APPROVAL_REQUIRED`.

HTTP 422. Ocena D dla pozycji powyżej skonfigurowanego progu wymaga `approvedBy`.
Uzupełnij zatwierdzenie przed ponowieniem.

## Incomplete inspection

Kod: `INCOMPLETE_INSPECTION`.

HTTP 422. Wyniki inspekcji nie obejmują prawidłowo wszystkich pozycji zwrotu
lub próbowano podjąć decyzję o refundacji przed ukończeniem inspekcji.
Popraw wyniki lub zakończ inspekcję przed ponowieniem odpowiedniej operacji.
