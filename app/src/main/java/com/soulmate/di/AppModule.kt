package com.soulmate.di

import com.soulmate.data.memory.ObjectBoxVectorSearchEngine
import com.soulmate.data.memory.VectorSearchEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import io.objectbox.BoxStore
import com.soulmate.MyObjectBox
import com.soulmate.core.data.memory.MemoryRepository
import com.soulmate.core.data.memory.ObjectBoxMemoryRepository

/**
 * Qualifier for Application-scoped CoroutineScope.
 * Use this for long-running operations that should survive ViewModel lifecycle.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

/**
 * Qualifier for IO Dispatcher.
 * Use this for disk/network I/O operations.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

/**
 * Qualifier for Default Dispatcher.
 * Use this for CPU-intensive operations.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

/**
 * Qualifier for Main Dispatcher.
 * Use this for UI operations.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MainDispatcher

/**
 * Hilt module for providing application-wide dependencies.
 * 
 * This module provides:
 * - CoroutineScope for application-level coroutines
 * - CoroutineDispatchers for structured concurrency
 * - Interface bindings
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    
    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    
    /**
     * Provides Application-scoped CoroutineScope.
     * 
     * Uses SupervisorJob to prevent child coroutine failures from cancelling
     * sibling coroutines. Runs on Default dispatcher for CPU-bound work.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBoxStore(@ApplicationContext context: Context): BoxStore {
        return MyObjectBox.builder().androidContext(context).build()
    }
}

/**
 * Hilt module for providing interface bindings.
 * 
 * Note: LLMApiService is provided by NetworkModule using Retrofit.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    
    /**
     * Binds VectorSearchEngine interface to ObjectBoxVectorSearchEngine implementation.
     * 
     * Uses ObjectBox's HNSW (Hierarchical Navigable Small World) index for efficient
     * approximate nearest neighbor search with cosine distance.
     */
    @Binds
    @Singleton
    abstract fun bindVectorSearchEngine(
        objectBoxVectorSearchEngine: ObjectBoxVectorSearchEngine
    ): VectorSearchEngine

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(
        objectBoxMemoryRepository: ObjectBoxMemoryRepository
    ): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindEmbeddingService(
        doubaoEmbeddingService: com.soulmate.core.data.brain.DoubaoEmbeddingService
    ): com.soulmate.core.data.brain.EmbeddingService
}
