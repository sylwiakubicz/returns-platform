# Kontrakt API zwrotów — etap 3, krok 6

Źródłem kontraktu jest `contracts/src/main/resources/openapi/returns-v1.yaml`.
Specyfikacja opisuje docelowe endpointy kroku 6; obecnie zaimplementowany jest
wyłącznie `POST /admin/projections/rebuild`. Dokument nie oznacza wdrożenia API.

## Ustalenia kontraktu

- Ścieżki używają numeru `RMA-RRRR-NNNNNN`. UUID występuje wyłącznie
  w metadanych historii, nigdy jako parametr ścieżki.
- Utworzenie zwraca `201`, model projekcji i `Location`. Polecenia anulowania
  oraz symulacji zwracają `204`; stan można następnie odczytać przez GET.
- Odczyt zawiera `businessNumber`, `status`, `refundAmount` i `updatedAt`.
  Kwota to obiekt z dokładną wartością dziesiętną jako tekstem i walutą ISO 4217;
  przed decyzją refundacyjną jest `null`. Projekcja wymaga dodania waluty
  nową migracją i obsługi jej podczas aktualizacji oraz odbudowy.
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
