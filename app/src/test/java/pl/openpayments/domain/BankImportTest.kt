package pl.openpayments.domain

import org.junit.Assert.*
import org.junit.Test
import pl.openpayments.data.Category
import pl.openpayments.data.Transaction
import pl.openpayments.data.TxSource
import pl.openpayments.domain.bank.*
import java.time.LocalDate

class BankImportTest {

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    private fun load(text: String, charset: java.nio.charset.Charset = Charsets.UTF_8): Pair<Analysis, ReadResult> {
        val a = BankStatement.analyze(BankStatement.table(text.toByteArray(charset)))
        return a to BankStatement.read(a)
    }

    private val cats = listOf(
        Category(1, "Zakupy spożywcze", "cart", 0), Category(2, "Rachunki", "bolt", 0), Category(3, "Transport", "car", 0),
        Category(4, "Restauracje", "food", 0), Category(5, "Subskrypcje", "repeat", 0), Category(6, "Inne", "dots", 0),
        Category(7, "Wynagrodzenie", "wallet", 0, isIncome = true), Category(8, "Inne przychody", "plus", 0, isIncome = true),
        Category(9, "Zdrowie", "health", 0),
    )

    // ——— Formaty banków ———

    @Test fun mBankWithPreambleAndFooter() {
        val csv = """
            mBank S.A. Bankowość Detaliczna;
            Skrytka Pocztowa 2108;
            #Klient;
            JAN KOWALSKI;
            #Za okres:;
            01.11.2026;30.11.2026;
            #Waluta;
            PLN;

            #Data operacji;#Opis operacji;#Rachunek;#Kategoria;#Kwota;
            2026-11-03;"BIEDRONKA 1234       KRAKOW              ";"eKonto 1111 ... 2222";"Żywność i chemia domowa";"-45,20 PLN";
            2026-11-04;"ZABKA Z5512 K.1      WARSZAWA";"eKonto 1111 ... 2222";"Żywność i chemia domowa";"-12,99 PLN";
            2026-11-05;"PRZELEW PRZYCHODZĄCY ACME SP. Z O.O. WYNAGRODZENIE ZA 10/2026";"eKonto 1111 ... 2222";"Wpływy";"5 400,00 PLN";
            2026-11-06;"SKLEP U ANI 22 /GDANSK";"eKonto 1111 ... 2222";"Bez kategorii";"-8,50 PLN";
            ;;;#Saldo końcowe;1 234,56 PLN;
        """.trimIndent()
        val (a, r) = load(csv)
        assertEquals("mBank", a.bank)
        assertEquals(Role.DATE, a.roles[0]); assertEquals(Role.DESCRIPTION, a.roles[1])
        assertEquals(Role.IGNORE, a.roles[2]); assertEquals(Role.CATEGORY, a.roles[3]); assertEquals(Role.AMOUNT, a.roles[4])
        assertEquals(4, r.rows.size)
        assertEquals(SignMode.SIGNED, r.signMode)
        assertEquals(-4520L, r.rows[0].amount)
        assertEquals(540000L, r.rows[2].amount)
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), emptyList()).associateBy { it.date }
        assertEquals("Biedronka", plan[day(2026, 11, 3)]!!.merchant)
        assertEquals(1L, plan[day(2026, 11, 3)]!!.categoryId)
        assertEquals("Żabka", plan[day(2026, 11, 4)]!!.merchant)
        assertEquals("Sklep u Ani", plan[day(2026, 11, 6)]!!.merchant)
        assertTrue(plan[day(2026, 11, 5)]!!.isIncome)
        assertEquals(7L, plan[day(2026, 11, 5)]!!.categoryId)
    }

    @Test fun pkoWithKeyValueDescriptions() {
        val csv = """
            "Data operacji","Data waluty","Typ transakcji","Kwota","Waluta","Saldo po transakcji","Opis transakcji","","","",""
            "2026-11-03","2026-11-03","Płatność kartą","-45.20","PLN","+1234.56","Tytuł: 000498849 74230786213","Lokalizacja: Adres: SKLEP ZIELARSKI 12 Miasto: KRAKOW Kraj: POLSKA","Data i czas operacji: 2026-11-02 18:21:00","Oryginalna kwota operacji: 45.20 PLN","Numer karty: 425125******1234"
            "2026-11-02","2026-11-02","Przelew na konto","-1500.00","PLN","+1279.76","Rachunek odbiorcy: 11 2222 3333 4444 5555 6666 7777","Nazwa odbiorcy: WSPÓLNOTA MIESZKANIOWA JASNA 5","Tytuł: CZYNSZ LISTOPAD","",""
            "2026-11-01","2026-11-01","Płatność web - kod mobilny","-49.99","PLN","+2779.76","Tytuł: NETFLIX.COM 123","Numer telefonu: 48500100200","","",""
        """.trimIndent()
        val (a, r) = load(csv)
        assertEquals("PKO BP", a.bank)
        assertEquals(Role.DATE, a.roles[0]); assertEquals(Role.TYPE, a.roles[2]); assertEquals(Role.AMOUNT, a.roles[3])
        assertEquals(Role.CURRENCY, a.roles[4]); assertEquals(Role.BALANCE, a.roles[5]); assertEquals(Role.DESCRIPTION, a.roles[6])
        assertEquals(Role.DESCRIPTION, a.roles[7])
        assertEquals(3, r.rows.size)
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), emptyList()).associateBy { it.date }
        assertEquals("Sklep Zielarski", plan[day(2026, 11, 3)]!!.merchant)
        assertEquals("Wspólnota Mieszkaniowa Jasna", plan[day(2026, 11, 2)]!!.merchant)
        assertTrue(plan[day(2026, 11, 2)]!!.note.contains("CZYNSZ LISTOPAD"))
        assertEquals("Netflix", plan[day(2026, 11, 1)]!!.merchant)
        assertEquals(5L, plan[day(2026, 11, 1)]!!.categoryId)
        assertFalse(plan[day(2026, 11, 3)]!!.note.contains("425125"))
    }

    @Test fun ingWithBlockadesAndManyCurrencyColumns() {
        val csv = """
            "Lista transakcji"
            "Dokument nr 0123456/2026"
            "Data wygenerowania: 2026-11-30"
            "Nazwa rachunku:";"ING Konto z Lwem"
            "Data transakcji";"Data księgowania";"Dane kontrahenta";"Tytuł";"Nr rachunku";"Nazwa banku";"Szczegóły";"Nr transakcji";"Kwota transakcji (waluta rachunku)";"Waluta";"Kwota blokady/zwolnienie blokady";"Waluta";"Kwota płatności w walucie";"Waluta";"Konto";"Saldo po transakcji";"Waluta";;;
            2026-11-04;2026-11-05;" LIDL SP. Z O.O. SP.K. UL. POZNANSKA 48 JANKOWICE";"Płatność kartą 04.11.2026 Nr karty 4246xx1234";;;" TR.KART";"2026310123456";-32,46;PLN;;;;;"ING Konto z Lwem";1 200,00;PLN;;;
            2026-11-05;;" ORLEN STACJA NR 123 KRAKOW";"Płatność kartą 05.11.2026 Nr karty 4246xx1234";;;" TR.KART";"2026310999999";;;-250,00;PLN;;;"ING Konto z Lwem";;;;;
            2026-11-06;2026-11-06;"JAN KOWALSKI UL. DŁUGA 5 00-001 WARSZAWA";"Zwrot za bilety";"'12 3456 7890 1234 5678 9012 3456";"ING";" PRZELEW";"2026310777777";120,00;PLN;;;;;"ING Konto z Lwem";1 320,00;PLN;;;
        """.trimIndent()
        val (a, r) = load(csv)
        assertEquals("ING Bank Śląski", a.bank)
        assertEquals(Role.DATE, a.roles[0]); assertEquals(Role.MERCHANT, a.roles[2]); assertEquals(Role.DESCRIPTION, a.roles[3])
        assertEquals(Role.AMOUNT, a.roles[8]); assertEquals(Role.CURRENCY, a.roles[9]); assertEquals(Role.BALANCE, a.roles[15])
        assertEquals(Role.IGNORE, a.roles[10]); assertEquals(Role.IGNORE, a.roles[4]); assertEquals(Role.IGNORE, a.roles[5])
        // Blokada nie ma kwoty transakcji — nie jest importowana.
        assertEquals(2, r.rows.size)
        assertEquals(1, r.ignoredLines)
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), emptyList()).associateBy { it.date }
        assertEquals("Lidl", plan[day(2026, 11, 4)]!!.merchant)
        assertEquals(3246L, plan[day(2026, 11, 4)]!!.amount)
        assertEquals("Jan Kowalski", plan[day(2026, 11, 6)]!!.merchant)
        assertTrue(plan[day(2026, 11, 6)]!!.isIncome)
    }

    @Test fun millenniumDebitCreditColumns() {
        val csv = """
            Numer rachunku/karty,Data transakcji,Data rozliczenia,Rodzaj transakcji,Na konto/Z konta,Odbiorca/Zleceniodawca,Opis,Obciążenia,Uznania,Saldo,Waluta
            "PL61116022020000000123456789","2026-11-03","2026-11-04","PŁATNOŚĆ KARTĄ","","KAUFLAND PL 6150 KRAKOW","Zakupy","-87.15","","1 912.85","PLN"
            "PL61116022020000000123456789","2026-11-01","2026-11-01","PRZELEW PRZYCHODZĄCY","PL12...","ACME SA","Wynagrodzenie","","5400.00","2 000.00","PLN"
        """.trimIndent()
        val (a, r) = load(csv)
        assertEquals("Bank Millennium", a.bank)
        assertEquals(Role.IGNORE, a.roles[0]); assertEquals(Role.DATE, a.roles[1]); assertEquals(Role.TYPE, a.roles[3])
        assertEquals(Role.MERCHANT, a.roles[5]); assertEquals(Role.DEBIT, a.roles[7]); assertEquals(Role.CREDIT, a.roles[8])
        assertEquals(SignMode.DEBIT_CREDIT, r.signMode)
        assertEquals(-8715L, r.rows[0].amount)
        assertEquals(540000L, r.rows[1].amount)
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), emptyList())
        assertEquals("Kaufland", plan.first { !it.isIncome }.merchant)
        assertEquals(7L, plan.first { it.isIncome }.categoryId)
    }

    @Test fun revolutSkipsPendingTopupsAndAddsFees() {
        val csv = """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            CARD_PAYMENT,Current,2026-11-03 14:22:11,2026-11-04 09:00:00,Uber,-23.40,0.00,PLN,COMPLETED,476.60
            TOPUP,Current,2026-11-02 10:00:00,2026-11-02 10:00:01,Payment from Jan Kowalski,500.00,0.00,PLN,COMPLETED,500.00
            CARD_PAYMENT,Current,2026-11-05 19:00:00,,Pizzeria Roma,-60.00,0.00,PLN,PENDING,
            ATM,Current,2026-11-06 12:00:00,2026-11-06 12:00:00,Cash at Euronet,-200.00,4.00,PLN,COMPLETED,272.60
        """.trimIndent()
        val (a, r) = load(csv)
        assertEquals("Revolut", a.bank)
        assertEquals(Role.TYPE, a.roles[0]); assertEquals(Role.DATE, a.roles[2]); assertEquals(Role.AMOUNT, a.roles[5])
        assertEquals(Role.FEE, a.roles[6]); assertEquals(Role.STATUS, a.roles[8]); assertEquals(Role.BALANCE, a.roles[9])
        val byDate = r.rows.associateBy { it.date }
        assertEquals(SkipReason.TRANSFER, byDate[day(2026, 11, 2)]!!.reason)
        assertEquals(SkipReason.PENDING, byDate[day(2026, 11, 5)]!!.reason)
        assertEquals(-20400L, byDate[day(2026, 11, 6)]!!.amount)
        assertNull(byDate[day(2026, 11, 3)]!!.reason)
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), emptyList()).associateBy { it.date }
        assertEquals("Uber", plan[day(2026, 11, 3)]!!.merchant)
        assertEquals(3L, plan[day(2026, 11, 3)]!!.categoryId)
    }

    @Test fun headerlessSantanderStyleWithBalanceSign() {
        // Bez nagłówka, kwoty bez znaku — znak wynika ze zmiany salda (wiersze od najnowszych).
        val csv = """
            "03-11-2026","03-11-2026","ROSSMANN 123 KRAKOW","PL61109000000000000012345678","45,00","955,00","3"
            "02-11-2026","02-11-2026","WPŁATA WŁASNA OD ANNY","PL61109000000000000012345678","200,00","1000,00","2"
            "01-11-2026","01-11-2026","APTEKA POD LWEM","PL61109000000000000012345678","30,00","800,00","1"
        """.trimIndent()
        val (a, r) = load(csv)
        assertEquals(Role.DATE, a.roles[0]); assertEquals(Role.DESCRIPTION, a.roles[2]); assertEquals(Role.IGNORE, a.roles[3])
        assertEquals(Role.AMOUNT, a.roles[4]); assertEquals(Role.BALANCE, a.roles[5])
        assertEquals(SignMode.BALANCE, r.signMode)
        val byDate = r.rows.associateBy { it.date }
        assertEquals(-4500L, byDate[day(2026, 11, 3)]!!.amount)
        assertEquals(20000L, byDate[day(2026, 11, 2)]!!.amount)
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), emptyList()).associateBy { it.date }
        assertEquals("Rossmann", plan[day(2026, 11, 3)]!!.merchant)
        assertEquals(9L, plan[day(2026, 11, 3)]!!.categoryId)
    }

    // ——— Tabele przygotowane samodzielnie ———

    @Test fun userTableUniversalLayout() {
        val csv = "Data;Kwota;Opis\n3.11.2026;-45,20 zł;Biedronka\n5.11.2026;5400;Wypłata\n7.11.2026;-1 299,99 zł;Nowy telefon\n"
        val (a, r) = load(csv)
        assertNull(a.bank)
        assertEquals(listOf(Role.DATE, Role.AMOUNT, Role.DESCRIPTION), a.roles)
        assertEquals(3, r.rows.size)
        assertEquals(-129999L, r.rows[2].amount)
        assertEquals(540000L, r.rows[1].amount)
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), emptyList()).associateBy { it.date }
        assertEquals("Nowy Telefon".lowercase(), plan[day(2026, 11, 7)]!!.merchant.lowercase())
        assertEquals(7L, plan[day(2026, 11, 5)]!!.categoryId)
    }

    @Test fun userTableShuffledColumnsAndEnglishAmounts() {
        val csv = "Description,Category,Amount,Date\nCoffee shop,Restauracje,-12.50,11/23/2026\nRent,Rachunki,\"-2,100.00\",11/01/2026\n"
        val (a, r) = load(csv)
        assertEquals(listOf(Role.DESCRIPTION, Role.CATEGORY, Role.AMOUNT, Role.DATE), a.roles)
        assertEquals(DateOrder.MDY, a.dateOrder)
        assertEquals(-210000L, r.rows[1].amount)
        assertEquals(day(2026, 11, 23), r.rows[0].date)
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), emptyList())
        assertEquals(4L, plan.first { it.amount == 1250L }.categoryId)
        assertEquals(2L, plan.first { it.amount == 210000L }.categoryId)
    }

    @Test fun userTableSeparateExpenseIncomeColumnsWithoutHeaderWords() {
        val csv = "Kiedy;Na co;Wydatek;Wpływ\n2026-11-01;Kino;35,00;\n2026-11-02;Premia;;800,00\n"
        val (a, r) = load(csv)
        assertEquals(Role.DATE, a.roles[0]); assertEquals(Role.DESCRIPTION, a.roles[1])
        assertEquals(Role.DEBIT, a.roles[2]); assertEquals(Role.CREDIT, a.roles[3])
        assertEquals(-3500L, r.rows[0].amount); assertEquals(80000L, r.rows[1].amount)
    }

    @Test fun userTableTypeColumnDecidesSign() {
        val csv = "data\ttyp\tkwota\topis\n2026-11-01\twydatek\t20,00\tObiad\n2026-11-02\tprzychód\t100,00\tSprzedaż roweru\n"
        val (a, r) = load(csv)
        assertEquals(SignMode.TYPE, r.signMode)
        assertEquals(-2000L, r.rows[0].amount); assertEquals(10000L, r.rows[1].amount)
        assertEquals('\t', a.table.delimiter)
    }

    @Test fun ownExportRoundTrip() {
        val csv = "﻿data;typ;kwota;sprzedawca;kategoria;notatka;źródło\n" +
            "\"2026-11-03\";\"wydatek\";\"45,20\";\"Moja piekarnia\";\"Zakupy spożywcze\";\"\";\"manual\"\n" +
            "\"2026-11-01\";\"przychód\";\"5400,00\";\"ACME\";\"Wynagrodzenie\";\"listopad\";\"manual\"\n"
        val (a, r) = load(csv)
        assertEquals("OpenPayments", a.bank)
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), emptyList())
        val bread = plan.first { !it.isIncome }
        assertEquals("Moja piekarnia", bread.merchant)
        assertEquals(1L, bread.categoryId)
        assertEquals(7L, plan.first { it.isIncome }.categoryId)
    }

    @Test fun windows1250AndExcelSepLine() {
        val csv = "sep=;\r\nData;Kwota;Opis;Kontrahent\r\n2026-11-03;-10,00;Opłata za kartę;Bank Śląski\r\n"
        val (a, r) = load(csv, charset("windows-1250"))
        assertEquals("Windows-1250", a.table.charset)
        assertEquals(1, r.rows.size)
        assertEquals("Opłata za kartę", r.rows[0].descriptions.single())
    }

    // ——— Duplikaty i nauka ———

    @Test fun detectsReceiptAlreadyInAppAndReimport() {
        val csv = "Data;Kwota;Opis\n2026-11-04;-32,46;LIDL SP Z O.O. JANKOWICE\n2026-11-04;-15,00;KIOSK\n2026-11-05;-9,99;Spotify\n"
        val (_, r) = load(csv)
        val existing = listOf(
            Transaction(id = 10, amount = 3246, merchant = "Lidl", categoryId = 1, date = day(2026, 11, 3), source = TxSource.RECEIPT),
            Transaction(id = 11, amount = 999, merchant = "Spotify", categoryId = 5, date = day(2026, 11, 5), source = TxSource.IMPORT),
            Transaction(id = 12, amount = 1500, merchant = "Zupełnie coś innego", categoryId = 6, date = day(2026, 11, 8), source = TxSource.MANUAL),
        )
        val plan = ImportPlanner.plan(r.rows, cats, emptyList(), existing).associateBy { it.amount }
        assertEquals(10L, plan[3246L]!!.duplicate?.id)
        assertEquals(SkipReason.DUPLICATE, plan[3246L]!!.reason)
        assertEquals(11L, plan[999L]!!.duplicate?.id)
        assertNull(plan[1500L]!!.duplicate)
    }

    @Test fun learnedRulesWinOverDictionary() {
        val csv = "Data;Kwota;Opis\n2026-11-04;-32,46;LIDL KRAKOW\n"
        val (_, r) = load(csv)
        val rules = listOf(pl.openpayments.data.CategoryRule(1, "lidl", 6))
        assertEquals(6L, ImportPlanner.plan(r.rows, cats, rules, emptyList()).single().categoryId)
    }

    @Test fun rememberedMappingOverridesDetection() {
        val t = BankStatement.table("Data;Kwota;Opis\n2026-11-04;-32,46;LIDL\n".toByteArray())
        val a = BankStatement.analyze(t, listOf(Role.DATE, Role.AMOUNT, Role.MERCHANT))
        assertEquals(Role.MERCHANT, a.roles[2])
        assertEquals("h:data|kwota|opis", a.signature)
    }

    @Test fun invertOptionFlipsSigns() {
        val a = BankStatement.analyze(BankStatement.table("Data;Kwota;Opis\n2026-11-04;32,46;Zakupy\n2026-11-05;-10,00;Zwrot\n".toByteArray()))
        val r = BankStatement.read(a, ReadOptions(invert = true))
        assertEquals(-3246L, r.rows[0].amount)
    }

    // ——— Wartości ———

    @Test fun parsesAmountFormats() {
        assertEquals(-123456L, Amounts.parse("-1 234,56 PLN"))
        assertEquals(123456L, Amounts.parse("1.234,56"))
        assertEquals(123456L, Amounts.parse("1,234.56", '.'))
        assertEquals(-1200L, Amounts.parse("(12.00)", '.'))
        assertEquals(-1200L, Amounts.parse("12,00-"))
        assertEquals(500000L, Amounts.parse("5 000"))
        assertEquals(1250L, Amounts.parse("+12,5 zł"))
        assertEquals(-4520L, Amounts.parse("−45,20"))
        assertNull(Amounts.parse("Biedronka"))
        assertNull(Amounts.parse("2026-11-03"))
    }

    @Test fun parsesDateFormats() {
        val d = LocalDate.of(2026, 11, 3)
        assertEquals(d, Dates.parse("2026-11-03"))
        assertEquals(d, Dates.parse("03.11.2026"))
        assertEquals(d, Dates.parse("3/11/2026"))
        assertEquals(d, Dates.parse("11/03/2026", DateOrder.MDY))
        assertEquals(d, Dates.parse("20261103"))
        assertEquals(d, Dates.parse("2026-11-03 14:22:11"))
        assertEquals(d, Dates.parse("3 lis 2026"))
        assertEquals(d, Dates.parse("03.11.26"))
        assertNull(Dates.parse("Kwota"))
    }

    @Test fun cleansMerchantNames() {
        assertEquals("Biedronka", MerchantName.extract("", listOf("ZAKUP PRZY UŻYCIU KARTY 4246xx1234 BIEDRONKA 4321 /KRAKOW"), ""))
        assertEquals("McDonald's", MerchantName.extract("", listOf("MCDONALDS 0123 WARSZAWA PL"), ""))
        assertEquals("Kwiaciarnia Róża", MerchantName.extract("KWIACIARNIA RÓŻA S.C. UL. KWIATOWA 1 00-950 WARSZAWA", emptyList(), ""))
        assertEquals("Wypłata z bankomatu", MerchantName.extract("", listOf("4246xx1234 12.11.2026"), "Wypłata z bankomatu"))
    }
}
