# SoulMate Agent Guidelines

This document provides guidelines for agentic coding assistants working on the SoulMate Android project.

## Build Commands

```bash
# Full build
./gradlew build

# Run unit tests
./gradlew test

# Run specific test class
./gradlew test --tests com.soulmate.core.data.brain.RAGServiceTest

# Run specific test method
./gradlew test --tests "com.soulmate.core.data.brain.RAGServiceTest.prepareContext_filtersLowSimilarityAndRecentEcho"

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

## Code Style Guidelines

### Import Organization
- Standard Android/Compose imports first (androidx.*)
- Third-party libraries next (io.coil-kt.*, com.google.gson.*)
- Project imports last (com.soulmate.*)
- Use wildcard imports sparingly (acceptable for Compose UI: `androidx.compose.runtime.*`)
- No strict alphabetical ordering enforced

### Naming Conventions
- **Classes**: PascalCase (`ChatViewModel`, `RAGService`, `LLMException`)
- **Functions/Methods**: camelCase (`sendMessage`, `prepareContext`)
- **Composable Functions**: PascalCase (`ChatScreen`, `ParallaxGlassCard`)
- **Variables/Properties**: camelCase (`chatState`, `currentSessionId`)
- **Constants**: UPPER_SNAKE_CASE (`TOP_K_CANDIDATES`, `TAG`, `MIN_SIMILARITY`)
- **Private Mutable State**: Prefix with underscore (`_chatState`, `_voiceInputText`)
- **Test Methods**: `methodName_descriptiveBehavior` (`prepareContext_filtersLowSimilarityAndRecentEcho`)

### Formatting
- **Indentation**: 4 spaces (Kotlin standard)
- **Brace Placement**: Opening brace on same line for functions/constructors
- **Line Length**: Approx 100-120 characters (soft limit)
- **KDoc**: Required for all public classes and functions
- **Comments**: Bilingual (Chinese/English) is acceptable and common

### Example Code Style

```kotlin
/**
 * Service description in KDoc format.
 * 
 * @param param1 Description
 * @return Return value description
 */
@Singleton
class ExampleService @Inject constructor(
    private val repository: Repository
) {
    companion object {
        private const val TAG = "ExampleService"
        private const val MAX_ITEMS = 10
    }

    private val _state = MutableStateFlow<State>(State.Initial)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Method description.
     */
    suspend fun doSomething(param: String): Result {
        return try {
            repository.fetch(param)
        } catch (e: Exception) {
            Log.e(TAG, "Operation failed", e)
            Result.Error(e.message)
        }
    }
}
```

## Error Handling

### Custom Exceptions
Define custom exceptions for domain-specific errors:
```kotlin
class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

### Try/Catch Patterns
- Use specific exception handling for known error cases
- Always log errors with TAG for debugging
- Provide user-friendly error messages through StateFlow updates
- For Flow chains, use `.catch { }` operator

### Logging
- Use `Log.d(TAG, "message")` for debug
- Use `Log.e(TAG, "message", exception)` for errors
- Define `TAG` constant in companion object
- Use privacy-safe logging (avoid logging sensitive user data)

## Testing Guidelines

### Test Structure
- Unit tests in `app/src/test/java/com/soulmate/`
- Instrumented tests in `app/src/androidTest/java/com/soulmate/`
- Test file naming: `ClassNameTest.kt`

### Test Patterns
```kotlin
class ExampleServiceTest {

    @Test
    fun methodName_descriptiveBehavior() {
        // Arrange
        val input = "test"
        val expected = "result"
        val fakeRepo = FakeRepository(listOf(/*...*/))
        val service = ExampleService(fakeRepo)

        // Act
        val result = runBlocking { service.process(input) }

        // Assert
        assertEquals(expected, result)
    }
}
```

### Dependencies
- **Unit Tests**: Use fake implementations for dependencies
- **Android Tests**: Use `@HiltAndroidTest` with `HiltTestRunner`
- **Coroutines**: Use `runBlocking` or `runTest`
- **Compose UI**: Use `createAndroidComposeRule`

## Architecture Patterns

### MVVM + Clean Architecture
- **UI Layer**: Jetpack Compose + ViewModels
- **Data Layer**: Repositories for data access
- **Domain Layer**: Services (RAGService, LLMService, etc.)

### Dependency Injection
- Use Hilt for DI
- `@Inject constructor` for dependencies
- `@Singleton` for services that should have single instance
- `@HiltViewModel` for ViewModels
- Provide fake implementations in test modules

### State Management
- Use `StateFlow` for reactive state
- Backing field pattern for mutable state (`_state` / `state`)
- Expose immutable `StateFlow` to observers
- Update state using `.update { }` lambda

### Concurrency
- Use Kotlin coroutines for async operations
- `viewModelScope.launch { }` in ViewModels
- `suspend fun` for blocking operations
- Flow for streams of data
- `.flowOn(Dispatchers.IO)` for network/database operations

## Project-Specific Patterns

### RAG (Retrieval Augmented Generation)
- Memory stored in ObjectBox with vector embeddings
- Tags: `manual`, `summary`, `user_input`, `ai_output`
- Similarity threshold: 0.30f (configurable)
- Time decay: 14-day half-life (configurable)
- Exclude recent conversation rounds to avoid repetition

### Streaming LLM Responses
- Use SSE (Server-Sent Events) for real-time streaming
- Emit accumulated tokens as they arrive
- Throttle UI updates (200ms or 3 char delta) to reduce recomposition
- Use `StringBuilder` for accumulation

### Avatar (Digital Human)
- Abstracted via `IAvatarDriver` interface
- `XmovAvatarDriver` as current implementation
- Lifecycle-aware: destroy on `onDestroy`
- Supports gesture and emotion tags

### Memory System
- Short-term: Chat history (ObjectBox)
- Long-term: Vector embeddings (Doubao Embedding API)
- RAG for context-aware responses
- Tag-based categorization

## Important Notes

- **Language**: This is a bilingual codebase (Chinese/English). Comments and documentation may be in either language.
- **API Keys**: Required in `local.properties` (DOUBAO_API_KEY, ALIYUN_ASR_APP_KEY, XMOV_APP_ID, etc.)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Build Config**: Use `BuildConfig.DOUBAO_API_KEY` for API access

## File Structure Reference

```
com.soulmate/
├── core/
│   ├── data/brain/      # LLM, RAG, Embedding services
│   ├── data/memory/      # ObjectBox memory repository
│   └── data/chat/       # Chat message repository
├── data/
│   ├── repository/       # API services (LLM, ImageGen)
│   ├── service/          # ASR, MindWatch, Avatar services
│   ├── model/           # Data models and DTOs
│   └── preferences/     # User preferences (DataStore)
├── worker/              # WorkManager workers (Heartbeat, EmotionTracker)
└── ui/
    ├── screens/          # Composable screens
    ├── components/      # Reusable UI components
    ├── viewmodel/       # ViewModels
    └── theme/          # Compose theming
```
