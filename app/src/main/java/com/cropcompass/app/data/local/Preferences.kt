package com.cropcompass.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.preferences by preferencesDataStore("crop_compass")
class Preferences(context: Context) {
    private val store = context.preferences
    private val data = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val guest = data.map { it[booleanPreferencesKey("guest")] ?: false }
    val remote = data.map { it[booleanPreferencesKey("remote")] ?: false }
    val district = data.map { it[stringPreferencesKey("district")] ?: "Kolar" }
    suspend fun guest(value: Boolean) { store.edit { it[booleanPreferencesKey("guest")] = value } }
    suspend fun remote(value: Boolean) { store.edit { it[booleanPreferencesKey("remote")] = value } }
    suspend fun district(value: String) { store.edit { it[stringPreferencesKey("district")] = value } }
    val settings = data.map { values -> Settings(
        language = values[stringPreferencesKey("language")] ?: "en",
        homeDistrictId = values[stringPreferencesKey("homeDistrictId")] ?: "",
        homeMandiId = values[stringPreferencesKey("homeMandiId")] ?: "",
        transportRate = values[doublePreferencesKey("transportRate")] ?: 0.05,
        radiusKm = values[doublePreferencesKey("radiusKm")] ?: 200.0,
        firstRun = values[booleanPreferencesKey("firstRun")] ?: true,
        dataSource = values[stringPreferencesKey("dataSource")] ?: "fixture",
        recentSearches = values[stringPreferencesKey("recentSearches")]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
    ) }
    suspend fun region(districtId: String, mandiId: String) { store.edit {
        it[stringPreferencesKey("homeDistrictId")] = districtId
        it[stringPreferencesKey("homeMandiId")] = mandiId
        it[booleanPreferencesKey("firstRun")] = false
    } }
    suspend fun configure(language: String, rate: Double, radius: Double) { store.edit {
        it[stringPreferencesKey("language")] = language
        it[doublePreferencesKey("transportRate")] = rate
        it[doublePreferencesKey("radiusKm")] = radius
    } }
    suspend fun source(source: String) { store.edit { it[stringPreferencesKey("dataSource")] = source } }
    suspend fun recent(id: String) { store.edit {
        val old = it[stringPreferencesKey("recentSearches")]?.split("|").orEmpty()
        it[stringPreferencesKey("recentSearches")] = (listOf(id) + old.filter { item -> item != id }).take(8).joinToString("|")
    } }
    suspend fun resetOnboarding() { store.edit { it[booleanPreferencesKey("firstRun")] = true } }
}
data class Settings(val language: String = "en", val homeDistrictId: String = "", val homeMandiId: String = "", val transportRate: Double = .05, val radiusKm: Double = 200.0, val firstRun: Boolean = true, val dataSource: String = "fixture", val recentSearches: List<String> = emptyList())
