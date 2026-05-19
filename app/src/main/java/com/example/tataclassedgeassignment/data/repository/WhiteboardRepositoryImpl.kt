package com.example.tataclassedgeassignment.data.repository


import android.content.Context
import com.example.tataclassedgeassignment.domain.repository.WhiteboardRepository
import com.example.tataclassedgeassignment.model.WhiteboardData
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhiteboardRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WhiteboardRepository {

    private val storageDir: File
        get() = File(context.filesDir, "whiteboards").also { it.mkdirs() }

    override suspend fun saveWhiteboard(data: WhiteboardData): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "whiteboard_${sdf.format(Date())}.json"
        val file = File(storageDir, fileName)
        file.writeText(gson.toJson(data))
        return fileName
    }

    override suspend fun loadWhiteboard(fileName: String): WhiteboardData? {
        val file = File(storageDir, fileName)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), WhiteboardData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    override fun listSavedWhiteboards(): List<String> {
        return storageDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name }
            ?: emptyList()
    }
}