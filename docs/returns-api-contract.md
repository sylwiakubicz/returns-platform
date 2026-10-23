# Kontrakt API zwrotów — etap 3, krok 6

Źródłem kontraktu jest `contracts/src/main/resources/openapi/returns-v1.yaml`.
Specyfikacja opisuje docelowe endpointy kroku 6. Obecnie działają
`POST /admin/projections/rebuild`, `POST /api/v1/returns`,
`GET /api/v1/returns`, `GET /api/v1/returns/{numer}`,
`GET /api/v1/returns/{numer}/history` i `POST /api/v1/returns/{numer}/cancel`.
Działają również wszystkie osiem zaplanowanych operacji symulacyjnych: wysyłka,
przyjęcie paczki, inspekcja, wykonanie refundacji i zamknięcie zwrotu.

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
więc widzą ten sam stan danych pomimo równoczesnych zapisów. Pozostałe endpointy symulacyjne są wymienione w opisie postępu poniżej. Pełna ścieżka
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

## Punkt 5 — partia 2: szczegóły i lista przez HTTP

ReturnQueryController udostępnia GET /api/v1/returns/{numer} i GET /api/v1/returns.
Numer w ścieżce musi mieć format RMA-RRRR-NNNNNN; inny format daje 400,
nieistniejący poprawny numer daje 404 z kodem RETURN_NOT_FOUND.
Domyślne parametry listy: page=0, size=20; maksymalny size to 100.
Opcjonalny status przyjmuje dokładną nazwę stanu. Wyniki są uporządkowane po
numerze biznesowym malejąco. Błędne parametry dają 400 z nazwą parametru w errors.
Pusta strona lub brak wyników zgodnych z filtrem to 200, nie 404.

ReturnResponse zapisuje kwotę jako tekst z dwoma miejscami po przecinku oraz
walutę jako kod ISO. Brak decyzji refundacyjnej to null, a odmowa zwrotu może
mieć kwotę "0.00". updatedAt pochodzi z bazy i jest datą ISO 8601,
bez zastępowania jej bieżącym czasem.

## Punkt 5 — partia 3: anulowanie i historia przez HTTP

POST /api/v1/returns/{numer}/cancel wymaga JSON z niepustym cancelledBy;
reason jest opcjonalnym tekstem lub null. Sukces to 204 bez ciała. Powstaje
RmaCancelled, a zwrot pozostaje w CANCELLED; nie zamykamy go automatycznie.
Tożsamość jest polem wejściowym do czasu uwierzytelniania w etapie 4.
Brak zwrotu daje 404, niepoprawne dane 400, odmowa anulowania lub konflikt
wersji 409. Nieudane polecenie nie dopisuje zdarzenia.

GET /api/v1/returns/{numer}/history zwraca całą tablicę zdarzeń według wersji,
także sprzed snapshotu i niezależnie od obecności projekcji. Każdy wpis ma
eventId, rmaId, version, type, schemaVersion, occurredAt i payload.
schemaVersion opisuje aktualny format payloadu po upcastingu; nie jest wersją
agregatu ani koniecznie wersją pierwotnego zapisu. Źródło wersji formatu jest
wspólne z istniejącym mechanizmem kodowania zdarzeń.

ReturnHistoryResponseMapper przygotowuje JSON tylko na potrzeby HTTP:
kwoty są tekstem z walutą, daty są ISO 8601. Nie zmienia JSON zapisanych zdarzeń
ani klas domenowych. Testy HTTP obejmują ponad 50 zdarzeń, istniejący snapshot,
zdarzenie po snapshocie oraz odczyt przy pustej projekcji.

## Punkt 5 — partia 4: symulowanie wysyłki i przyjęcia paczki

Działają cztery tymczasowe operacje POST pod /internal/sim/returns/{numer}/:

| Końcówka ścieżki | Ciało żądania | Wynikowy stan |
| --- | --- | --- |
| label-issued | labelId, trackingNumber, labelFileKey | LABEL_ISSUED |
| pickup-scheduled | pickupId, scheduledFor (data ISO) | PICKUP_SCHEDULED |
| parcel-in-transit | bez ciała | IN_TRANSIT |
| parcel-received | warehouseId | RECEIVED |

Sukces to 204. Zaplanowanie odbioru jest opcjonalne; transport można rozpocząć
zarówno z LABEL_ISSUED, jak i PICKUP_SCHEDULED. Czasy zdarzeń nadaje zegar
aplikacji, a dane klienta nie mogą ich zastąpić. scheduledFor jest planowaną datą
odbioru, odrębną od czasu zapisania zdarzenia. Błędne dane dają 400, brak zwrotu
404, niedozwolona kolejność lub ponowienie tej samej zmiany stanu 409.
Operacje są transakcyjne i zachowują wersję sprzed polecenia przy zapisie zdarzenia.

ShipmentSimulationController odczytuje i sprawdza żądania, a
ShipmentSimulationService wywołuje istniejące metody Rma i zapisuje zdarzenia.
Te ścieżki jedynie symulują fakty z integracji; nie generują etykiety ani nie
zamawiają rzeczywistego transportu. Pozostają deprecated w OpenAPI i mają
zostać usunięte w etapie 6 projektu.

## Punkt 5 — partia 5: inspekcja, refundacja i zamknięcie

Działają kolejne tymczasowe operacje POST pod /internal/sim/returns/{numer}/:

| Końcówka | Dane JSON | Wynik |
| --- | --- | --- |
| inspection-started | inspectorId | UNDER_INSPECTION |
| inspection-completed | inspectorId, results, opcjonalnie approvedBy | REFUND_PENDING lub REJECTED |
| refund-executed | payoutId, amount (kwota i waluta), attempt | REFUNDED |
| close | closureReason | CLOSED |

Sukces to 204. Zakończenie inspekcji zapisuje InspectionCompleted i RefundDecided
w jednej transakcji. Wynik i kwotę wylicza Rma na podstawie powodów zwrotu,
ocen pozycji oraz skonfigurowanych potrąceń i opłaty za wysyłkę. Nie przyjmujemy
od klienta wyliczonej decyzji. SimulationPolicyConfiguration podłącza istniejące
ustawienia returns.return-shipping-fee, returns.deduction.* oraz
inspection.supervisor-approval-threshold do zasad obliczeń.

Reguły wymagają wyników dla wszystkich pozycji oraz zgody przełożonego dla
oceny D powyżej progu wartości. W symulacji approvedBy jest podanym identyfikatorem,
a sprawdzanie uprawnień należy do etapu 4. Refundacja musi mieć kwotę i walutę
zgodne z decyzją, a przyczyna zamknięcia musi odpowiadać stanowi zwrotu.
Niedozwolony stan daje 409; niezgodna kwota/waluta lub przyczyna zamknięcia daje
422 z kodem SIMULATION_COMMAND_REJECTED. Błędy nie dopisują zdarzeń.

Testy HTTP przechodzą od utworzenia do CLOSED i sprawdzają pełną historię.
Obejmują też potrącenia B/C, odrzucenie D, pełną kwotę przy DAMAGED_IN_TRANSIT,
brak zgody przełożonego, niezgodną wypłatę oraz błędne dane i kolejność.
Wszystkie symulacje nadal są deprecated i przewidziane do usunięcia w etapie 6.
