## 1.4.0

- Skanowanie paragonów z automatycznym kadrowaniem: przycisk „Skanuj paragon” otwiera skaner dokumentów, który sam znajduje krawędzie paragonu, pozwala poprawić narożniki i prostuje perspektywę.
- Zdjęcia z aparatu i galerii też są przycinane i prostowane przed odczytem: aplikacja wycina paragon z tła (np. z podkładki z napisami), wyrównuje cienie i kontrast, a paragon sfotografowany bokiem obraca.
- Długie paragony są czytane kawałkami, żeby drobny druk był czytelny. Aplikacja porównuje kilka wariantów odczytu i wybiera ten, w którym suma pozycji zgadza się z sumą paragonu.
- Lepsze składanie wierszy: nazwa i cena z przekrzywionego zdjęcia nie mieszają się już między wierszami.
- Paragony z Biedronki: cena po rabacie w osobnej linii, rabat rozbity na dwie linie, zgubiona ilość przed „x”, nazwy pisane łącznie („MullMilchCzek” → „Müller Milch czekolada”, „BułecŚniad” → „Bułeczki śniadaniowe”).
- Sklep rozpoznawany mimo literówek OCR („Biedronko”) i po nazwie spółki z nagłówka. Suma także wtedy, gdy kwota trafi do następnej linii albo z sumy sprzedaży opodatkowanej.
- Przycięty i wyprostowany paragon zapisuje się przy transakcji zamiast całego zdjęcia.

## 1.3.0

- Rozwijanie skróconych nazw z paragonów: „SER ZOLT.GOUDA PLAS.150G” → „Ser żółty gouda plastry 150 g”. Słownik kilkuset produktów i skrótów kasowych, z przywracaniem polskich znaków.
- Aplikacja uczy się z poprawek: popraw nazwę produktu raz, a na kolejnych paragonach rozpozna ją sama.
- Grupy produktów (nabiał, pieczywo, mięso, warzywa i owoce, chemia, napoje…) i nowa karta „Co jest w koszyku” w statystykach.
- Mocniejsze odczytywanie paragonów: pozycje rozbite na dwie linie, rabaty i opusty przypisane do produktu, towar na wagę, typowe pomyłki OCR w kwotach i rozbite słowa.
- Przy sprawdzaniu paragonu widać oryginalny zapis z paragonu i grupę każdej pozycji.
- Kopia zapasowa obejmuje wyuczone nazwy produktów.

## 1.2.2

- Zakładki bez dużych nagłówków powtarzających nazwę z paska nawigacji. W górnym pasku zostaje tylko to, co ważne w danej chwili: miesiąc, liczba pozycji z sumą wydatków albo dni do końca miesiąca.

## 1.2.1

- Wydanie porządkowe interfejsu w stylu Androida 16: czytelniejsze ikony kategorii w trybie ciemnym, spokojniejsza siatka wykresu miesięcy, ikona portfela przy budżecie ogólnym.

## 1.2.0

- Nowy interfejs w stylu Androida 16 (Material 3 Expressive): sprężyste animacje, mocniej zaokrąglone kształty i nowa typografia.
- Kolory z tapety (Material You) na Androidzie 12 i nowszym, z możliwością powrotu do zielonej palety aplikacji.
- Duże, zwijane nagłówki ekranów i nowy pasek nawigacji.
- Przycisk dodawania rozwija menu: skan paragonu, wydatek, przychód.
- Listy zgrupowane jak w ustawieniach Androida 16; ikony kategorii w kształtach Expressive.
- Falujące paski postępu budżetu, nowy wskaźnik ładowania przy skanowaniu, połączone przyciski wyboru.
- Przewidywane cofanie (predictive back) i płynniejsze przejścia między ekranami.
- Poprawki czytelności w trybie ciemnym.

## 1.1.0

- Automatyczne aktualizacje przez redzikowski.com: sprawdzanie przy uruchomieniu, pobieranie z weryfikacją SHA-256 i certyfikatu podpisu, instalacja bez wychodzenia z aplikacji.
- Podpisane wydania i informacja o podpisie w sekcji „O aplikacji”.
- Autor i kontakt: Adam Rędzikowski · redzikowski.com, kontakt@redzikowski.com, „Postaw kawę”.
- Ekran powitalny z ustawieniem miesięcznego budżetu.
- Blokada aplikacji odciskiem palca, twarzą lub PIN-em telefonu.
- Kopia zapasowa i przywracanie danych z pliku JSON.
- Usuwanie transakcji przesunięciem w bok z możliwością cofnięcia.
- Ranking najdroższych produktów z paragonów w statystykach.
- Kopia danych w systemowym backupie Androida.
- Poprawki: pusty ekran statystyk, nieużywane zdjęcia paragonów są sprzątane.

## 1.0.0

- Pierwsza wersja: wydatki i przychody, skan paragonu (OCR na urządzeniu), automatyczna kategoryzacja, budżety z powiadomieniami, płatności cykliczne, statystyki, eksport CSV.
