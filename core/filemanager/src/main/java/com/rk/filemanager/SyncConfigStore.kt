package com.rk.filemanager

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persistence for sync pairs and the last-sync state snapshot.
 *
 * Stored in app-internal SharedPreferences (filemanager module cannot depend on
 * core:main, so we use plain SharedPreferences rather than the Settings object there).
 */
object SyncConfigStore {

    private const val PREFS = "fm_sync"
    private const val KEY_PAIRS = "pairs"
    private const val KEY_STATE_PREFIX = "state:"

    data class Pair(
        val ubuntuPath: String,
        val phonePath: String,
        val enabled: Boolean = true
    )

    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPairs(context: Context): List<Pair> {
        val json = prefs(context).getString(KEY_PAIRS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<Pair>>(json, object : TypeToken<List<Pair>>() {}.type)
        }.getOrDefault(emptyList())
    }

    fun savePairs(context: Context, pairs: List<Pair>) {
        prefs(context).edit().putString(KEY_PAIRS, gson.toJson(pairs)).apply()
    }

    fun getState(context: Context, pair: Pair): SyncEngine.SyncState {
        val json = prefs(context).getString(KEY_STATE_PREFIX + stateKey(pair), null) ?: return SyncEngine.SyncState()
        return runCatching {
            gson.fromJson(json, SyncEngine.SyncState::class.java)
        }.getOrDefault(SyncEngine.SyncState())
    }

    fun setState(context: Context, pair: Pair, state: SyncEngine.SyncState) {
        prefs(context).edit().putString(KEY_STATE_PREFIX + stateKey(pair), gson.toJson(state)).apply()
    }

    private fun stateKey(pair: Pair): String =
        "${pair.ubuntuPath}||${pair.phonePath}".hashCode().toString()
}
