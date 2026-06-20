package com.bianque.health.base.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy_settings")

object PrivacyManager {

    private val CONSENT_KEY = booleanPreferencesKey("privacy_consent")

    private var context: Context? = null

    fun init(context: Context) {
        this.context = context
    }

    fun isConsentGranted(): Boolean {
        val ctx = context ?: return false
        return runBlocking {
            ctx.dataStore.data.map { preferences ->
                preferences[CONSENT_KEY] ?: false
            }.first()
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