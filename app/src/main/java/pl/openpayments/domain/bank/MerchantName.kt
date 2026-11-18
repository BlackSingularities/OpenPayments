package pl.openpayments.domain.bank

import pl.openpayments.domain.PL

/**
 * Wyciąga czytelną nazwę sprzedawcy z opisu operacji bankowej:
 * „ZAKUP PRZY UŻYCIU KARTY 4246xx1234 BIEDRONKA 1234 /KRAKOW” → „Biedronka”,
 * „Lokalizacja: Adres: SKLEP U ANI 12 Miasto: GDANSK” → „Sklep u Ani”.
 */
object MerchantName {

    /** Sieci i usługi: fragment (bez polskich znaków) → nazwa wyświetlana. „*” = dopasowanie początku słowa. */
    private val brands: List<Pair<String, String>> = listOf(
        "biedronka" to "Biedronka", "lidl" to "Lidl", "zabka" to "Żabka", "kaufland" to "Kaufland", "auchan" to "Auchan",
        "carrefour" to "Carrefour", "netto" to "Netto", "dino" to "Dino", "stokrotka" to "Stokrotka", "aldi" to "Aldi",
        "lewiatan" to "Lewiatan", "polomarket" to "POLOmarket", "intermarche" to "Intermarché", "spar" to "Spar",
        "freshmarket" to "Freshmarket", "delikatesy centrum" to "Delikatesy Centrum", "chata polska" to "Chata Polska",
        "topaz" to "Topaz", "mila" to "Mila", "groszek" to "Groszek", "frisco" to "Frisco", "rossmann" to "Rossmann",
        "hebe" to "Hebe", "super pharm" to "Super-Pharm", "superpharm" to "Super-Pharm", "dm drogerie" to "dm",
        "drogerie natura" to "Natura", "apteka gemini" to "Apteka Gemini", "doz" to "DOZ", "ziko" to "Ziko",
        "orlen" to "Orlen", "shell" to "Shell", "circle k" to "Circle K", "bp" to "BP", "moya" to "Moya", "lotos" to "Lotos",
        "amic" to "Amic", "uber" to "Uber", "bolt" to "Bolt", "free now" to "FREE NOW", "freenow" to "FREE NOW",
        "pkp intercity" to "PKP Intercity", "intercity" to "PKP Intercity", "koleo" to "Koleo", "jakdojade" to "Jakdojade",
        "skycash" to "SkyCash", "mpk" to "MPK", "ztm" to "ZTM", "flixbus" to "FlixBus", "ryanair" to "Ryanair",
        "wizz" to "Wizz Air", "lot polish" to "LOT", "booking com" to "Booking.com", "airbnb" to "Airbnb",
        "netflix" to "Netflix", "spotify" to "Spotify", "hbo" to "Max", "disney" to "Disney+", "youtube" to "YouTube",
        "icloud" to "iCloud", "apple com" to "Apple", "apple" to "Apple", "google one" to "Google One", "google play" to "Google Play",
        "google" to "Google", "microsoft" to "Microsoft", "xbox" to "Xbox", "playstation" to "PlayStation", "steam" to "Steam",
        "canal+" to "Canal+", "canal plus" to "Canal+", "player pl" to "Player", "chatgpt" to "ChatGPT", "openai" to "OpenAI",
        "anthropic" to "Anthropic", "audible" to "Audible", "storytel" to "Storytel", "legimi" to "Legimi", "empik" to "Empik",
        "multikino" to "Multikino", "cinema city" to "Cinema City", "helios" to "Helios",
        "mcdonald*" to "McDonald's", "kfc" to "KFC", "burger king" to "Burger King", "subway" to "Subway",
        "pizza hut" to "Pizza Hut", "domino*" to "Domino's", "starbucks" to "Starbucks", "costa coffee" to "Costa Coffee",
        "north fish" to "North Fish", "pyszne" to "Pyszne.pl", "glovo" to "Glovo", "wolt" to "Wolt",
        "allegro" to "Allegro", "amazon" to "Amazon", "aliexpress" to "AliExpress", "temu" to "Temu", "shein" to "Shein",
        "olx" to "OLX", "vinted" to "Vinted", "zalando" to "Zalando", "answear" to "Answear", "modivo" to "Modivo",
        "eobuwie" to "eobuwie.pl", "media markt" to "MediaMarkt", "mediamarkt" to "MediaMarkt", "media expert" to "Media Expert",
        "rtv euro agd" to "RTV Euro AGD", "euro rtv" to "RTV Euro AGD", "x kom" to "x-kom", "morele" to "Morele",
        "ikea" to "IKEA", "castorama" to "Castorama", "leroy merlin" to "Leroy Merlin", "obi" to "OBI", "jysk" to "Jysk",
        "pepco" to "Pepco", "action" to "Action", "tedi" to "TEDi", "kik" to "KiK", "half price" to "Half Price",
        "tk maxx" to "TK Maxx", "zara" to "Zara", "reserved" to "Reserved", "sinsay" to "Sinsay", "cropp" to "Cropp",
        "house" to "House", "mohito" to "Mohito", "h&m" to "H&M", "h m" to "H&M", "ccc" to "CCC", "deichmann" to "Deichmann",
        "decathlon" to "Decathlon", "smyk" to "Smyk", "inpost" to "InPost", "poczta polska" to "Poczta Polska",
        "orange" to "Orange", "t mobile" to "T-Mobile", "plus gsm" to "Plus", "polkomtel" to "Plus", "p4 sp" to "Play",
        "upc" to "UPC", "vectra" to "Vectra", "netia" to "Netia", "inea" to "Inea", "pge" to "PGE", "tauron" to "Tauron",
        "enea" to "Enea", "energa" to "Energa", "pgnig" to "PGNiG", "e on" to "E.ON", "veolia" to "Veolia",
        "medicover" to "Medicover", "luxmed" to "LUX MED", "lux med" to "LUX MED", "enel med" to "Enel-Med",
        "zooplus" to "Zooplus", "maxi zoo" to "Maxi Zoo", "kakadu" to "Kakadu",
    ).map { (k, v) -> fold(k).replace(Regex("[^a-z0-9&+*]+"), " ") to v }

    fun brandOf(text: String): String? {
        val p = words(text)
        for ((k, v) in brands) {
            val hit = if (k.endsWith("*")) p.contains(" " + k.dropLast(1)) else p.contains(" $k ")
            if (hit) return v
        }
        return null
    }

    /** Klucze opisów w formacie „Klucz: wartość” (np. PKO BP). */
    private val kvKeys = listOf(
        "nazwa odbiorcy", "nazwa nadawcy", "adres odbiorcy", "adres nadawcy", "rachunek odbiorcy", "rachunek nadawcy",
        "data i czas operacji", "data wykonania", "data transakcji", "oryginalna kwota operacji", "kwota oryginalna",
        "numer karty", "numer telefonu", "numer referencyjny", "referencje wlasne zleceniodawcy", "lokalizacja",
        "tytul", "adres", "miasto", "kraj", "operacja", "odbiorca", "nadawca", "kontrahent", "ref",
    )
    private val kvRegex = Regex("(?<![a-z])(" + kvKeys.joinToString("|") { Regex.escape(it) } + ")\\s*:")

    /** „Tytuł: X Lokalizacja: Adres: Y Miasto: Z” → {tytul=X, adres=Y, miasto=Z} i tekst przed pierwszym kluczem. */
    fun keyValues(text: String): Pair<String, Map<String, String>> {
        val f = fold(text)
        val ms = kvRegex.findAll(f).toList()
        if (ms.isEmpty() || f.length != text.length) return text to emptyMap()
        val map = LinkedHashMap<String, String>()
        ms.forEachIndexed { i, m ->
            val end = if (i + 1 < ms.size) ms[i + 1].range.first else text.length
            val v = text.substring(m.range.last + 1, end).trim().trim(',', ';', '|').trim()
            val key = m.groupValues[1]
            if (v.isNotEmpty() && key !in map) map[key] = v
        }
        return text.substring(0, ms.first().range.first).trim() to map
    }

    /** Początki opisów, które nic nie mówią o sprzedawcy. */
    private val prefixes = listOf(
        "zakup przy uzyciu karty w kraju", "zakup przy uzyciu karty za granica", "zakup przy uzyciu karty",
        "platnosc karta debetowa", "platnosc karta kredytowa", "platnosc karta", "platnosc karty", "transakcja karta debetowa",
        "transakcja karta kredytowa", "transakcja karta", "transakcja bezgotowkowa", "operacja karta", "zakup karta",
        "platnosc web blik", "platnosc blik", "zakup blik", "przelew na telefon blik", "przelew blik", "przelew na telefon",
        "platnosc mobilna", "platnosc apple pay", "platnosc google pay", "apple pay", "google pay", "blik",
        "przelew wychodzacy", "przelew przychodzacy", "przelew zewnetrzny", "przelew wewnetrzny", "przelew sepa",
        "przelew krajowy", "przelew natychmiastowy", "przelew express elixir", "przelew elixir", "przelew na rachunek",
        "przelew z rachunku", "przelew do", "przelew od", "przelew dla", "przelew", "polecenie zaplaty", "zlecenie stale",
        "card payment to", "card payment", "payment to", "payment from", "transfer to", "transfer from", "money added from",
        "pos", "obciazenie", "uznanie", "wplata", "transakcja", "zakup", "zwrot", "na rzecz", "dla", "od", "do",
        "nazwa odbiorcy", "nazwa nadawcy", "odbiorca", "nadawca", "tytul", "adres", "kontrahent",
    ).map(::fold)

    private val generic = setOf(
        "", "przelew", "platnosc", "zakup", "transakcja", "operacja", "oplata", "karta", "blik", "pln", "polska", "poland",
        "uznanie", "obciazenie", "wplata", "wyplata", "brak", "brak tytulu", "brak opisu", "n a", "na", "-", "zwrot",
    )

    private val noise = listOf(
        Regex("(?i)\\bnr\\s+karty\\s*:?\\s*\\S+"),
        Regex("(?i)\\bkarta\\s*:?\\s*\\d[\\dx*]{5,}"),
        Regex("(?i)\\b\\d{4,6}[x*]{2,}[\\dx*]{0,8}\\b"),
        Regex("(?i)\\b[x*]{4,}\\d{2,4}\\b"),
        Regex("\\b\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}\\b"),
        Regex("\\b\\d{1,2}[-./]\\d{1,2}[-./]\\d{2,4}\\b"),
        Regex("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b"),
        Regex("(?i)[-+]?\\d[\\d ]*[.,]\\d{2}\\s*(PLN|EUR|USD|GBP|CHF|zł)\\b"),
        Regex("(?i)\\b(PLN|EUR|USD|GBP|CHF)\\s*[-+]?\\d[\\d ]*[.,]\\d{2}"),
        Regex("(?i)\\bkurs\\s*:?\\s*[\\d.,]+"),
        Regex("(?i)\\bref(erencja)?\\s*[:.]\\s*\\S+"),
        Regex("\\b[A-Z]{2}\\d{2}[\\d ]{12,}\\b"),
        Regex("\\b\\d{5,}\\b"),
    )

    private val legal = Regex(
        "(?i)\\b(sp\\.?\\s*z\\.?\\s*o\\.?\\s*o\\.?|sp\\.?\\s*z\\s*oo|s\\.?\\s*a\\.?(?=\\s|$)|sp\\.?\\s*k\\.?(?=\\s|$)|sp\\.?\\s*j\\.?(?=\\s|$)|" +
            "sp[oó][lł]ka\\b.*|s\\.?c\\.?(?=\\s|$)|ltd\\.?|gmbh|inc\\.?|llc|limited|b\\.?v\\.?(?=\\s|$)|s\\.?r\\.?o\\.?)",
    )
    private val cutAt = listOf(
        Regex("\\s{2,}"), Regex("\\s/"), Regex("\\s\\d{2}-\\d{3}\\b"), Regex("(?i)\\s(ul|al|os|pl|ulica)\\.\\s?"),
        Regex("(?i)\\sul\\s"), Regex("\\s\\|"), Regex("(?i)\\s(miasto|kraj|adres)\\s*:"),
    )
    private val countryTail = Regex("(?i)[\\s,]+(PL|POL|POLSKA|POLAND|PRT|DEU|DE|GB|GBR|IE|IRL|NL|NLD|LU|LUX|US|USA|CZ|SK)\\s*$")

    private fun stripPrefixes(s: String): String {
        var t = s.trim()
        var changed = true
        while (changed && t.isNotEmpty()) {
            changed = false
            val f = fold(t)
            for (p in prefixes) {
                if (f.startsWith(p) && (f.length == p.length || !f[p.length].isLetterOrDigit())) {
                    t = t.substring(p.length).trimStart(' ', ':', '-', '–', ',', '.', '/', '*')
                    changed = true
                    break
                }
            }
        }
        return t
    }

    /** Porządkuje jeden fragment opisu do postaci nazwy; pusty, gdy nic sensownego nie zostało. */
    fun simplify(raw: String): String {
        var t = raw.replace(' ', ' ').replace('\t', ' ').trim()
        if (t.isEmpty()) return ""
        t = stripPrefixes(t)
        for (r in noise) t = r.replace(t, " ")
        t = stripPrefixes(t.trim())
        // Ucinamy adres, miasto i numer sklepu.
        for (r in cutAt) r.find(t)?.let { m -> if (m.range.first >= 2) t = t.substring(0, m.range.first) }
        t = legal.replace(t, " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        // Numery sklepów („BIEDRONKA 1234”, „ZABKA Z1234 K.1”) — po pierwszym słowie.
        val parts = t.split(' ')
        if (parts.size > 1) {
            val kept = listOf(parts[0]) + parts.drop(1).filterNot { w ->
                w.matches(Regex("(?i)[a-z]?\\d{2,}[a-z]?")) || w.matches(Regex("(?i)k\\.?\\d+")) || w.matches(Regex("\\d+"))
            }
            t = kept.joinToString(" ")
        }
        repeat(2) { t = countryTail.replace(t, "") }
        t = t.trim().trim(',', '.', ';', ':', '-', '–', '/', '\\', '*', '"', '\'', '#', '(', ')').trim()
        if (fold(t).replace(Regex("[^a-z]"), "").length < 2) return ""
        if (fold(t).trim() in generic) return ""
        return prettify(limit(t, 40))
    }

    private fun limit(s: String, max: Int): String {
        if (s.length <= max) return s
        val cut = s.substring(0, max)
        val sp = cut.lastIndexOf(' ')
        return if (sp > max / 2) cut.substring(0, sp) else cut
    }

    private val keepUpper = setOf("PKP", "KFC", "PGE", "ZTM", "MPK", "BP", "CCC", "DPD", "UPS", "OBI", "LOT", "IKEA", "PZU",
        "ZUS", "US", "UM", "PKO", "BNP", "ING", "DHL", "GLS", "MZK", "KZK", "SKM", "WKD", "PKS", "TVP", "AGD", "RTV", "DOZ", "OLX", "BLIK")

    /** „SKLEP U ANI” → „Sklep u Ani”; tekst pisany normalnie zostaje bez zmian. */
    fun prettify(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty() || letters.count { it.isLowerCase() } > letters.length / 5) return s
        val small = setOf("u", "w", "z", "i", "na", "do", "od", "pod", "przy", "nad", "and", "of", "the", "von", "de")
        return s.split(' ').mapIndexed { i, w ->
            val up = w.uppercase(PL)
            when {
                up in keepUpper || up.replace(Regex("[^A-Z&]"), "") in keepUpper && w.length <= 4 -> up
                w.any { it.isDigit() } && w.length <= 4 -> w
                i > 0 && w.lowercase(PL) in small -> w.lowercase(PL)
                w.contains('&') -> up
                else -> w.lowercase(PL).split('-').joinToString("-") { p -> p.replaceFirstChar { it.titlecase(PL) } }
            }
        }.joinToString(" ")
    }

    /**
     * Nazwa sprzedawcy: znana sieć z dowolnego pola, potem kontrahent, pola „Nazwa odbiorcy/nadawcy”,
     * adres z lokalizacji karty, kolejne fragmenty opisu, tytuł, typ operacji.
     */
    fun extract(counterparty: String, descriptions: List<String>, type: String): String {
        val kvs = descriptions.map { keyValues(it) }
        val cp = keyValues(counterparty)
        val maps = listOf(cp.second) + kvs.map { it.second }
        fun field(vararg keys: String) = keys.flatMap { k -> maps.mapNotNull { it[k] } }
        val allText = (listOf(counterparty) + descriptions).joinToString(" ")
        brandOf(allText)?.let { return it }
        val candidates = buildList {
            add(cp.first)
            addAll(field("nazwa odbiorcy", "nazwa nadawcy", "odbiorca", "nadawca", "kontrahent"))
            addAll(field("adres"))
            addAll(kvs.map { it.first })
            addAll(field("tytul"))
            add(type)
        }
        for (c in candidates) {
            val s = simplify(c)
            if (s.isNotEmpty()) return s
        }
        return when {
            words(allText + " " + type).let { it.contains(" bankomat") || it.contains(" atm ") } -> "Wypłata z bankomatu"
            type.isNotBlank() -> prettify(limit(type.trim(), 40))
            else -> "Operacja bankowa"
        }
    }

    private val noteDrop = setOf(
        "numer karty", "data i czas operacji", "data wykonania", "data transakcji", "oryginalna kwota operacji", "kwota oryginalna",
        "numer referencyjny", "referencje wlasne zleceniodawcy", "rachunek odbiorcy", "rachunek nadawcy", "kraj", "lokalizacja", "ref",
    )

    /** Notatka: oryginalny opis bez numerów kart, referencji i powtórzonej nazwy. */
    fun note(merchant: String, descriptions: List<String>, type: String): String {
        val parts = LinkedHashSet<String>()
        for (d in descriptions) {
            val (head, kv) = keyValues(d)
            if (head.isNotBlank()) parts += head
            kv.forEach { (k, v) -> if (k !in noteDrop && !v.all { it.isDigit() || it == ' ' }) parts += v }
        }
        if (parts.isEmpty() && type.isNotBlank()) parts += type
        val text = parts.map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() && !it.equals(merchant, true) && !it.all { c -> c.isDigit() || c == ' ' } }
            .joinToString(" · ")
        return limit(text, 160)
    }
}
