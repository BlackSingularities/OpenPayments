package pl.openpayments.domain

import java.text.Normalizer

/** Grupa produktu z paragonu — do statystyk „co jest w koszyku”. */
enum class ProductGroup(val label: String, val icon: String, val color: Long) {
    DAIRY("Nabiał", "egg", 0xFF5C9DD6),
    BREAD("Pieczywo", "bakery", 0xFFC8963E),
    MEAT("Mięso i wędliny", "meat", 0xFFC0504D),
    FISH("Ryby", "fish", 0xFF3E8EA8),
    PRODUCE("Warzywa i owoce", "produce", 0xFF4E9A4E),
    DRY("Produkty suche", "grain", 0xFFB08D57),
    FROZEN("Mrożonki", "frozen", 0xFF6FA8C8),
    DRINKS("Napoje", "drink", 0xFF3B7DD8),
    ALCOHOL("Alkohol", "wine", 0xFF8E3B6E),
    SWEETS("Słodycze i przekąski", "candy", 0xFFD9729B),
    HOUSEHOLD("Chemia i higiena", "soap", 0xFF7E6BC4),
    BABY("Dla dzieci", "baby", 0xFFE0A030),
    PETS("Dla zwierząt", "pets", 0xFF8D6E63),
    PACKAGING("Torby i kaucje", "bag", 0xFF8A8F8C),
    OTHER("Inne", "dots", 0xFF78909C),
}

/**
 * Słownik do rozwijania skróconych nazw z paragonów, np.
 * „SER ZOLT.GOUDA PLAS.150G” → „Ser żółty gouda plastry 150 g” (grupa: Nabiał).
 *
 * Działa w trzech krokach:
 * 1. wyuczone poprawki użytkownika (pełna nazwa z paragonu → jego nazwa),
 * 2. każdy wyraz: skrót spółgłoskowy z tabeli, pełne słowo bez ogonków albo
 *    prefiks słowa ze słownika („KIELB” → „kiełbasa”, „SMIET” → „śmietana”),
 * 3. grupa produktu z pierwszego rzeczownika, który ją ma.
 */
object ProductDictionary {

    data class Resolved(val name: String, val group: ProductGroup)

    private class Word(val text: String, val group: ProductGroup?) { val key = strip(text) }

    private fun w(group: ProductGroup?, vararg words: String) = words.map { Word(it, group) }

    /** Kolejność ma znaczenie: przy dopasowaniu po prefiksie wygrywa pierwsze słowo. */
    private val vocabulary: List<Word> = buildList {
        // Nabiał
        addAll(w(ProductGroup.DAIRY, "mleko", "jogurt", "twaróg", "twarożek", "ser", "serek", "śmietana", "śmietanka",
            "kefir", "maślanka", "masło", "margaryna", "jaja", "jajka", "mozzarella", "gouda", "edamer", "camembert",
            "feta", "parmezan", "ricotta", "mascarpone", "skyr", "budyń", "deser", "zsiadłe", "homogenizowany"))
        // Pieczywo
        addAll(w(ProductGroup.BREAD, "bułka", "bułki", "kajzerka", "chleb", "bagietka", "rogal", "rogalik", "grahamka",
            "ciabatta", "tortilla", "bułeczki", "bułeczka", "pączek", "drożdżówka", "chałka", "pieczywo", "tost", "tostowy", "croissant"))
        // Mięso i wędliny
        addAll(w(ProductGroup.MEAT, "kiełbasa", "kiełbaski", "szynka", "parówki", "boczek", "polędwica", "salami",
            "pierś", "filet", "kurczak", "kurczaka", "indyk", "indyka", "schab", "karkówka", "łopatka", "wołowina",
            "wieprzowina", "mięso", "mielone", "kabanosy", "pasztet", "pasztetowa", "kaszanka", "salceson", "udko",
            "skrzydełka", "żeberka", "wędlina", "baleron", "mortadela", "frankfurterki", "golonka", "wątróbka", "cheeseburger", "hamburger"))
        // Ryby
        addAll(w(ProductGroup.FISH, "łosoś", "tuńczyk", "śledź", "śledzie", "makrela", "dorsz", "mintaj", "pstrąg",
            "krewetki", "paluszki rybne", "sardynki", "szprot", "ryba"))
        // Warzywa i owoce
        addAll(w(ProductGroup.PRODUCE, "pomidory", "pomidor", "pomarańcze", "pomarańcza", "ziemniaki", "marchew",
            "marchewka", "cebula", "ogórek", "ogórki", "papryka", "sałata", "kapusta", "brokuł", "kalafior", "cukinia",
            "bakłażan", "czosnek", "pietruszka", "seler", "por", "szczypiorek", "koperek", "rzodkiewka", "burak",
            "buraki", "pieczarki", "jabłka", "jabłko", "banany", "banan", "cytryna", "cytryny", "limonka", "gruszki",
            "gruszka", "winogrona", "truskawki", "borówki", "maliny", "śliwki", "brzoskwinie", "nektarynki",
            "mandarynki", "kiwi", "ananas", "arbuz", "melon", "awokado", "mango", "grejpfrut", "szpinak", "rukola",
            "kukurydza", "fasolka", "groszek", "warzywa", "owoce", "imbir", "natka"))
        // Produkty suche
        addAll(w(ProductGroup.DRY, "makaron", "ryż", "mąka", "kasza", "płatki", "musli", "cukier", "sól", "olej",
            "oliwa", "ocet", "ketchup", "majonez", "musztarda", "kawa", "herbata", "kakao", "przyprawa", "pieprz",
            "sos", "koncentrat", "konserwa", "fasola", "ciecierzyca", "soczewica", "dżem", "miód", "kisiel", "galaretka",
            "zupa", "bulion", "orzechy", "rodzynki", "gnocchi", "kluski", "kopytka", "drożdże", "proszek do pieczenia", "chrzan", "pesto", "passata"))
        // Mrożonki
        addAll(w(ProductGroup.FROZEN, "mrożone", "mrożonka", "pierogi", "pizza", "frytki", "lody"))
        // Napoje
        addAll(w(ProductGroup.DRINKS, "woda", "sok", "nektar", "napój", "cola", "lemoniada", "oranżada", "kompot",
            "izotonik", "energetyk", "tonic", "syrop"))
        // Alkohol
        addAll(w(ProductGroup.ALCOHOL, "piwo", "wino", "wódka", "whisky", "gin", "rum", "likier", "cydr", "prosecco",
            "szampan", "nalewka"))
        // Słodycze i przekąski
        addAll(w(ProductGroup.SWEETS, "czekolada", "baton", "batonik", "ciastka", "ciasteczka", "wafle", "wafelek",
            "chipsy", "chrupki", "paluszki", "żelki", "cukierki", "lizak", "guma", "krakersy", "precle", "popcorn",
            "draże", "pianki", "prażynki", "nachosy", "orzeszki", "rogaliki", "biszkopty", "sernik", "ciasto", "tort", "pralinki"))
        // Chemia i higiena
        addAll(w(ProductGroup.HOUSEHOLD, "papier toaletowy", "ręczniki", "ręcznik", "płyn", "proszek", "kapsułki",
            "tabletki", "mydło", "szampon", "odżywka", "pasta", "szczoteczka", "dezodorant", "antyperspirant",
            "chusteczki", "worki", "gąbki", "zmywak", "domestos", "żel", "krem", "balsam", "podpaski", "tampony",
            "pieluchy", "folia", "pianka", "odplamiacz", "środek", "wkłady", "maszynki", "patyczki", "płatki kosmetyczne"))
        // Dzieci i zwierzęta
        addAll(w(ProductGroup.BABY, "chusteczki nawilżane", "kaszka", "przecier dla dzieci", "mleko modyfikowane"))
        addAll(w(ProductGroup.PETS, "karma", "żwirek", "przysmak dla psa", "przysmak dla kota"))
        // Opakowania
        addAll(w(ProductGroup.PACKAGING, "torba", "torebka", "reklamówka", "kaucja", "butelka zwrotna"))
        // Przymiotniki i dookreślenia (bez grupy)
        addAll(w(null, "żółty", "żółta", "biały", "biała", "czarny", "czarna", "zielony", "zielona", "czerwony",
            "czerwona", "naturalny", "naturalna", "owocowy", "owocowa", "truskawkowy", "waniliowy", "czekoladowy",
            "pszenny", "pszenna", "żytni", "żytnia", "razowy", "razowa", "pełnoziarnisty", "pełnoziarnista", "graham",
            "wieloziarnisty", "orkiszowy", "tłusty", "półtłusty", "chudy", "odtłuszczony", "świeży", "świeże",
            "wędzony", "wędzona", "gotowany", "gotowana", "pieczony", "pieczona", "suszony", "suszona", "mielony",
            "mielona", "ziarnista", "rozpuszczalna", "plastry", "plasterki", "kostka", "krojony", "krojona", "tarty",
            "gazowana", "niegazowana", "lekko gazowana", "mineralna", "źródlana", "bezglutenowy", "bez laktozy",
            "ekologiczny", "wiejski", "wiejskie", "domowy", "domowa", "klasyczny", "klasyczna", "extra", "premium",
            "kremowy", "delikatny", "pikantny", "słodki", "kwaśny", "wolny wybieg", "ściółkowe", "młode", "duży",
            "mały", "mrożony", "luz", "pomidorowy", "pomidorowa", "ogórkowa", "jabłkowy", "pomarańczowy",
            "wieloowocowy", "tuńczyk w oleju", "w sosie", "kurczaka", "wieprzowy", "wieprzowa", "wołowy", "wołowa",
            "drobiowy", "drobiowa", "ser żółty", "śląska", "krakowska", "podwawelska", "żywiecka", "toruńska",
            "zwyczajna", "myśliwska", "parzona", "podsuszana", "staropolska", "tradycyjna", "sopocka",
            "łaciate", "kozi", "owczy", "topiony", "pleśniowy", "rolada", "kanapkowy", "sałatkowy",
            "kiszone", "kiszona", "konserwowe", "marynowane", "obrane", "myte", "baby", "cherry", "koktajlowe", "śmietankowy", "kakaowy", "mleczna", "gorzka", "deserowa",
            "ziemniaczane", "solone", "paprykowe", "śniadaniowe", "śniadaniowa", "drobiowe", "wieprzowe", "wołowe", "zębów", "naczyń", "prania", "toaletowy", "kuchenne", "uniwersalny"))
    }

    /** Skróty, które nie są początkiem słowa (spółgłoskowe, zwyczajowe skróty kas). */
    private val abbreviations: Map<String, String> = mapOf(
        "MLK" to "mleko", "ML" to "mleko", "JGT" to "jogurt", "SMT" to "śmietana", "TW" to "twaróg",
        "PSZ" to "pszenny", "PSZEN" to "pszenny", "ZYT" to "żytni", "RAZ" to "razowy", "PELNOZ" to "pełnoziarnisty",
        "WIELOZ" to "wieloziarnisty", "GRAH" to "graham",
        "KURCZ" to "kurczak", "KURCZ." to "kurczak", "FIL" to "filet", "WP" to "wieprzowy", "WIEP" to "wieprzowy",
        "WOL" to "wołowy", "DROB" to "drobiowy", "WEDZ" to "wędzony", "GOT" to "gotowany", "PLAS" to "plastry",
        "PLAST" to "plastry", "KROJ" to "krojony", "NAT" to "naturalny", "ZOL" to "żółty", "ZOLT" to "żółty",
        "SW" to "świeży", "SWIEZ" to "świeży", "MROZ" to "mrożony", "NGAZ" to "niegazowana", "NIEGAZ" to "niegazowana",
        "GAZ" to "gazowana", "LGAZ" to "lekko gazowana", "MIN" to "mineralna", "ZROD" to "źródlana",
        "PAP" to "papier", "TOAL" to "toaletowy", "RECZ" to "ręczniki", "KUCH" to "kuchenne", "PL" to "płyn",
        "NACZ" to "naczyń", "PRAN" to "prania", "ZEB" to "zębów", "SZT" to "szt.", "OP" to "opak.",
        "CZEK" to "czekolada", "MLECZ" to "mleczna", "GORZ" to "gorzka", "TRUSK" to "truskawkowy",
        "WAN" to "waniliowy", "POMID" to "pomidory", "ZIEMN" to "ziemniaki", "ZIEM" to "ziemniaki",
        "JAB" to "jabłka", "JABL" to "jabłka", "BAN" to "banany", "CEB" to "cebula", "MARCH" to "marchew",
        "PIECZ" to "pieczarki", "OGOR" to "ogórek", "SAL" to "sałata", "MAS" to "masło", "MASL" to "masło",
        "EKO" to "ekologiczny", "BIO" to "BIO", "WB" to "wolny wybieg", "SCIOLK" to "ściółkowe",
        "SZYNK" to "szynka", "KIELB" to "kiełbasa", "PAR" to "parówki", "PAROW" to "parówki",
        "SLEDZ" to "śledź", "LOS" to "łosoś", "TUN" to "tuńczyk", "MAK" to "makaron", "MAKA" to "mąka",
        "KAJZ" to "kajzerka", "BUL" to "bułka", "BAGIET" to "bagietka", "CHL" to "chleb",
        "HOM" to "homogenizowany", "WYB" to "wybieg", "SNIAD" to "śniadaniowe", "BULEC" to "bułeczki",
        "CHEESB" to "cheeseburger", "GNO" to "gnocchi", "WIEPR" to "wieprzowe", "KABAN" to "kabanosy",
        "POLED" to "polędwica", "PARZ" to "parzony", "MIEL" to "mielone", "PRAZ" to "prażynki",
        "CHIP" to "chipsy", "SMIET" to "śmietana", "SMIETAN" to "śmietana", "TWAR" to "twaróg",
        "JOG" to "jogurt", "NAP" to "napój", "NEKT" to "nektar", "CZEKOL" to "czekolada",
    )

    private val unit = Regex("""^(\d+(?:[.,]\d+)?)(G|GR|KG|ML|L|LTR|SZT|%|X\d+)$""", RegexOption.IGNORE_CASE)

    /** Wielkie litery bez polskich znaków i interpunkcji — klucz do porównań. */
    fun strip(s: String): String {
        val noPl = s.replace('ł', 'l').replace('Ł', 'L')
        return Normalizer.normalize(noPl, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase(PL)
    }

    /** Klucz wyuczonej poprawki: cała nazwa z paragonu, bez znaków przestankowych. */
    fun key(raw: String): String = strip(raw).replace(Regex("[^A-Z0-9%]+"), " ").trim()

    private val byKey: Map<String, Word> by lazy { vocabulary.associateBy { it.key } }

    /** Marki i nazwy własne: zostają z wielkiej litery, część ma grupę. */
    private val brands: Map<String, Pair<String, ProductGroup?>> = mapOf(
        "MULL" to ("Müller" to ProductGroup.DAIRY), "MULLER" to ("Müller" to ProductGroup.DAIRY),
        "MILCH" to ("Milch" to null), "DANIO" to ("Danio" to ProductGroup.DAIRY), "ZOTT" to ("Zott" to ProductGroup.DAIRY),
        "PIATNICA" to ("Piątnica" to ProductGroup.DAIRY), "MLEKOVITA" to ("Mlekovita" to ProductGroup.DAIRY),
        "LACIATE" to ("Łaciate" to ProductGroup.DAIRY), "HOCHLAND" to ("Hochland" to ProductGroup.DAIRY),
        "PEPS" to ("Pepsi" to ProductGroup.DRINKS), "PEPSI" to ("Pepsi" to ProductGroup.DRINKS),
        "COCA" to ("Coca-Cola" to ProductGroup.DRINKS), "FANTA" to ("Fanta" to ProductGroup.DRINKS),
        "SPRITE" to ("Sprite" to ProductGroup.DRINKS), "TYMBARK" to ("Tymbark" to ProductGroup.DRINKS),
        "DR" to ("Dr" to null), "PEPP" to ("Pepper" to ProductGroup.DRINKS), "PEPPER" to ("Pepper" to ProductGroup.DRINKS),
        "LAYS" to ("Lay's" to ProductGroup.SWEETS), "CHEETOS" to ("Cheetos" to ProductGroup.SWEETS),
        "MILKA" to ("Milka" to ProductGroup.SWEETS), "WEDEL" to ("Wedel" to ProductGroup.SWEETS),
        "OREO" to ("Oreo" to ProductGroup.SWEETS), "HARIBO" to ("Haribo" to ProductGroup.SWEETS),
        "UHT" to ("UHT" to null), "XXL" to ("XXL" to null), "BIO" to ("BIO" to null),
    )

    /** Dwuwyrazowe skróty kas: „W WYB” (jaja z wolnego wybiegu), „B K” (bez kości). */
    private val phrases: Map<String, String> = mapOf(
        "W WYB" to "z wolnego wybiegu", "Z WYB" to "z wolnego wybiegu", "B K" to "bez kości", "Z K" to "z kością",
        "BEZ LAKT" to "bez laktozy", "B LAKT" to "bez laktozy",
    )

    private fun expandToken(token: String, mixed: Boolean = false): Pair<String, ProductGroup?> {
        val t = strip(token).trimEnd('.')
        if (t.isEmpty()) return "" to null
        unit.matchEntire(t)?.let { m ->
            val n = m.groupValues[1].replace('.', ',')
            val u = m.groupValues[2].lowercase(PL).let { if (it == "gr") "g" else if (it == "ltr") "l" else it }
            return (if (u == "%") "$n%" else "$n $u") to null
        }
        val keep = if (mixed) token.trimEnd('.') else token.lowercase(PL)
        if (t.any { it.isDigit() }) return token.lowercase(PL) to null
        byKey[t]?.let { return it.text to it.group }
        brands[t]?.let { return it }
        abbreviations[t]?.let { full -> return full to byKey[strip(full)]?.group }
        // W nazwach pisanych łącznie („SerSałKanPik”) krótkie kawałki często są skrótami marek — ostrożniej z prefiksem.
        if (t.length >= (if (mixed) 4 else 3)) {
            vocabulary.firstOrNull { it.key.startsWith(t) && !it.key.contains(' ') }?.let { return it.text to it.group }
        }
        return keep to null
    }

    /**
     * Rozbija nazwy pisane łącznie (Biedronka: „MullMilchCzek400g”, „NaPepsiCreamSo0,33L”)
     * na słowa: „Mull Milch Czek 400g”. Działa tylko dla zapisu mieszanego — nazwy WIELKIMI
     * literami zostają bez zmian.
     */
    fun splitCamel(s: String): String = s
        .replace(Regex("""(?<=\p{Ll})(?=\p{Lu}\p{Ll})"""), " ")
        .replace(Regex("""(?<=\p{Lu})(?=\p{Lu}\p{Ll})"""), " ")
        .replace(Regex("""(?<=\p{L})(?<!\d[xX])(?=\d)"""), " ")

    /** Rozwija nazwę z paragonu do czytelnej postaci i przypisuje grupę. */
    fun resolve(raw: String, learned: Map<String, String> = emptyMap()): Resolved {
        learned[key(raw)]?.let { return Resolved(it, groupOf(it)) }
        val mixed = raw.any { it.isLowerCase() } && raw.count { it.isUpperCase() } >= 2
        var s = raw.replace(Regex("""[<>|_~^"`]"""), " ").replace(Regex("""(?<=\p{L})-(?=\p{L})"""), " ")
        if (mixed) s = splitCamel(s)
        val tokens = s.replace(Regex("(?<=[A-Za-zĄĆĘŁŃÓŚŹŻąćęłńóśźż])\\.(?=\\S)"), ". ")
            .split(Regex("[\\s/]+")).filter { it.isNotBlank() }
            .let(::mergeUnits).let(::mergeSplit)
        val parts = mutableListOf<Pair<String, ProductGroup?>>()
        var i = 0
        while (i < tokens.size) {
            val pair = tokens.getOrNull(i + 1)?.let { strip(tokens[i]).trimEnd('.') + " " + strip(it).trimEnd('.') }
            val phrase = pair?.let { phrases[it] }
            when {
                phrase != null -> { parts += phrase to null; i += 2; continue }
                // „Na”/„N” na początku nazwy pisanej łącznie to napój („NaPepsiCream”, „N DrPepper”)
                mixed && i == 0 && tokens.size > 1 && strip(tokens[0]).trimEnd('.') in setOf("N", "NA", "NAP") ->
                    parts += "napój" to ProductGroup.DRINKS
                else -> parts += expandToken(tokens[i], mixed)
            }
            i++
        }
        val visible = parts.filter { it.first.isNotBlank() }
        val name = visible.joinToString(" ") { it.first }.replace(Regex("\\s+"), " ").trim()
            .replaceFirstChar { it.titlecase(PL) }
        val group = visible.firstNotNullOfOrNull { it.second } ?: groupOf(name)
        return Resolved(name.ifBlank { raw }, group)
    }

    /** Czy fragment da się rozpoznać (słowo, skrót albo prefiks słowa). */
    private fun known(t: String): Boolean {
        val k = strip(t).trimEnd('.')
        return k.length >= 2 && (byKey.containsKey(k) || abbreviations.containsKey(k) ||
            (k.length >= 3 && vocabulary.any { it.key.startsWith(k) && !it.key.contains(' ') }))
    }

    /** Skleja słowa rozbite przez OCR: „PS ZEN.” → „PSZEN.”, gdy osobno nic nie znaczą, a razem tak. */
    private fun mergeSplit(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val a = tokens[i]
            val b = tokens.getOrNull(i + 1)
            if (b != null && a.none { it.isDigit() } && b.none { it.isDigit() } && !a.endsWith('.') &&
                (!known(a) || !known(b)) && strip(a).length <= 4 && known(a + b)) {
                out += a + b; i += 2
            } else { out += a; i++ }
        }
        return out
    }

    private val unitWord = Regex("""^(G|GR|KG|ML|L|LTR|SZT)\.?$""", RegexOption.IGNORE_CASE)

    /** „500 ML” → „500ML”, żeby jednostka nie została wzięta za skrót („ML” = mleko). */
    private fun mergeUnits(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (t in tokens) {
            val prev = out.lastOrNull()
            if (prev != null && prev.matches(Regex("""\d+(?:[.,]\d+)?""")) && unitWord.matches(t)) out[out.lastIndex] = prev + t.trimEnd('.')
            else out += t
        }
        return out
    }

    /** Grupa dla nazwy już czytelnej (np. poprawionej przez użytkownika). */
    fun groupOf(name: String): ProductGroup {
        val words = strip(name).split(Regex("[^A-Z0-9]+")).filter { it.length >= 2 }
        for (w in words) {
            byKey[w]?.group?.let { return it }
            if (w.length >= 4) vocabulary.firstOrNull { it.group != null && it.key.startsWith(w) }?.group?.let { return it }
        }
        return ProductGroup.OTHER
    }
}
