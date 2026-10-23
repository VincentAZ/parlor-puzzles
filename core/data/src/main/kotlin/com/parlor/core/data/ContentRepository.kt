package com.parlor.core.data

import android.content.Context
import com.parlor.core.game.verifyReference
import com.parlor.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentRepository(private val context: Context) {
    suspend fun dailySchedule(): DailySchedule = withContext(Dispatchers.IO) {
        context.assets.open("levels/daily-schedule.json").bufferedReader().use { ParlorJson.decodeFromString(it.readText()) }
    }
    suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        val index = context.assets.open("levels/catalog-index.json").bufferedReader().use { ParlorJson.decodeFromString<CatalogIndex>(it.readText()) }
        val catalogs = index.files.map { file -> context.assets.open("levels/$file").bufferedReader().use { ParlorJson.decodeFromString<Catalog>(it.readText()) } }
        require(catalogs.all { it.schemaVersion == 1 && it.contentVersion <= index.contentVersion })
        val catalog = Catalog(contentVersion = index.contentVersion, levels = catalogs.flatMap { it.levels })
        require(catalog.schemaVersion == 1) { "Unsupported content schema" }
        require(catalog.levels.map { it.id }.distinct().size == catalog.levels.size)
        catalog.levels.forEach(::verifyReference)
        catalog
    }
}
