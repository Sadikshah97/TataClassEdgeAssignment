package com.example.tataclassedgeassignment.domain.usecase



import com.example.tataclassedgeassignment.domain.repository.WhiteboardRepository
import com.example.tataclassedgeassignment.model.WhiteboardData
import javax.inject.Inject

class SaveWhiteboardUseCase @Inject constructor(
    private val repository: WhiteboardRepository
) {
    suspend operator fun invoke(data: WhiteboardData): String {
        return repository.saveWhiteboard(data)
    }
}