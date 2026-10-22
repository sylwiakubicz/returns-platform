# Kontrakty i błędy API

Ten katalog obejmuje obecnie endpoint `POST /api/v1/returns`.
Błędy mają format RFC 9457 i typ treści `application/problem+json`.
Pole `type` wskazuje odpowiednią sekcję tego dokumentu, a `instance` ścieżkę żądania.
Pełne uporządkowanie błędów pozostałych endpointów i modułu common pozostaje do wykonania.

## Invalid request

Kod: `INVALID_REQUEST`.

HTTP 400. Brak wymaganego nagłówka lub ciała, błędny JSON albo niepoprawne pola.
`errors` zawiera pole i komunikat. `$` oznacza całe ciało lub ogólny błąd żądania.
Popraw dane przed ponowieniem. Nieudana operacja nie zajmuje klucza.

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
