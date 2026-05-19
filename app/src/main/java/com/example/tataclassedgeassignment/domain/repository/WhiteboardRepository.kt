package com.example.tataclassedgeassignment.domain.repository

import com.example.tataclassedgeassignment.model.WhiteboardData


interface WhiteboardRepository {
    suspend fun saveWhiteboard(data: WhiteboardData): String  // returns filename
    suspend fun loadWhiteboard(fileName: String): WhiteboardData?
    fun listSavedWhiteboards(): List<String>
}