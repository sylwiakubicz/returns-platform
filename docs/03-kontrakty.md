# Kontrakty i błędy API

Ten katalog obejmuje tworzenie i anulowanie zwrotu oraz odczyt szczegółów,
listy i pełnej historii pod /api/v1/returns oraz symulacje wysyłki pod
/internal/sim/returns/{numer}/.
Błędy mają format RFC 9457 i typ treści `application/problem+json`.
Pole `type` wskazuje odpowiednią sekcję tego dokumentu, a `instance` ścieżkę żądania.
Pełne uporządkowanie błędów pozostałych endpointów i modułu common pozostaje do wykonania.

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
Sprawdź numer; po przyszłym wprowadzeniu asynchronicznych aktualizacji możliwe
będzie krótkie opóźnienie widoczności nowo utworzonego zwrotu.

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

## Unsupported media type

Kod: `UNSUPPORTED_MEDIA_TYPE`.

HTTP 415. Żądanie musi mieć `Content-Type: application/json`.

## Not acceptable

Kod: `NOT_ACCEPTABLE`.

HTTP 406. Nagłówek `Accept` musi dopuszczać odpowiedź JSON.

## Http request error

Kod: `HTTP_REQUEST_ERROR`.

Błąd protokołu HTTP, np. niedozwolona metoda. Popraw sposób wywołania endpointu.

## Internal error

Kod: `INTERNAL_ERROR`.

HTTP 500. Nieoczekiwany błąd serwera. Można ponowić żądanie z tym samym kluczem
oraz dokładnie tym samym ciałem. Transakcja nie zapisuje częściowego zwrotu.
Szczegóły techniczne pozostają w logach serwera i nie są zwracane klientowi.

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
