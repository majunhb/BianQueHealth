package com.bianque.health.base.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy_settings")

object PrivacyManager {

    private val CONSENT_KEY = booleanPreferencesKey("privacy_consent")

    private var context: Context? = null

    fun init(context: Context) {
        this.context = context
    }

    /**
     * 异步检查用户是否已授权隐私协议。
     * 使用 suspend 避免主线程阻塞。
     */
    suspend fun isConsentGranted(): Boolean {
        val ctx = context ?: return false
        return ctx.dataStore.data.map { preferences ->
            preferences[CONSENT_KEY] ?: false
        }.first()
    }

    /**
     * 以 Flow 形式观察隐私授权状态，适用于 Compose 实时响应。
     */
    fun observeConsent(): Flow<Boolean> {
        val ctx = context ?: return kotlinx.coroutines.flow.flowOf(false)
        return ctx.dataStore.data.map { preferences ->
            preferences[CONSENT_KEY] ?: false
        }
    }

    suspend fun grantConsent() {
        val ctx = context ?: return
        ctx.dataStore.edit { preferences ->
            preferences[CONSENT_KEY] = true
        }
    }

    suspend fun revokeConsent() {
        val ctx = context ?: return
        ctx.dataStore.edit { preferences ->
            preferences[CONSENT_KEY] = false
        }
    }

    fun anonymizeData(data: String): String {
        if (data.length <= 2) return "***"
        return data.first() + "*".repeat(data.length - 2) + data.last()
    }
}