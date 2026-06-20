package com.bianque.health.base.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bianque.health.base.security.DatabasePassphraseManager
import net.zetetic.database.sqlcipher.SupportFactory
import timber.log.Timber

@Database(
    entities = [HealthRecordEntity::class, UserProfileEntity::class],
    version = 1
)
abstract class HealthDatabase : RoomDatabase() {

    abstract fun healthDao(): HealthDao

    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null

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
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Timber.d("HealthDatabase: encrypted database created")
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}