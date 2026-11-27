package com.yuanguangbb.ledger.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        DebtEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dao(): LedgerDao

    companion object {

        /**
         * 1 -> 2：账单增加「录入时间」。旧账单用记账时间回填，一条都不丢。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("transactions", "createdAtMillis")) {
                    db.execSQL(
                        "ALTER TABLE transactions ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0"
                    )
                }
                db.execSQL(
                    "UPDATE transactions SET createdAtMillis = timeMillis WHERE createdAtMillis = 0"
                )
            }
        }

        /**
         * 2 -> 3：新增商品表；账单增加商品和数量两列。旧账单数据全部保留。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.createProductsIfMissing()
                if (!db.hasColumn("transactions", "createdAtMillis")) {
                    db.execSQL(
                        "ALTER TABLE transactions ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "UPDATE transactions SET createdAtMillis = timeMillis WHERE createdAtMillis = 0"
                    )
                }
                if (!db.hasColumn("transactions", "productId")) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN productId INTEGER")
                }
                if (!db.hasColumn("transactions", "quantity")) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN quantity INTEGER")
                }
            }
        }

        /**
         * 3 -> 4：账单加软删除（回收站），新增客户和赊账两张表。
         * 旧账单 deletedAtMillis 为空，全部正常显示。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("transactions", "deletedAtMillis")) {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN deletedAtMillis INTEGER")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `customers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`customerId` INTEGER NOT NULL, `amountCents` INTEGER NOT NULL, " +
                        "`timeMillis` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                        "`settledAtMillis` INTEGER)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_debts_customerId` ON `debts` (`customerId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_debts_timeMillis` ON `debts` (`timeMillis`)"
                )
            }
        }

        /**
         * 4 -> 5：赊账记录关联「赊账时自动记的那笔收入」。老赊账为 NULL，不影响原有数据。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("debts", "incomeTransactionId")) {
                    db.execSQL("ALTER TABLE debts ADD COLUMN incomeTransactionId INTEGER")
                }
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
            query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }

        private fun SupportSQLiteDatabase.createProductsIfMissing() {
            execSQL(
                "CREATE TABLE IF NOT EXISTS `products` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
            )
            execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)"
            )
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ledger.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addCallback(object : Callback() {
                    // 第一次创建数据库时，写入默认分类和账户
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        listOf("销售收入", "其他收入").forEach {
                            db.execSQL("INSERT INTO categories(name, isIncome) VALUES ('$it', 1)")
                        }
                        listOf("进货", "房租", "人工", "水电", "运输", "其他支出").forEach {
                            db.execSQL("INSERT INTO categories(name, isIncome) VALUES ('$it', 0)")
                        }
                        listOf("微信", "支付宝", "现金", "银行卡").forEach {
                            db.execSQL("INSERT INTO accounts(name) VALUES ('$it')")
                        }
                    }
                })
                .build()
    }
}