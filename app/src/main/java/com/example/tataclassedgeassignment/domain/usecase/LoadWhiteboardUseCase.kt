package com.example.tataclassedgeassignment.domain.usecase




import com.example.tataclassedgeassignment.domain.repository.WhiteboardRepository
import com.example.tataclassedgeassignment.model.WhiteboardData
import javax.inject.Inject

class LoadWhiteboardUseCase @Inject constructor(
    private val repository: WhiteboardRepository
) {
    suspend operator fun invoke(fileName: String): WhiteboardData? {
        return repository.loadWhiteboard(fileName)
    }
}