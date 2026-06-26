package com.bianque.health.base.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bianque.health.base.security.DatabasePassphraseManager
import net.sqlcipher.database.SupportFactory
import timber.log.Timber

@Database(
    entities = [HealthRecordEntity::class, UserProfileEntity::class],
    version = 2
)
abstract class HealthDatabase : RoomDatabase() {

    abstract fun healthDao(): HealthDao

    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null

        /**
         * 数据库迁移策略：每个版本升级都有明确的迁移路径，
         * 不使用 fallbackToDestructiveMigration() 以避免用户数据丢失。
         *
         * 新增表/列时请在此处添加对应的 Migration 对象。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v1 → v2：当前 schema 无变更，预留迁移入口
                // 后续新增表/列时在此处编写 ALTER TABLE / CREATE TABLE 语句
                Timber.i("HealthDatabase: migrating from v1 to v2 (no schema changes)")
            }
        }

        private val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun getInstance(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): HealthDatabase {
            val passphrase = DatabasePassphraseManager.init(context)
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                HealthDatabase::class.java,
                "bianque_health.db"
            )
                .openHelperFactory(factory)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Timber.d("HealthDatabase: encrypted database created")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // 验证数据库完整性（防止 SQLCipher 损坏）
                        db.execSQL("PRAGMA cipher_integrity_check")
                    }
                })
                .build()
        }
    }
}