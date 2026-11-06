# OpenPayments — budżet domowy na Androida

Aplikacja do kontroli wydatków inspirowana [Sumply](https://www.sumply.pl/): ręczne wpisy, skanowanie paragonów,
automatyczna kategoryzacja, budżety z ostrzeżeniami, płatności cykliczne i statystyki. Działa w pełni offline.

## Funkcje
| Funkcja | Realizacja |
|---|---|
| Ręczne wpisy wydatków i przychodów | `EditTransactionScreen` |
| Skan paragonu (sklep, data, suma, **lista pozycji**) | skaner dokumentów ML Kit, własne kadrowanie i prostowanie perspektywy (`DocumentDetector`), ML Kit Text Recognition (na urządzeniu) + `ReceiptParser` |
| Zdjęcie paragonu przy transakcji | plik w `files/receipts`, podgląd pełnoekranowy |
| Automatyczna kategoryzacja, „poprawiasz raz — pamięta” | `Categorizer` + tabela `category_rules` |
| Budżety (ogólny i per kategoria) + powiadomienia przed przekroczeniem | `BudgetsScreen`, `Notifier` |
| Subskrypcje / płatności cykliczne, automatyczne księgowanie i przypomnienia | `SubscriptionsScreen`, `DailyWorker` (WorkManager) |
| Statystyki: 6 miesięcy, tempo wydatków, kategorie m/m, top sklepy | `StatsScreen`, własne wykresy na Canvas |
| Eksport CSV, kopia zapasowa JSON, własne kategorie, motyw jasny/ciemny | `SettingsScreen`, `Backup` |
| Blokada aplikacji (biometria / PIN telefonu) | `MainActivity`, androidx.biometric |
| Ranking produktów z paragonów | `StatsScreen`, zapytanie `observeProducts` |
| Automatyczne aktualizacje przez redzikowski.com | `update/Updates.kt` |
| Rozwijanie skrótów z paragonów, polskie znaki, grupy produktów, nauka z poprawek | `ProductDictionary`, tabela `product_aliases` |

Poza zakresem (wymaga serwera albo oficjalnych API sieci handlowych): integracje z Lidl Plus/Biedronką/Żabką
oraz budżet współdzielony między kontami.

## Architektura
- **Kotlin + Jetpack Compose, Material 3 Expressive (Android 16)**: kolory z tapety, MotionScheme.expressive, LargeFlexibleTopAppBar, ShortNavigationBar, FloatingActionButtonMenu, SegmentedListItem, MaterialShapes; jedna aktywność, Navigation Compose
- **Room** (SQLite): `Category`, `Transaction`, `ReceiptItem`, `Budget`, `Subscription`, `CategoryRule`
- `Repository` z logiką domenową, `MainViewModel` z `StateFlow` dla UI
- Kwoty w groszach (`Long`), daty jako epoch day
- `ReceiptScanner` przycina paragon i prostuje perspektywę (`DocumentDetector`, `ReceiptImage`), czyta kilka wariantów obrazu, `OcrLayout` skleja linie OCR w wiersze z poprawką na przekrzywienie, a `ReceiptParser` wyciąga dane i wybierany jest wariant, w którym suma pozycji zgadza się z sumą paragonu

```
app/src/main/java/pl/openpayments/
├── data/      encje, DAO, baza, repozytorium, ustawienia
├── domain/    parser paragonów, OCR, kategoryzacja, formatowanie kwot
├── notify/    kanały powiadomień, dzienny worker
├── update/    aktualizator (SHA-256 + weryfikacja certyfikatu)
└── ui/        motyw, komponenty, wykresy, ekrany
```

## Aktualizacje i wydania
Aplikacja pyta `redzikowski.com/api/releases/repos/BlackSingularities/OpenPayments/releases/latest`,
pobiera `OpenPayments.apk` i sprawdza sumę SHA-256, nazwę pakietu, numer wersji oraz certyfikat podpisu.
Dopiero potem instaluje plik przez `PackageInstaller`.

Wydanie: podbij `VERSION`, dopisz sekcję w `CHANGELOG.md`, a potem `git tag vX.Y.Z && git push origin vX.Y.Z`.
GitHub Actions zbuduje i podpisze APK (sekrety `SIGNING_KEY`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`),
sprawdzi certyfikat i opublikuje wydanie.

## Budowanie
Wymagania: JDK 17 i Android SDK 36 (ścieżka w `local.properties`).
```
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # testy parsera, kwot, kategoryzacji, kopii i aktualizatora
```

---
Adam Rędzikowski · [redzikowski.com](https://redzikowski.com) · kontakt@redzikowski.com
