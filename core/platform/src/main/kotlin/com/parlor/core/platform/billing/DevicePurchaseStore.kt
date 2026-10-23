package com.parlor.core.platform.billing

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.ByteArrayOutputStream

/** Purchase tokens/entitlement never enter Room progress, exports or Android backup. */
class DevicePurchaseStore(context: Context, fileName: String = "play-purchases-v1.json") : PurchaseStore {
    init { require(fileName.matches(Regex("[a-z0-9-]+\\.json"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    override suspend fun load(): PurchaseCache = withContext(Dispatchers.IO) {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return@withContext PurchaseCache()
        val bytes = file.openRead().use { input ->
            val output = ByteArrayOutputStream();val buffer = ByteArray(4096)
            while(true) {
                val count = input.read(buffer)
                if(count < 0) break
                require(output.size()+count <= 1_048_576)
                output.write(buffer,0,count)
            }
            output.toByteArray()
        }
        json.decodeFromString<PurchaseCache>(bytes.toString(Charsets.UTF_8))
    }
    override suspend fun save(cache: PurchaseCache) = withContext(Dispatchers.IO) {
        val bytes = json.encodeToString(cache).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1_048_576)
        val output = file.startWrite()
        try { output.write(bytes);file.finishWrite(output) }
        catch(error: Exception) { file.failWrite(output);throw error }
    }
}
