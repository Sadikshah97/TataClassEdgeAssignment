package com.example.tataclassedgeassignment.di


// di/AppModule.kt

import com.example.tataclassedgeassignment.data.repository.WhiteboardRepositoryImpl
import com.example.tataclassedgeassignment.domain.repository.WhiteboardRepository
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindWhiteboardRepository(
        impl: WhiteboardRepositoryImpl
    ): WhiteboardRepository
}