package com.yuanguangbb.ledger.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    // ---------- 账单 ----------

    @Insert
    suspend fun insertTransaction(entity: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(entity: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransaction(id: Long): TransactionEntity?

    /** 删除进回收站（软删除），保留一段时日可恢复 */
    @Query("UPDATE transactions SET deletedAtMillis = :now WHERE id = :id AND deletedAtMillis IS NULL")
    suspend fun softDeleteTransaction(id: Long, now: Long)

    /** 一键删除全部账单：全部进回收站，返回放入回收站的条数 */
    @Query("UPDATE transactions SET deletedAtMillis = :now WHERE deletedAtMillis IS NULL")
    suspend fun softDeleteAllTransactions(now: Long): Int

    /**
     * 账单列表（含筛选与分页）。
     * 分类/账户用 LEFT JOIN：万一分组被删，账单也不会从列表里消失。
     */
    @Query(
        """
        SELECT t.id AS id, t.isIncome AS isIncome, t.amountCents AS amountCents,
               t.timeMillis AS timeMillis, t.createdAtMillis AS createdAtMillis,
               t.note AS note, t.receiptPath AS receiptPath, t.deletedAtMillis AS deletedAtMillis,
               IFNULL(c.name, '已删除分类') AS categoryName,
               IFNULL(a.name, '已删除账户') AS accountName,
               p.name AS productName, t.quantity AS quantity
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN products p ON p.id = t.productId
        WHERE t.deletedAtMillis IS NULL
          AND (:fromMillis IS NULL OR t.timeMillis >= :fromMillis)
          AND (:toMillis IS NULL OR t.timeMillis < :toMillis)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:accountId IS NULL OR t.accountId = :accountId)
          AND (:productId IS NULL OR t.productId = :productId)
          AND (:keyword IS NULL OR :keyword = ''
               OR t.note LIKE '%' || :keyword || '%'
               OR IFNULL(c.name, '') LIKE '%' || :keyword || '%'
               OR IFNULL(a.name, '') LIKE '%' || :keyword || '%'
               OR IFNULL(p.name, '') LIKE '%' || :keyword || '%')
        ORDER BY t.timeMillis DESC, t.id DESC
        LIMIT :limit
        """
    )
    fun observeTransactionsFiltered(
        fromMillis: Long?,
        toMillis: Long?,
        categoryId: Long?,
        accountId: Long?,
        productId: Long?,
        keyword: String?,
        limit: Int,
    ): Flow<List<TransactionWithNames>>

    /** 回收站列表 */
    @Query(
        """
        SELECT t.id AS id, t.isIncome AS isIncome, t.amountCents AS amountCents,
               t.timeMillis AS timeMillis, t.createdAtMillis AS createdAtMillis,
               t.note AS note, t.receiptPath AS receiptPath, t.deletedAtMillis AS deletedAtMillis,
               IFNULL(c.name, '已删除分类') AS categoryName,
               IFNULL(a.name, '已删除账户') AS accountName,
               p.name AS productName, t.quantity AS quantity
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN accounts a ON a.id = t.accountId
        LEFT JOIN products p ON p.id = t.productId
        WHERE t.deletedAtMillis IS NOT NULL
        ORDER BY t.deletedAtMillis DESC, t.id DESC
        """
    )
    fun observeDeletedTransactions(): Flow<List<TransactionWithNames>>

    @Query("UPDATE transactions SET deletedAtMillis = NULL WHERE id = :id")
    suspend fun restoreTransaction(id: Long)

    @Query("DELETE FROM transactions WHERE id = :id AND deletedAtMillis IS NOT NULL")
    suspend fun purgeTransaction(id: Long)

    @Query("DELETE FROM transactions WHERE deletedAtMillis IS NOT NULL")
    suspend fun purgeAllDeleted(): Int

    /** 回收站里超过 [beforeMillis] 没动过的，永久清掉 */
    @Query("DELETE FROM transactions WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :beforeMillis")
    suspend fun purgeDeletedBefore(beforeMillis: Long): Int

    // ---------- 统计 ----------

    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE deletedAtMillis IS NULL AND isIncome = :isIncome
          AND timeMillis >= :fromMillis AND timeMillis < :toMillis
        """
    )
    fun observeSum(isIncome: Boolean, fromMillis: Long, toMillis: Long): Flow<Long>

    /** 报表：某个区间内按分类汇总 */
    @Query(
        """
        SELECT IFNULL(c.name, '已删除分类') AS name, t.isIncome AS isIncome,
               SUM(t.amountCents) AS totalCents
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.deletedAtMillis IS NULL
          AND t.timeMillis >= :fromMillis AND t.timeMillis < :toMillis
        GROUP BY t.categoryId, t.isIncome
        ORDER BY totalCents DESC
        """
    )
    fun observeCategorySums(fromMillis: Long, toMillis: Long): Flow<List<CategorySumRow>>

    /** 报表：按月汇总，month 形如 2026-11 */
    @Query(
        """
        SELECT strftime('%Y-%m', t.timeMillis / 1000, 'unixepoch', 'localtime') AS month,
               t.isIncome AS isIncome,
               SUM(t.amountCents) AS totalCents
        FROM transactions t
        WHERE t.deletedAtMillis IS NULL
          AND t.timeMillis >= :fromMillis AND t.timeMillis < :toMillis
        GROUP BY month, t.isIncome
        """
    )
    fun observeMonthSums(fromMillis: Long, toMillis: Long): Flow<List<MonthSumRow>>

    // ---------- 分类 / 账户 ----------

    @Query("SELECT * FROM categories WHERE isIncome = :isIncome ORDER BY id")
    fun observeCategories(isIncome: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isIncome DESC, id")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insertCategory(entity: CategoryEntity): Long

    @Update
    suspend fun updateCategory(entity: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id AND deletedAtMillis IS NULL")
    suspend fun countByCategory(id: Long): Int

    /** 含回收站在内：有账单引用就不许删分类 */
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id")
    suspend fun countByCategoryAll(id: Long): Int

    @Query("SELECT id FROM categories WHERE name = :name AND isIncome = :isIncome LIMIT 1")
    suspend fun findCategoryId(name: String, isIncome: Boolean): Long?

    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Insert
    suspend fun insertAccount(entity: AccountEntity): Long

    @Update
    suspend fun updateAccount(entity: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id AND deletedAtMillis IS NULL")
    suspend fun countByAccount(id: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id")
    suspend fun countByAccountAll(id: Long): Int

    @Query("SELECT id FROM accounts WHERE name = :name LIMIT 1")
    suspend fun findAccountId(name: String): Long?

    /** 从备份恢复：整批写进去，中途出错全部回滚 */
    @Transaction
    suspend fun importTransactions(entities: List<TransactionEntity>) {
        entities.forEach { insertTransaction(it) }
    }

    // ---------- 商品 ----------

    /** 名字已存在时返回 -1，不会重复添加 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProduct(entity: ProductEntity): Long

    @Query("SELECT id FROM products WHERE name = :name LIMIT 1")
    suspend fun findProductId(name: String): Long?

    @Query("SELECT * FROM products ORDER BY name")
    fun observeProducts(): Flow<List<ProductEntity>>

    /** 清空商品档案前，先把账单里的商品/数量摘掉（账单本身保留） */
    @Query("UPDATE transactions SET productId = NULL, quantity = NULL WHERE productId IS NOT NULL")
    suspend fun detachProductsFromTransactions(): Int

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts(): Int

    /** 一键删除全部商品：账单保留，只摘掉账单里的商品和数量 */
    @Transaction
    suspend fun clearAllProducts(): Int {
        detachProductsFromTransactions()
        return deleteAllProducts()
    }

    @Query(
        """
        SELECT p.id AS productId, p.name AS name,
               COALESCE(SUM(CASE WHEN t.isIncome = 1 THEN t.quantity END), 0) AS soldQty,
               COALESCE(SUM(CASE WHEN t.isIncome = 1 THEN t.amountCents END), 0) AS revenueCents,
               COALESCE(SUM(CASE WHEN t.isIncome = 0 THEN t.quantity END), 0) AS purchasedQty,
               COALESCE(SUM(CASE WHEN t.isIncome = 0 THEN t.amountCents END), 0) AS purchaseCents
        FROM products p
        LEFT JOIN transactions t ON t.productId = p.id AND t.deletedAtMillis IS NULL
        GROUP BY p.id, p.name
        """
    )
    fun observeProductStats(): Flow<List<ProductStatRow>>

    /** 滞销/畅销提醒用的汇总（最近卖出时间、近 N 天销量） */
    @Query(
        """
        SELECT p.id AS productId, p.name AS name,
               COALESCE(SUM(CASE WHEN t.isIncome = 1 THEN t.quantity END), 0) AS soldQty,
               COALESCE(SUM(CASE WHEN t.isIncome = 1 THEN t.amountCents END), 0) AS revenueCents,
               COALESCE(SUM(CASE WHEN t.isIncome = 0 THEN t.quantity END), 0) AS purchasedQty,
               COALESCE(SUM(CASE WHEN t.isIncome = 0 THEN t.amountCents END), 0) AS purchaseCents,
               MAX(CASE WHEN t.isIncome = 1 THEN t.timeMillis END) AS lastSoldAtMillis,
               MAX(CASE WHEN t.isIncome = 0 THEN t.timeMillis END) AS lastPurchasedAtMillis,
               COALESCE(SUM(CASE WHEN t.isIncome = 1 AND t.timeMillis >= :sinceMillis
                                 THEN t.quantity END), 0) AS soldQtyInWindow,
               COALESCE(SUM(CASE WHEN t.isIncome = 1 AND t.timeMillis >= :sinceMillis
                                 THEN t.amountCents END), 0) AS revenueCentsInWindow
        FROM products p
        LEFT JOIN transactions t ON t.productId = p.id AND t.deletedAtMillis IS NULL
        GROUP BY p.id, p.name
        """
    )
    fun observeProductActivity(sinceMillis: Long): Flow<List<ProductActivityRow>>

    // ---------- 赊账 ----------

    @Insert
    suspend fun insertCustomer(entity: CustomerEntity): Long

    @Query("SELECT id FROM customers WHERE name = :name LIMIT 1")
    suspend fun findCustomerId(name: String): Long?

    @Query(
        """
        SELECT cu.id AS customerId, cu.name AS name, cu.phone AS phone,
               COALESCE(SUM(CASE WHEN d.settledAtMillis IS NULL THEN d.amountCents END), 0) AS outstandingCents,
               COUNT(d.id) AS debtCount,
               COALESCE(MAX(d.timeMillis), 0) AS lastTimeMillis
        FROM customers cu
        LEFT JOIN debts d ON d.customerId = cu.id
        GROUP BY cu.id, cu.name, cu.phone
        ORDER BY outstandingCents DESC, cu.name
        """
    )
    fun observeCustomers(): Flow<List<CustomerWithDebt>>

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long)

    @Insert
    suspend fun insertDebt(entity: DebtEntity): Long

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebt(id: Long): DebtEntity?

    @Query("UPDATE debts SET incomeTransactionId = :transactionId WHERE id = :id")
    suspend fun linkDebtIncome(id: Long, transactionId: Long)

    /** 记赊账：欠款 + 自动生成的那笔收入账单，一起写进去 */
    @Transaction
    suspend fun insertDebtWithIncome(debt: DebtEntity, income: TransactionEntity): Long {
        val debtId = insertDebt(debt)
        val transactionId = insertTransaction(income)
        linkDebtIncome(debtId, transactionId)
        return debtId
    }

    @Query("SELECT * FROM debts WHERE customerId = :customerId ORDER BY timeMillis DESC, id DESC")
    fun observeDebts(customerId: Long): Flow<List<DebtEntity>>

    /** 备份用：所有赊账 + 客户名 */
    @Query(
        """
        SELECT cu.name AS customerName, d.amountCents AS amountCents,
               d.timeMillis AS timeMillis, d.note AS note, d.settledAtMillis AS settledAtMillis
        FROM debts d
        JOIN customers cu ON cu.id = d.customerId
        ORDER BY d.timeMillis
        """
    )
    suspend fun getAllDebtsForBackup(): List<DebtBackupRow>

    @Query("UPDATE debts SET settledAtMillis = :now WHERE id = :id")
    suspend fun settleDebt(id: Long, now: Long)

    @Query("UPDATE debts SET settledAtMillis = NULL WHERE id = :id")
    suspend fun unsettleDebt(id: Long)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: Long)

    /** 删一条赊账，连带把自动生成的那笔收入账单放进回收站 */
    @Transaction
    suspend fun deleteDebtWithIncome(id: Long) {
        val linked = getDebt(id)?.incomeTransactionId
        deleteDebt(id)
        if (linked != null) softDeleteTransaction(linked, System.currentTimeMillis())
    }

    @Query("SELECT incomeTransactionId FROM debts WHERE customerId = :customerId AND incomeTransactionId IS NOT NULL")
    suspend fun linkedIncomeIdsOfCustomer(customerId: Long): List<Long>

    @Query("DELETE FROM debts WHERE customerId = :customerId")
    suspend fun deleteDebtsOf(customerId: Long)

    /** 删客户：欠款记录 + 每笔赊账自动生成的收入，一起处理 */
    @Transaction
    suspend fun deleteCustomerWithDebtsAndIncome(customerId: Long) {
        val now = System.currentTimeMillis()
        linkedIncomeIdsOfCustomer(customerId).forEach { softDeleteTransaction(it, now) }
        deleteDebtsOf(customerId)
        deleteCustomer(customerId)
    }
}