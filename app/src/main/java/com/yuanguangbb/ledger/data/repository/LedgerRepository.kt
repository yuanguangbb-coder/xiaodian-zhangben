package com.yuanguangbb.ledger.data.repository

import com.yuanguangbb.ledger.data.db.AccountEntity
import com.yuanguangbb.ledger.data.db.CategoryEntity
import com.yuanguangbb.ledger.data.db.CategorySumRow
import com.yuanguangbb.ledger.data.db.CustomerEntity
import com.yuanguangbb.ledger.data.db.CustomerWithDebt
import com.yuanguangbb.ledger.data.db.DebtBackupRow
import com.yuanguangbb.ledger.data.db.DebtEntity
import com.yuanguangbb.ledger.data.db.LedgerDao
import com.yuanguangbb.ledger.data.db.MonthSumRow
import com.yuanguangbb.ledger.data.db.ProductEntity
import com.yuanguangbb.ledger.data.db.ProductStatRow
import com.yuanguangbb.ledger.data.db.TransactionEntity
import com.yuanguangbb.ledger.data.db.TransactionWithNames
import com.yuanguangbb.ledger.domain.product.ProductActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 账单筛选条件。空字段表示不限。 */
data class TransactionFilter(
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val productId: Long? = null,
    val keyword: String = "",
) {
    val hasKeyword: Boolean get() = keyword.isNotBlank()
}

/** 界面层只和 Repository 打交道，不直接碰数据库。 */
class LedgerRepository(private val dao: LedgerDao) {

    val accounts: Flow<List<AccountEntity>> = dao.observeAccounts()
    val products: Flow<List<ProductEntity>> = dao.observeProducts()
    val productStats: Flow<List<ProductStatRow>> = dao.observeProductStats()
    val allCategories: Flow<List<CategoryEntity>> = dao.observeAllCategories()
    val deletedTransactions: Flow<List<TransactionWithNames>> = dao.observeDeletedTransactions()
    val customers: Flow<List<CustomerWithDebt>> = dao.observeCustomers()

    fun categories(isIncome: Boolean): Flow<List<CategoryEntity>> = dao.observeCategories(isIncome)

    fun transactions(
        filter: TransactionFilter = TransactionFilter(),
        limit: Int = 500,
    ): Flow<List<TransactionWithNames>> =
        dao.observeTransactionsFiltered(
            fromMillis = filter.fromMillis,
            toMillis = filter.toMillis,
            categoryId = filter.categoryId,
            accountId = filter.accountId,
            productId = filter.productId,
            keyword = filter.keyword.trim().ifEmpty { null },
            limit = limit,
        )

    /** 滞销/畅销提醒用：每个商品的流水汇总 */
    fun productActivity(sinceMillis: Long): Flow<List<ProductActivity>> =
        dao.observeProductActivity(sinceMillis).map { rows ->
            rows.map {
                ProductActivity(
                    productId = it.productId,
                    name = it.name,
                    soldQty = it.soldQty,
                    revenueCents = it.revenueCents,
                    purchasedQty = it.purchasedQty,
                    purchaseCents = it.purchaseCents,
                    lastSoldAtMillis = it.lastSoldAtMillis,
                    lastPurchasedAtMillis = it.lastPurchasedAtMillis,
                    soldQtyInWindow = it.soldQtyInWindow,
                    revenueCentsInWindow = it.revenueCentsInWindow,
                )
            }
        }

    fun sum(isIncome: Boolean, fromMillis: Long, toMillis: Long): Flow<Long> =
        dao.observeSum(isIncome, fromMillis, toMillis)

    // ---------- 报表 ----------

    fun categorySums(fromMillis: Long, toMillis: Long): Flow<List<CategorySumRow>> =
        dao.observeCategorySums(fromMillis, toMillis)

    fun monthSums(fromMillis: Long, toMillis: Long): Flow<List<MonthSumRow>> =
        dao.observeMonthSums(fromMillis, toMillis)

    // ---------- 账单 ----------

    suspend fun addTransaction(
        isIncome: Boolean,
        amountCents: Long,
        categoryId: Long,
        accountId: Long,
        timeMillis: Long,
        note: String,
        productId: Long? = null,
        quantity: Int? = null,
        receiptPath: String? = null,
        /** 录入时间，默认就是保存的这一刻 */
        createdAtMillis: Long = System.currentTimeMillis(),
    ): Long = dao.insertTransaction(
        TransactionEntity(
            isIncome = isIncome,
            amountCents = amountCents,
            categoryId = categoryId,
            accountId = accountId,
            timeMillis = timeMillis,
            createdAtMillis = createdAtMillis,
            note = note.trim(),
            productId = productId,
            quantity = quantity,
            receiptPath = receiptPath,
        )
    )

    suspend fun updateTransaction(entity: TransactionEntity) = dao.updateTransaction(entity)

    suspend fun getTransaction(id: Long): TransactionEntity? = dao.getTransaction(id)

    /** 删除单笔：先进回收站 */
    suspend fun deleteTransaction(id: Long) = dao.softDeleteTransaction(id, System.currentTimeMillis())

    /** 一键删除全部账单：全部进回收站，返回条数 */
    suspend fun deleteAllTransactions(): Int =
        dao.softDeleteAllTransactions(System.currentTimeMillis())

    suspend fun restoreTransaction(id: Long) = dao.restoreTransaction(id)

    suspend fun purgeTransaction(id: Long) = dao.purgeTransaction(id)

    suspend fun purgeAllDeleted(): Int = dao.purgeAllDeleted()

    /** 回收站超过 [days] 天的自动清掉，返回清掉的条数 */
    suspend fun purgeDeletedOlderThan(days: Long = RECYCLE_DAYS): Int {
        val before = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        return dao.purgeDeletedBefore(before)
    }

    // ---------- 从备份恢复用 ----------

    suspend fun findOrCreateCategoryId(name: String, isIncome: Boolean): Long {
        val clean = name.trim().ifEmpty { if (isIncome) "销售收入" else "其他支出" }
        return dao.findCategoryId(clean, isIncome)
            ?: dao.insertCategory(CategoryEntity(name = clean, isIncome = isIncome))
    }

    suspend fun findOrCreateAccountId(name: String): Long {
        val clean = name.trim().ifEmpty { "其他" }
        return dao.findAccountId(clean) ?: dao.insertAccount(AccountEntity(name = clean))
    }

    suspend fun importTransactions(entities: List<TransactionEntity>) = dao.importTransactions(entities)

    // ---------- 分类 / 账户 ----------

    suspend fun addCategory(name: String, isIncome: Boolean): Long =
        dao.insertCategory(CategoryEntity(name = name.trim(), isIncome = isIncome))

    suspend fun renameCategory(entity: CategoryEntity) = dao.updateCategory(entity)

    /** 分类被账单用着就不让删，返回 false。回收站里的账单也算「用着」。 */
    suspend fun deleteCategory(id: Long): Boolean {
        if (dao.countByCategoryAll(id) > 0) return false
        dao.deleteCategory(id)
        return true
    }

    suspend fun addAccount(name: String): Long = dao.insertAccount(AccountEntity(name = name.trim()))

    suspend fun renameAccount(entity: AccountEntity) = dao.updateAccount(entity)

    suspend fun deleteAccount(id: Long): Boolean {
        if (dao.countByAccountAll(id) > 0) return false
        dao.deleteAccount(id)
        return true
    }

    // ---------- 商品 ----------

    /** 按名字找商品，没有就新建。名字为空返回 null。 */
    suspend fun getOrCreateProductId(name: String): Long? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        dao.insertProduct(ProductEntity(name = clean))
        return dao.findProductId(clean)
    }

    /** 一键删除全部商品（账单保留，只摘掉账单里的商品/数量），返回删除个数 */
    suspend fun clearAllProducts(): Int = dao.clearAllProducts()

    // ---------- 赊账 ----------

    suspend fun addCustomer(name: String, phone: String): Long? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val existing = dao.findCustomerId(clean)
        if (existing != null) return existing
        return dao.insertCustomer(CustomerEntity(name = clean, phone = phone.trim()))
    }

    suspend fun deleteCustomer(id: Long) = dao.deleteCustomerWithDebtsAndIncome(id)

    /**
     * 记一笔赊账。默认同时生成一笔收入（分类「销售收入」、账户「赊账」），
     * 也就是「赊出去就算卖掉了」；收款只冲欠款，不再记第二笔收入。
     */
    suspend fun addDebt(
        customerId: Long,
        customerName: String,
        amountCents: Long,
        timeMillis: Long,
        note: String,
        settledAtMillis: Long? = null,
        createIncome: Boolean = true,
    ): Long {
        val debt = DebtEntity(
            customerId = customerId,
            amountCents = amountCents,
            timeMillis = timeMillis,
            note = note.trim(),
            settledAtMillis = settledAtMillis,
        )
        if (!createIncome) return dao.insertDebt(debt)

        val categoryId = findOrCreateCategoryId("销售收入", isIncome = true)
        val accountId = findOrCreateAccountId("赊账")
        val incomeNote = buildString {
            append("赊账：").append(customerName.trim())
            if (note.isNotBlank()) append("（").append(note.trim()).append("）")
        }
        val income = TransactionEntity(
            isIncome = true,
            amountCents = amountCents,
            categoryId = categoryId,
            accountId = accountId,
            timeMillis = timeMillis,
            createdAtMillis = System.currentTimeMillis(),
            note = incomeNote,
        )
        return dao.insertDebtWithIncome(debt, income)
    }

    fun debts(customerId: Long): Flow<List<DebtEntity>> = dao.observeDebts(customerId)

    /** 备份用：所有赊账（带客户名） */
    suspend fun debtsForBackup(): List<DebtBackupRow> = dao.getAllDebtsForBackup()

    suspend fun settleDebt(id: Long) = dao.settleDebt(id, System.currentTimeMillis())

    suspend fun unsettleDebt(id: Long) = dao.unsettleDebt(id)

    suspend fun deleteDebt(id: Long) = dao.deleteDebtWithIncome(id)

    companion object {
        /** 回收站保留天数 */
        const val RECYCLE_DAYS = 30L
    }
}