package pl.openpayments.data

object Defaults {
    val categories = listOf(
        Category(name = "Zakupy spożywcze", icon = "cart", color = 0xFF2E8B6E),
        Category(name = "Rachunki", icon = "bolt", color = 0xFF3B6FB6),
        Category(name = "Transport", icon = "car", color = 0xFFE08A2E),
        Category(name = "Rozrywka", icon = "movie", color = 0xFF9B59B6),
        Category(name = "Restauracje", icon = "food", color = 0xFFD9534F),
        Category(name = "Zdrowie", icon = "health", color = 0xFF16A2B8),
        Category(name = "Dom", icon = "home", color = 0xFF8D6E63),
        Category(name = "Ubrania", icon = "shirt", color = 0xFFC2185B),
        Category(name = "Subskrypcje", icon = "repeat", color = 0xFF5C6BC0),
        Category(name = "Inne", icon = "dots", color = 0xFF78909C),
        Category(name = "Wynagrodzenie", icon = "wallet", color = 0xFF43A047, isIncome = true),
        Category(name = "Inne przychody", icon = "plus", color = 0xFF7CB342, isIncome = true),
    )

    /** Wbudowany słownik: fragment nazwy sprzedawcy → nazwa kategorii. */
    val keywords: List<Pair<String, String>> = listOf(
        "lidl" to "Zakupy spożywcze", "biedronka" to "Zakupy spożywcze", "kaufland" to "Zakupy spożywcze",
        "żabka" to "Zakupy spożywcze", "zabka" to "Zakupy spożywcze", "auchan" to "Zakupy spożywcze",
        "carrefour" to "Zakupy spożywcze", "dino" to "Zakupy spożywcze", "netto" to "Zakupy spożywcze",
        "stokrotka" to "Zakupy spożywcze", "lewiatan" to "Zakupy spożywcze", "aldi" to "Zakupy spożywcze",
        "jeronimo" to "Zakupy spożywcze", "polomarket" to "Zakupy spożywcze", "intermarche" to "Zakupy spożywcze",
        "orlen" to "Transport", "shell" to "Transport", "circle k" to "Transport", "moya" to "Transport",
        "uber" to "Transport", "bolt" to "Transport", "pkp" to "Transport", "intercity" to "Transport",
        "jakdojade" to "Transport", "mpk" to "Transport", "ztm" to "Transport", "paliwo" to "Transport",
        "netflix" to "Subskrypcje", "spotify" to "Subskrypcje", "hbo" to "Subskrypcje",
        "disney" to "Subskrypcje", "youtube" to "Subskrypcje", "icloud" to "Subskrypcje", "google one" to "Subskrypcje",
        "kino" to "Rozrywka", "cinema" to "Rozrywka", "multikino" to "Rozrywka", "steam" to "Rozrywka",
        "mcdonald" to "Restauracje", "kfc" to "Restauracje", "pizza" to "Restauracje", "pyszne" to "Restauracje",
        "glovo" to "Restauracje", "wolt" to "Restauracje", "starbucks" to "Restauracje", "kebab" to "Restauracje",
        "apteka" to "Zdrowie", "rossmann" to "Zdrowie", "hebe" to "Zdrowie", "medicover" to "Zdrowie", "luxmed" to "Zdrowie",
        "ikea" to "Dom", "castorama" to "Dom", "leroy" to "Dom", "jysk" to "Dom", "pepco" to "Dom", "action" to "Dom",
        "pge" to "Rachunki", "tauron" to "Rachunki", "enea" to "Rachunki", "energa" to "Rachunki", "pgnig" to "Rachunki",
        "orange" to "Rachunki", "t-mobile" to "Rachunki", "upc" to "Rachunki", "czynsz" to "Rachunki",
        "prąd" to "Rachunki", "internet" to "Rachunki",
        "zara" to "Ubrania", "reserved" to "Ubrania", "h&m" to "Ubrania", "sinsay" to "Ubrania", "cropp" to "Ubrania",
        "ccc" to "Ubrania", "decathlon" to "Ubrania", "zalando" to "Ubrania",
        "wypłata" to "Wynagrodzenie", "pensja" to "Wynagrodzenie", "wynagrodzenie" to "Wynagrodzenie",
    )
}
