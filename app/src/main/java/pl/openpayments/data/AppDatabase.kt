package pl.openpayments.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun src(v: TxSource): String = v.name
    @TypeConverter fun toSrc(v: String): TxSource = TxSource.valueOf(v)
    @TypeConverter fun cyc(v: Cycle): String = v.name
    @TypeConverter fun toCyc(v: String): Cycle = Cycle.valueOf(v)
}

@Database(
    entities = [Category::class, Transaction::class, ReceiptItem::class, Budget::class,
        Subscription::class, CategoryRule::class],
    version = 1, exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun budgets(): BudgetDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun rules(): RuleDao

    companion object {
        fun build(ctx: Context) =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "openpayments.db").build()
    }
}
