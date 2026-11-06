package pl.openpayments.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isIncome, name") fun observeAll(): Flow<List<Category>>
    @Query("SELECT * FROM categories") suspend fun all(): List<Category>
    @Query("SELECT COUNT(*) FROM categories") suspend fun count(): Int
    @Upsert suspend fun upsert(c: Category): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(c: List<Category>)
    @Delete suspend fun delete(c: Category)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC") fun observeAll(): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun get(id: Long): Transaction?
    @Query("SELECT * FROM transactions ORDER BY date DESC") suspend fun all(): List<Transaction>

    @Query(
        """SELECT COALESCE(SUM(amount),0) FROM transactions
           WHERE isIncome = 0 AND date BETWEEN :from AND :to AND (:cat IS NULL OR categoryId = :cat)"""
    )
    suspend fun spent(from: Long, to: Long, cat: Long?): Long

    @Insert suspend fun insert(t: Transaction): Long
    @androidx.room.Update suspend fun update(t: Transaction)
    @Delete suspend fun delete(t: Transaction)

    @Query("SELECT * FROM receipt_items WHERE transactionId = :txId") suspend fun items(txId: Long): List<ReceiptItem>
    @Query("DELETE FROM receipt_items WHERE transactionId = :txId") suspend fun clearItems(txId: Long)
    @Insert suspend fun insertItems(items: List<ReceiptItem>)
    @Query("SELECT * FROM receipt_items") suspend fun allItems(): List<ReceiptItem>
    @Query("SELECT * FROM receipt_items WHERE productGroup IS NULL") suspend fun itemsWithoutGroup(): List<ReceiptItem>
    @Query("UPDATE receipt_items SET productGroup = :group WHERE id = :id") suspend fun setGroup(id: Long, group: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(t: List<Transaction>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllItems(items: List<ReceiptItem>)

    /** Produkty z paragonów w zakresie dat: nazwa, łączna kwota, liczba zakupów. */
    @Query(
        """SELECT LOWER(i.name) AS name, SUM(i.price) AS total, COUNT(*) AS count, MAX(t.date) AS lastDate
           FROM receipt_items i JOIN transactions t ON t.id = i.transactionId
           WHERE t.date BETWEEN :from AND :to GROUP BY LOWER(i.name) ORDER BY total DESC LIMIT :limit"""
    )
    fun observeProducts(from: Long, to: Long, limit: Int): Flow<List<ProductStat>>

    /** Wydatki z paragonów w zakresie dat w podziale na grupy produktów. */
    @Query(
        """SELECT i.productGroup AS productGroup, SUM(i.price) AS total
           FROM receipt_items i JOIN transactions t ON t.id = i.transactionId
           WHERE t.date BETWEEN :from AND :to GROUP BY i.productGroup ORDER BY total DESC"""
    )
    fun observeGroups(from: Long, to: Long): Flow<List<GroupSum>>

    @androidx.room.Transaction
    suspend fun saveWithItems(t: Transaction, items: List<ReceiptItem>?): Long {
        val id = if (t.id == 0L) insert(t) else { update(t); t.id }
        if (items != null) {
            clearItems(id)
            insertItems(items.map { it.copy(id = 0, transactionId = id) })
        }
        return id
    }
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY categoryId IS NOT NULL, name") fun observeAll(): Flow<List<Budget>>
    @Query("SELECT * FROM budgets") suspend fun all(): List<Budget>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(b: List<Budget>)
    @Upsert suspend fun upsert(b: Budget)
    @Delete suspend fun delete(b: Budget)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY active DESC, nextDate") fun observeAll(): Flow<List<Subscription>>
    @Query("SELECT * FROM subscriptions WHERE active = 1 AND nextDate <= :today") suspend fun due(today: Long): List<Subscription>
    @Query("SELECT * FROM subscriptions WHERE active = 1 AND nextDate BETWEEN :from AND :to")
    suspend fun upcoming(from: Long, to: Long): List<Subscription>
    @Upsert suspend fun upsert(s: Subscription)
    @Query("SELECT * FROM subscriptions") suspend fun all(): List<Subscription>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(s: List<Subscription>)
    @Delete suspend fun delete(s: Subscription)
}

@Dao
interface AliasDao {
    @Query("SELECT * FROM product_aliases") suspend fun all(): List<ProductAlias>
    @Query("SELECT COUNT(*) FROM product_aliases") fun observeCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(a: ProductAlias)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(a: List<ProductAlias>)
    @Query("DELETE FROM product_aliases") suspend fun clear()
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM category_rules") suspend fun all(): List<CategoryRule>
    @Query("SELECT COUNT(*) FROM category_rules") fun observeCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(r: CategoryRule)
    @Query("DELETE FROM category_rules") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(r: List<CategoryRule>)
}
