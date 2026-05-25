package com.example.tataclassedgeassignment.di

import com.example.tataclassedgeassignment.data.repository.WhiteboardRepositoryImpl
import com.example.tataclassedgeassignment.domain.repository.WhiteboardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWhiteboardRepository(
        impl: WhiteboardRepositoryImpl
    ): WhiteboardRepository
}