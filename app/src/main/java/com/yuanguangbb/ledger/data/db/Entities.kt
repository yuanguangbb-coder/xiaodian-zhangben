package com.yuanguangbb.ledger.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 分类：进货、房租、人工、销售收入等 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isIncome: Boolean,
)

/** 账户：微信、支付宝、现金、银行卡等 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** 商品：可乐、香烟、大米等。名字不重复。 */
@Entity(tableName = "products", indices = [Index(value = ["name"], unique = true)])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** 一笔账。金额统一用「分」存储（Long），避免浮点误差。 */
@Entity(tableName = "transactions", indices = [Index("timeMillis")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isIncome: Boolean,
    val amountCents: Long,
    val categoryId: Long,
    val accountId: Long,
    /** 这笔账的日期时间（记在账本上的时间点） */
    val timeMillis: Long,
    /** 录入时间：这笔账真正被保存进账本的时间，由系统在保存时写入 */
    val createdAtMillis: Long = timeMillis,
    val note: String = "",
    /** 单据照片路径，后续做拍照功能时使用 */
    val receiptPath: String? = null,
    /** 可选：这笔账对应的商品（收入=卖出，支出=进货） */
    val productId: Long? = null,
    /** 可选：数量（件），和 productId 一起使用 */
    val quantity: Int? = null,
    /** 软删除时间：不为空表示在回收站里，不进统计 */
    val deletedAtMillis: Long? = null,
)

/** 列表展示用：账目 + 分类名 + 账户名 */
data class TransactionWithNames(
    val id: Long,
    val isIncome: Boolean,
    val amountCents: Long,
    /** 账单日期时间 */
    val timeMillis: Long,
    /** 账单录入时间 */
    val createdAtMillis: Long,
    val note: String,
    val categoryName: String,
    val accountName: String,
    val productName: String?,
    val quantity: Int?,
    val receiptPath: String?,
    /** 回收站里的条目，删除时间不为空 */
    val deletedAtMillis: Long?,
)

/** 每个商品的汇总：卖出数量/金额、进货数量/金额（全部历史） */
data class ProductStatRow(
    val productId: Long,
    val name: String,
    val soldQty: Long,
    val revenueCents: Long,
    val purchasedQty: Long,
    val purchaseCents: Long,
)

/** 做滞销/畅销提醒用的汇总：在 [ProductStatRow] 基础上加最近卖出/进货时间和近 30 天销量。 */
data class ProductActivityRow(
    val productId: Long,
    val name: String,
    val soldQty: Long,
    val revenueCents: Long,
    val purchasedQty: Long,
    val purchaseCents: Long,
    val lastSoldAtMillis: Long?,
    val lastPurchasedAtMillis: Long?,
    val soldQtyInWindow: Long,
    val revenueCentsInWindow: Long,
)

/** 赊账客户 */
@Entity(tableName = "customers", indices = [Index(value = ["name"], unique = true)])
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
)

/** 一笔赊账（客户欠我）。settledAtMillis 不为空表示已还清。 */
@Entity(tableName = "debts", indices = [Index("customerId"), Index("timeMillis")])
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amountCents: Long,
    val timeMillis: Long,
    val note: String = "",
    val settledAtMillis: Long? = null,
    /** 赊账时自动记的那笔收入账单 id；老数据或导入的赊账为空 */
    val incomeTransactionId: Long? = null,
)

/** 客户列表用：名字 + 还没还的合计 */
data class CustomerWithDebt(
    val customerId: Long,
    val name: String,
    val phone: String,
    val outstandingCents: Long,
    val debtCount: Long,
    val lastTimeMillis: Long,
)

/** 备份用：一条赊账 + 客户名 */
data class DebtBackupRow(
    val customerName: String,
    val amountCents: Long,
    val timeMillis: Long,
    val note: String,
    val settledAtMillis: Long?,
)

/** 报表用：某个分类在一个区间内的合计 */
data class CategorySumRow(
    val name: String,
    val isIncome: Boolean,
    val totalCents: Long,
)

/** 报表用：某个月的收入/支出合计，month 形如 2026-11 */
data class MonthSumRow(
    val month: String,
    val isIncome: Boolean,
    val totalCents: Long,
)