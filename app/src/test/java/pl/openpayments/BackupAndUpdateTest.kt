package pl.openpayments

import org.junit.Assert.*
import org.junit.Test
import pl.openpayments.data.*
import pl.openpayments.update.Updates

class BackupAndUpdateTest {

    @Test fun backupRoundTrip() {
        val s = Backup.Snapshot(
            categories = listOf(Category(1, "Zakupy", "cart", 0xFF2E8B6E)),
            transactions = listOf(
                Transaction(10, 7565, false, "Lidl", "obiad", 1, 20000, TxSource.RECEIPT),
                Transaction(11, 500000, true, "Pensja", "", null, 20001),
            ),
            items = listOf(ReceiptItem(5, 10, "Mleko", 2.0, 998), ReceiptItem(6, 99, "Sierota", 1.0, 1)),
            budgets = listOf(Budget(1, "Miesięczny", 300000, null, 85)),
            subscriptions = listOf(Subscription(1, "Netflix", 6000, 1, Cycle.MONTHLY, 20010, true, true)),
            rules = listOf(CategoryRule(1, "lidl", 1), CategoryRule(2, "x", 42)),
        )
        val back = Backup.decode(Backup.encode(s, "1.1.0"))
        assertEquals(s.categories, back.categories)
        assertEquals(s.transactions.map { it.copy(receiptImage = null) }, back.transactions)
        assertEquals(1, back.items.size)          // pozycja bez transakcji odrzucona
        assertEquals(s.budgets, back.budgets)
        assertEquals(s.subscriptions, back.subscriptions)
        assertEquals(1, back.rules.size)          // reguła do nieistniejącej kategorii odrzucona
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignFile() { Backup.decode("""{"app":"Other","format":1}""") }

    @Test fun versionComparison() {
        assertTrue(Updates.newer("1.1.0", "1.0.9"))
        assertTrue(Updates.newer("1.10.0", "1.9.9"))
        assertFalse(Updates.newer("1.1.0", "1.1.0"))
        assertFalse(Updates.newer("1.1.0-beta", "1.0.0"))
        assertTrue(Updates.newer("1.1.0", "1.0.0-debug"))
    }

    @Test fun parsesReleaseFeed() {
        val base = Updates.ASSET_PREFIX
        val json = """{"tag_name":"v1.2.0","assets":[
            {"name":"OpenPayments.apk","size":123,"browser_download_url":"${base}1/OpenPayments.apk"},
            {"name":"OpenPayments.apk.sha256","browser_download_url":"${base}2/OpenPayments.apk.sha256"}]}"""
        val r = Updates.parse(json, "1.1.0")!!
        assertEquals("1.2.0", r.version)
        assertTrue(Updates.allowed(r.apk) && Updates.allowed(r.checksum))
        assertNull(Updates.parse(json, "1.2.0"))
        assertFalse(Updates.allowed("https://evil.example/OpenPayments.apk"))
    }
}
