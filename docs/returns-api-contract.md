# Kontrakt API zwrotów — etap 3, krok 6

Źródłem kontraktu jest `contracts/src/main/resources/openapi/returns-v1.yaml`.
Specyfikacja opisuje docelowe endpointy kroku 6. Obecnie działają
`POST /admin/projections/rebuild` i `POST /api/v1/returns`; pozostałe endpointy
publiczne oraz symulacyjne pozostają do wykonania.

## Stan realizacji punktu 2 planu API

Gotowe są modele, porty i przypadki użycia tworzenia, anulowania, szczegółów,
listy i pełnej historii zwrotu. Działają generator UUIDv7, transakcyjny generator
numeru biznesowego oraz konfiguracja zegara UTC. Numery są przydzielane z licznika
danego roku (wyznaczonego w UTC z czasu zgłoszenia); rollback cofa przydział,
a przekroczenie 999999 jest odrzucane przez bazę. UUIDv7 nie gwarantuje kolejności
w tej samej milisekundzie ani przy cofnięciu zegara.

Tworzenie i anulowanie wykonują się w transakcji. Zapis zdarzeń aktualizuje
projekcję przez istniejący adapter. Anulowanie przekazuje do magazynu wersję
agregatu sprzed polecenia domenowego. Wyszukiwanie UUID po numerze korzysta
ze zdarzenia RmaRequested, więc nie zależy od aktualności projekcji.

Historia jest odczytywana od wersji 1, dekodowana przez StoredEventDecoder
(z istniejącą obsługą upcastingu) i materializowana w liście przed zamknięciem
strumienia oraz transakcji. HistoryEntry jest modelem aplikacyjnym, nie DTO HTTP;
mapowanie na payload i aktualny schemaVersion z OpenAPI pozostaje do wykonania.

ReturnQueryService i PostgresRmaProjectionReader są zarejestrowane w Springu.
Szczegóły i lista są odczytywane z PostgreSQL. Liczenie wyników i pobieranie strony
działają w jednej transakcji tylko do odczytu, na poziomie REPEATABLE_READ,
więc widzą ten sam stan danych pomimo równoczesnych zapisów. Pozostałe kontrolery
publiczne i endpointy symulacyjne nadal nie są zaimplementowane. Pełna ścieżka
przez API pozostaje do wykonania.

Punkt 4, partie 1–3: IdempotentReturnCreationService oblicza SHA-256 surowego
ciała i sam odczytuje z niego zgłoszenie (żeby skrót i dane operacji dotyczyły
tych samych bajtów). Nowy klucz tworzy zwrot oraz zapisuje status 201 i JSON
odpowiedzi w jednej transakcji. Powtórzenie zwraca zapisane dane; Location
jest odtwarzane z zapisanego businessNumber. Inne ciało dla tego samego klucza
zgłasza IdempotencyKeyReuseException, przeznaczony do mapowania na HTTP 422.
JSONB zachowuje dane odpowiedzi, ale nie jej oryginalne formatowanie.
Partia 4: przed odczytem klucza operacja uzyskuje transakcyjną blokadę PostgreSQL
dla pary klucz–ścieżka. Drugie żądanie z tą samą parą czeka do commit lub rollback
pierwszego. READ_COMMITTED pozwala następnie odczytać zatwierdzoną odpowiedź.
Blokada nie tworzy wpisu klucza i nie wymaga zmiany schematu. Jej identyfikator
to 64 bity skrótu pary; ewentualna kolizja skrótów powoduje tylko dodatkowe
czekanie, a porównanie klucza i skrótu ciała nadal korzysta z pełnych wartości.
To blokada obsługi idempotencji, niezależna od kontroli wersji agregatu, która
nadal opiera się na unikalności (rma_id, version). Test identycznych równoczesnych
żądań jest powtarzany 20 razy; dodatkowe testy obejmują różne ciała i rollback
pierwszego żądania podczas oczekiwania drugiego.

Partia 5: wpisy starsze niż 24 godziny są usuwane przez IdempotencyCleanupJob,
domyślnie co godzinę. Konfiguracja: returns.idempotency.retention=PT24H oraz
returns.idempotency.cleanup-delay=PT1H. Przy obsłudze konkretnego klucza jego
przeterminowany wpis jest również usuwany, po uzyskaniu blokady i w tej samej
transakcji. Wygaśnięcie nie zależy więc od momentu ostatniego czyszczenia tabeli.
Wpis mający dokładnie 24 godziny nadal obowiązuje (usuwamy starsze wpisy).
Czyszczenie nie usuwa zwrotów ani ich historii. Po wygaśnięciu ten sam klucz
może utworzyć kolejny zwrot, również dla zmienionego ciała żądania.
Punkt 4 planu jest zakończony w warstwie aplikacji i bazy. Endpoint tworzenia i jego mapowanie błędów zostały dodane w punkcie 5, partii 1.

## Ustalenia kontraktu

Punkt 3 planu API jest zakończony: odczyt pojedynczego zwrotu, lista z filtrem
stanu i paginacją oraz pełna historia działają z PostgreSQL. Test integracyjny
potwierdza odczyt całej historii także przy istniejącym snapshocie i pustej
projekcji. Drugi test porównuje szczegóły, listę oraz historię przed odbudową
projekcji i po niej. Endpointy HTTP pozostają do implementacji w kolejnych
punktach planu; testy te wywołują bezpośrednio operacje aplikacji.

- Ścieżki używają numeru `RMA-RRRR-NNNNNN`. UUID występuje wyłącznie
  w metadanych historii, nigdy jako parametr ścieżki.
- Utworzenie zwraca `201`, model projekcji i `Location`. Polecenia anulowania
  oraz symulacji zwracają `204`; stan można następnie odczytać przez GET.
- Odczyt zawiera `businessNumber`, `status`, `refundAmount` i `updatedAt`.
  Kwota to obiekt z dokładną wartością dziesiętną jako tekstem i walutą ISO 4217;
  przed decyzją refundacyjną jest `null`. Walutę przechowuje kolumna `currency`
  dodana migracją V3. Istniejące wpisy są uzupełniane ze zdarzeń RmaRequested,
  a nowe wpisy i odbudowa zapisują walutę zgłoszenia. Brak waluty w historii
  istniejącego wpisu zatrzymuje migrację zamiast przypisywać domyślne PLN.
- `updatedAt` oznacza czas ostatniego zdarzenia zastosowanego do projekcji.
  Nie jest czasem obsługi żądania. Lista niesie ten znacznik dla każdego wpisu.
- Paginacja: `page=0`, `size=20`, limit `100`; przekroczenie limitu daje `400`.
  Filtr `status` przyjmuje pojedynczy stan. Kolejność: numer biznesowy malejąco.
- Historia to tablica wszystkich zdarzeń posortowanych po wersji rosnąco,
  również sprzed snapshotu. Zawiera metadane i typowany payload wszystkich
  14 zdarzeń. Payload jest upcastowany; `schemaVersion` opisuje zwracany format.
- `Idempotency-Key` jest obowiązkowy przy tworzeniu. Klucz jest związany
  z endpointem. SHA-256 liczony jest z surowych bajtów ciała, więc zmiana
  formatowania JSON również oznacza inne ciało. Powtórzenie zwraca zapamiętane
  `201`, ciało i `Location`; inne ciało daje `422`. Retencja wynosi 24 godziny.
  Jednoczesne żądania muszą utworzyć jeden zasób. Wycofana transakcja nie zajmuje klucza.
- Anulowanie zapisuje `RmaCancelled`, pozostawiając stan `CANCELLED`.
  Zamknięcie jest osobnym poleceniem. Tożsamość klienta jest polem wejściowym
  do czasu wprowadzenia uwierzytelniania w etapie 4.
- `/internal/sim/**` ma `deprecated: true` i zostanie usunięte w etapie 6 projektu.
  `/admin/**` pozostaje; w etapie 4 odbudowa wymaga roli przełożonego.
- Błędy używają `application/problem+json` (RFC 9457), kodu i URI dokumentacji
  w `type`. Walidacja niesie listę pól. Statusy: `400` niepoprawne wejście,
  `404` brak zasobu, `409` konflikt wersji lub stanu, `422` reguły domeny
  albo inne ciało dla klucza, `500` nieoczekiwany błąd bez szczegółów wewnętrznych.
  Pełny katalog i dokumentacja URI błędów należą do kroku 7.

## Ścieżka pozytywna

Utworzenie → `label-issued` → `parcel-in-transit` → `parcel-received` →
`inspection-started` → `inspection-completed` → `refund-executed` → `close` →
odczyt zwrotu (`CLOSED`) i historii. Wszystkie symulacje mają prefiks
`/internal/sim/returns/{businessNumber}/`. `pickup-scheduled` jest opcjonalne.
Czasy zdarzeń nadaje serwer. Zakończenie inspekcji zapisuje również decyzję
refundacyjną w tej samej transakcji.

## Weryfikacja

```sh
npx --yes @redocly/cli@2.63.0 lint --config redocly.yaml
```

Ten sam lint działa jako osobne zadanie w `.github/workflows/pr.yaml`.
Konfiguracja stosuje `recommended-strict`; pomija wymóg deklaracji licencji,
ponieważ projekt nie ma zadeklarowanej licencji dystrybucji. Dopuszcza również
adres `localhost`, będący obecnie jedynym środowiskiem aplikacji.
Reguły lintera: https://redocly.com/docs/cli/commands/lint.

Brakuje źródłowej specyfikacji `docs/01-domena.md` i diagramów. Powyższe
ustalenia oparto na dokumencie etapu oraz obecnym modelu domeny; trzeba je
porównać ze specyfikacją domeny przed implementacją przypadków użycia.

## Punkt 5 — partia 1: endpoint tworzenia

`POST /api/v1/returns` wymaga JSON i nagłówka Idempotency-Key. Kontroler przekazuje
surowe bajty do IdempotentReturnCreationService; zwraca zapisany status 201, JSON
oraz Location. Zgłoszenie jest odczytywane dopiero po sprawdzeniu klucza. Parser
sprawdza wymagane pola i ich format, odrzuca nieznane pola oraz dodatkowy JSON
po pierwszym obiekcie. Ilości muszą być całkowite; kwoty mają postać tekstu
z dwoma miejscami po przecinku. Reguły terminu i zgodności walut sprawdza domena.

ReturnApiExceptionHandler obsługuje błędy tworzenia oraz protokołu HTTP
(również gdy Spring odrzuci żądanie przed wyborem kontrolera). Zwraca
ProblemDetail dla błędnego wejścia (400), konfliktu klucza i odmowy domeny (422),
nieobsługiwanego typu treści (415), nieakceptowanej odpowiedzi (406) i błędu
wewnętrznego (500). Katalog kodów znajduje się w docs/03-kontrakty.md.
Wspólna obsługa wszystkich endpointów pozostaje do późniejszego uporządkowania.
