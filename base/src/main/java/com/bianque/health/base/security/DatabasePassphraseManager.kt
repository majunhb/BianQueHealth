package com.bianque.health.base.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * 管理 Room 数据库的 SQLCipher 加密密钥。
 *
 * 使用 AndroidX EncryptedSharedPreferences (基于 AndroidKeyStore) 安全存储数据库密钥，
 * 首次启动时生成随机 32 字节密钥。
 */
object DatabasePassphraseManager {

    private const val PREFS_NAME = "db_passphrase_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    private var passphrase: ByteArray? = null

    /**
     * 初始化并获取数据库密钥。
     * 必须在 Application.onCreate() 中调用。
     */
    fun init(context: Context): ByteArray {
        passphrase?.let { return it }

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs: SharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        var stored = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored == null) {
            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            stored = randomBytes.joinToString(",") { it.toInt().toString() }
            prefs.edit().putString(KEY_DB_PASSPHRASE, stored).apply()
        }

        passphrase = stored.split(",").map { it.toInt().toByte() }.toByteArray()
        return passphrase!!
    }

    /**
     * 获取已缓存的密钥，如果未初始化则返回 null。
     */
    fun getPassphrase(): ByteArray? = passphrase
}