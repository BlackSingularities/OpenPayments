package pl.openpayments.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Kwoty przechowujemy w groszach (Long), żeby uniknąć błędów zaokrągleń. Daty jako epoch day. */

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Long,
    val isIncome: Boolean = false,
)

enum class TxSource { MANUAL, RECEIPT, RECURRING }

@Entity(
    tableName = "transactions",
    indices = [Index("categoryId"), Index("date")],
    foreignKeys = [ForeignKey(
        entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL,
    )],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val isIncome: Boolean = false,
    val merchant: String,
    val note: String = "",
    val categoryId: Long?,
    val date: Long,
    val source: TxSource = TxSource.MANUAL,
    val receiptImage: String? = null,
    val subscriptionId: Long? = null,
)

@Entity(
    tableName = "receipt_items",
    indices = [Index("transactionId")],
    foreignKeys = [ForeignKey(
        entity = Transaction::class, parentColumns = ["id"], childColumns = ["transactionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ReceiptItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long = 0,
    val name: String,
    val quantity: Double = 1.0,
    val price: Long,
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limit: Long,
    val categoryId: Long?,
    val alertPercent: Int = 80,
)

enum class Cycle(val label: String) { WEEKLY("co tydzień"), MONTHLY("co miesiąc"), YEARLY("co rok") }

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val categoryId: Long?,
    val cycle: Cycle = Cycle.MONTHLY,
    val nextDate: Long,
    val autoBook: Boolean = true,
    val active: Boolean = true,
)

/** Wyuczona reguła: „sprzedawca zawiera słowo → kategoria”. */
@Entity(tableName = "category_rules", indices = [Index(value = ["keyword"], unique = true)])
data class CategoryRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val categoryId: Long,
)

data class ProductStat(val name: String, val total: Long, val count: Int, val lastDate: Long)
